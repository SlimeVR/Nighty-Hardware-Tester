use std::sync;
use std::thread;
use std::time;

use bno080::{interface::i2c, wrapper};
use rppal::i2c as rp_i2c;
use rppal::gpio;

use crate::api;
use crate::logger;
use crate::Board;

use super::TestExecutor;

type BNOInterface = bno080::wrapper::BNO080<bno080::interface::I2cInterface<rp_i2c::I2c>>;

pub struct AuxBoardTestExecutor {
	bno: BNOInterface,
	delay: rppal::hal::Delay,
	logger: sync::Arc<sync::Mutex<logger::Logger>>,
	int_pin: gpio::InputPin,
}

impl AuxBoardTestExecutor {
	pub fn new(
		i2c: rp_i2c::I2c,
		gpio: gpio::Gpio,
		logger: sync::Arc<sync::Mutex<logger::Logger>>,
	) -> AuxBoardTestExecutor {
		// TODO: switch out when testing extensions
		let si = i2c::I2cInterface::new(i2c, i2c::ALTERNATE_ADDRESS);
		let bno = wrapper::BNO080::new_with_interface(si);

		let delay = rppal::hal::Delay::new();
		let int_pin = gpio.get(17).unwrap().into_input_pullup();

		AuxBoardTestExecutor { bno, delay, logger, int_pin}
	}
}

impl TestExecutor for AuxBoardTestExecutor {
	fn wait_for_device_connect(&mut self) {
		loop {
			match self.bno.init(&mut self.delay) {
				Ok(_) => break,
				_ => {}
			}

			thread::sleep(time::Duration::from_millis(250));
		}
	}

	fn run(&mut self) -> crate::TestResult {
		thread::sleep(time::Duration::from_secs(1));

		let mut board = Board::new();
		board.id = Some(uuid::Uuid::new_v4().to_string());

		/*
		{
			let mut l = self.logger.lock().unwrap();
			l.in_progress("Initializing BNO080...");
		}
		let start = chrono::Utc::now();
		match self.bno.init(&mut self.delay) {
			Ok(_) => {
				board.add_value(api::TestReportValue::new(
					"Init",
					"should be successful",
					"true",
					None::<String>,
					false,
					start,
					chrono::Utc::now(),
				));

				{
					let mut l = self.logger.lock().unwrap();
					l.success("BNO080 initialized successfully");
				}
			}
			Err(e) => {
				board.add_value(api::TestReportValue::new(
					"Init",
					"should be successful",
					"false",
					Some(format!("{:?}", e)),
					true,
					start,
					chrono::Utc::now(),
				));
				board.ended_at = chrono::Utc::now();

				{
					let mut l = self.logger.lock().unwrap();
					l.error(&format!("BNO080 failed to initialize: {:?}", e));
				}

				return crate::TestResult::Failed(board);
			}
		}

		thread::sleep(time::Duration::from_millis(500));
		*/

		{
			let mut l = self.logger.lock().unwrap();
			l.in_progress("Enabling game rotation vector...");
		}
		let start = chrono::Utc::now();
		match self.bno.enable_rotation_vector(5) { // TODO enable game rotation vector
			Ok(_) => {
				board.add_value(api::TestReportValue::new(
					"Game rotation vector",
					"should be enableable",
					"true",
					None::<String>,
					false,
					start,
					chrono::Utc::now(),
				));

				{
					let mut l = self.logger.lock().unwrap();
					l.success("Game rotation vector enabled successfully");
				}
			}
			Err(e) => {
				board.add_value(api::TestReportValue::new(
					"Game rotation vector",
					"should be enabled",
					"false",
					Some(format!("{:?}", e)),
					true,
					start,
					chrono::Utc::now(),
				));
				board.ended_at = chrono::Utc::now();

				{
					let mut l = self.logger.lock().unwrap();
					l.error(&format!("Game rotation vector failed to enable: {:?}", e));
				}

				return crate::TestResult::Failed(board);
			}
		}

		{
			let mut l = self.logger.lock().unwrap();
			l.in_progress("Handling messages...");
		}

		let start = chrono::Utc::now();
		let mut processed_messages = 0;
		// Eat all and wait a bit
		//self.bno.eat_all_messages(&mut self.delay);
		//thread::sleep(time::Duration::from_millis(10));

		let now = time::Instant::now();
		while now.elapsed().as_millis() < 500
		{
			if self.int_pin.is_low()
			{
				processed_messages += self.bno.handle_one_message(&mut self.delay, 0);
				{
					let mut l = self.logger.lock().unwrap();
					l.in_progress("New message. Channel {}", self.bno.last_chan_received);
				}
			}
			thread::sleep(time::Duration::from_millis(1));
		}
		
		if processed_messages > 0
		{
			board.add_value(api::TestReportValue::new(
				"Handling messages",
				"should process messages",
				processed_messages,
				None::<String>,
				false,
				start,
				chrono::Utc::now(),
			));
		}
		else
		{
			board.add_value(api::TestReportValue::new(
				"Handling messages",
				"should process messages",
				"false",
				None::<String>,
				true,
				start,
				chrono::Utc::now(),
			));
			board.ended_at = chrono::Utc::now();

			{
				let mut l = self.logger.lock().unwrap();
				l.error(&format!("Failed to read any messages"));
			}

			return crate::TestResult::Failed(board);
		}

		{
			let mut l = self.logger.lock().unwrap();
			l.success(&format!("Processed {} messages", processed_messages));

			l.in_progress("Reading rotation quaternion...")
		}

		let start = chrono::Utc::now();
		match self.bno.rotation_quaternion() {
			Ok(q) => {
				if q == [0.0, 0.0, 0.0, 0.0]
				{
					board.add_value(api::TestReportValue::new(
						"Quaternion",
						"should be valid",
						"false",
						Some(format!("{:?}", q)),
						true,
						start,
						chrono::Utc::now(),
					));
					board.ended_at = chrono::Utc::now();
	
					{
						let mut l = self.logger.lock().unwrap();
						l.error(&format!("Quaternion is empty"));
					}
	
					return crate::TestResult::Failed(board);
				}
				board.add_value(api::TestReportValue::new(
					"Quaternion",
					"should be valid",
					"true",
					Some(format!("{:?}", q)),
					false,
					start,
					chrono::Utc::now(),
				));

				{
					let mut l = self.logger.lock().unwrap();
					l.success(&format!("Quaternion: {:?}", q));
				}
			}
			Err(e) => {
				board.add_value(api::TestReportValue::new(
					"Quaternion",
					"should be valid",
					"false",
					Some(format!("{:?}", e)),
					true,
					start,
					chrono::Utc::now(),
				));
				board.ended_at = chrono::Utc::now();

				{
					let mut l = self.logger.lock().unwrap();
					l.error(&format!("Failed to read quaternion: {:?}", e));
				}

				return crate::TestResult::Failed(board);
			}
		}

		board.ended_at = chrono::Utc::now();
		crate::TestResult::Passed(board)
	}

	fn wait_for_device_disconnect(&mut self) {
		thread::sleep(time::Duration::from_secs(2));
	}
}
