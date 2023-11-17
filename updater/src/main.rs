use std::{io, thread::sleep, time::Duration};

use updater::{
	helpers::{logger, pio},
	options,
	upload_update::UploadUpdateExecutor,
};

fn maybe_build_firmware(options: &options::Options) -> Result<(), io::Error> {
	if options.no_build {
		logger::in_progress("Skipping firmware build...");

		sleep(Duration::from_millis(500));

		Ok(())
	} else {
		logger::in_progress("Building firmware...");

		pio::build("esp12e")?;

		Ok(())
	}
}

fn main() {
	let options = options::Options::parse();

	if let Err(e) = maybe_build_firmware(&options) {
		println!("Could not build firmware: {}", e);

		std::process::exit(1);
	}

	let mut executor = UploadUpdateExecutor::new(options);

	loop {
		logger::action("[ Please connect the device ]");

		executor.wait_for_device_connect();

		logger::success("Device connected");

		let result = executor.run();

		let mut wait_for_next_board = |flash_color: colored::Color| {
			logger::action("[ Please disconnect the device ]");

			logger::banner("==================================", flash_color);

			executor.wait_for_device_disconnect();
		};

		match result {
			Ok(_) => {
				logger::success("Board updated");

				wait_for_next_board(colored::Color::Green);
			}
			Err(error) => {
				logger::error("Board failed updating");
				logger::error(&format!("{}", error));

				wait_for_next_board(colored::Color::Red);
			}
		}
	}
}
