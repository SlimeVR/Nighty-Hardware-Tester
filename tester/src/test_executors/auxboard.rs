use std::sync;
use std::thread;
use std::time;

use bno080::{interface::i2c, wrapper};
use rppal::i2c as rp_i2c;

use crate::api;
use crate::logger;
use crate::Board;

use super::TestExecutor;

type BNOInterface = bno080::wrapper::BNO080<bno080::interface::I2cInterface<rp_i2c::I2c>>;

pub struct AuxBoardTestExecutor {
	bno: BNOInterface,
	delay: rppal::hal::Delay,
	logger: sync::Arc<sync::Mutex<logger::Logger>>,
}

impl AuxBoardTestExecutor {
	pub fn new(
		i2c: rp_i2c::I2c,
		logger: sync::Arc<sync::Mutex<logger::Logger>>,
	) -> AuxBoardTestExecutor {
		// TODO: switch out when testing extensions
		let si = i2c::I2cInterface::new(i2c, i2c::ALTERNATE_ADDRESS);
		let bno = wrapper::BNO080::new_with_interface(si);

		let delay = rppal::hal::Delay::new();

		AuxBoardTestExecutor { bno, delay, logger }
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

		{
			let mut l = self.logger.lock().unwrap();
			l.in_progress("Enabling rotation vector...");
		}
		let start = chrono::Utc::now();
		match self.bno.enable_rotation_vector(5) {
			Ok(_) => {
				board.add_value(api::TestReportValue::new(
					"Rotation vector",
					"should be enableable",
					"true",
					None::<String>,
					false,
					start,
					chrono::Utc::now(),
				));

				{
					let mut l = self.logger.lock().unwrap();
					l.success("Rotation vector enabled successfully");
				}
			}
			Err(e) => {
				board.add_value(api::TestReportValue::new(
					"Rotation vector",
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
					l.error(&format!("Rotation vector failed to enable: {:?}", e));
				}

				return crate::TestResult::Failed(board);
			}
		}

		{
			let mut l = self.logger.lock().unwrap();
			l.in_progress("Handling messages...");
		}
		let start = chrono::Utc::now();
		thread::sleep(time::Duration::from_millis(500));

		let processed_messages = self.bno.handle_all_messages(&mut self.delay, u8::MAX);
		board.add_value(api::TestReportValue::new(
			"Handling messages",
			"should process messages",
			processed_messages,
			None::<String>,
			false,
			start,
			chrono::Utc::now(),
		));

		{
			let mut l = self.logger.lock().unwrap();
			l.success(&format!("Processed {} messages", processed_messages));

			l.in_progress("Reading rotation quaternion...")
		}

		let start = chrono::Utc::now();
		match self.bno.rotation_quaternion() {
			Ok(q) => {
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
