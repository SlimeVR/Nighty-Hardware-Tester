use rppal::{gpio, i2c};
use serde::{Deserialize, Serialize};
use std::{
	fs::{read_to_string, File},
	io::Write,
	sync::{Arc, Mutex},
	thread::{sleep, spawn},
	time::Duration,
};
use tester::{
	api, logger, options, pio,
	test_executors::{auxboard, mainboard, TestExecutor},
	Board, TestResult,
};

#[derive(Debug, Clone, Serialize, Deserialize)]
struct BoardUploadFailure {
	board: Board,
	error: String,
}

fn maybe_build_firmware(
	options: &options::Options,
	logger: Arc<Mutex<logger::Logger>>,
) -> gpio::Result<()> {
	if options.no_build {
		{
			let mut l = logger.lock().unwrap();
			l.in_progress("Skipping firmware build...");
		}

		sleep(Duration::from_millis(500));

		{
			let mut l = logger.lock().unwrap();
			l.reset();
		}

		Ok(())
	} else {
		{
			let mut l = logger.lock().unwrap();
			l.in_progress("Building firmware...");
		}

		pio::build("esp12e")?;

		{
			let mut l = logger.lock().unwrap();
			l.reset();
		}

		Ok(())
	}
}

fn main() {
	let options = options::Options::parse();

	let (mut renderer, logger) = logger::LoggerBuilder::split();
	let logger = Arc::new(Mutex::new(logger));

	let boards_to_upload: Arc<Mutex<Vec<Board>>> = Arc::new(Mutex::new(Vec::new()));

	let reports_to_upload_clone = boards_to_upload.clone();
	let options_clone = options.clone();
	let logger_clone = logger.clone();
	spawn(move || {
		let reports_to_upload = reports_to_upload_clone;
		let options = options_clone;
		let logger = logger_clone;

		if let Err(e) = maybe_build_firmware(&options, logger.clone()) {
			println!("Could not build firmware: {}", e);

			std::process::exit(1);
		}

		let i2c = i2c::I2c::with_bus(1).unwrap();
		let gpio = gpio::Gpio::new().unwrap();

		let mut executor: Box<dyn TestExecutor> = match options.report_type.as_str() {
			"mainboard" => Box::new(mainboard::MainBoardTestExecutor::new(
				i2c,
				gpio,
				logger.clone(),
				options.clone(),
			)) as Box<dyn TestExecutor>,
			"auxboard" => Box::new(auxboard::AuxBoardTestExecutor::new(i2c, gpio, logger.clone()))
				as Box<dyn TestExecutor>,
			_ => {
				{
					let mut l = logger.lock().unwrap();
					l.error("Invalid report type");
				}
				std::process::exit(1);
			}
		};

		loop {
			{
				let mut l = logger.lock().unwrap();
				l.action("[ Please connect the device ]");
			}

			executor.wait_for_device_connect();

			{
				let mut l = logger.lock().unwrap();
				l.reset();
				l.success("Device connected");
			}

			let result = executor.run();

			let mut wait_for_next_board = |board: Board, flash_color: logger::Color| {
				{
					let mut reports = reports_to_upload.lock().unwrap();
					reports.push(board);
				}

				{
					let mut l = logger.lock().unwrap();
					l.action("[ Please disconnect the device ]".to_string());
					l.fill(flash_color);
				}

				executor.wait_for_device_disconnect();

				{
					let mut l = logger.lock().unwrap();
					l.reset();
				}
			};

			match result {
				TestResult::Failed(board) => {
					{
						let mut l = logger.lock().unwrap();
						l.error("Board failed testing");
					}

					wait_for_next_board(board, logger::Color::Red);
				}
				TestResult::Passed(board) => {
					{
						let mut l = logger.lock().unwrap();
						l.success("Board passed testing");
					}

					wait_for_next_board(board, logger::Color::Green);
				}
			}
		}
	});

	spawn(move || {
		let client = api::Client::from_options(&options);

		let mut upload_failed_boards = {
			let failed_boards = read_to_string("failed_boards.json").unwrap_or("[]".to_string());

			serde_json::from_str::<Vec<BoardUploadFailure>>(&failed_boards)
				.unwrap_or_else(|_| Vec::new())
		};

		loop {
			let board_tests_to_upload = {
				let mut reports_to_upload = boards_to_upload.lock().unwrap();
				let boards_to_upload = reports_to_upload.clone();
				reports_to_upload.clear();

				boards_to_upload
			};

			for board in board_tests_to_upload {
				if let None = board.id {
					println!("Board has no MAC address, skipping upload");
					continue;
				}

				match client.send_test_report(
					&options.report_type,
					board.id.clone().unwrap(),
					&options.tester_name,
					&board.values,
					board.started_at,
					board.ended_at,
				) {
					Ok(s) => {
						if s.status() != reqwest::StatusCode::OK {
							let e = s.text().unwrap();

							println!("Failed to upload report: {}", e);

							upload_failed_boards.push(BoardUploadFailure { board, error: e });

							continue;
						}

						let e = s.text().unwrap();

						println!("{}", e);
					}
					Err(e) => {
						println!("Failed to upload report: {}", e);

						upload_failed_boards.push(BoardUploadFailure {
							board,
							error: e.to_string(),
						});
					}
				}
			}

			{
				let mut failed_boards_file = File::create("failed_boards.json").unwrap();
				failed_boards_file
					.write_all(
						serde_json::to_string_pretty(&upload_failed_boards)
							.unwrap()
							.as_bytes(),
					)
					.unwrap();
			}

			sleep(Duration::from_secs(1));
		}
	});

	renderer.run().unwrap();
}
