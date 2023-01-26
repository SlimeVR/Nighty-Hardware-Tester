use crate::{
	adc, api, esp, esptool, logger,
	options::{self, Options},
	pio, serial, usb, Board, TestResult,
};
use ads1x1x::ChannelSelection;
use rppal::{gpio, i2c};
use std::{sync, thread, time};

use super::TestExecutor;

const USB_VENDOR_ID: u16 = 0x1a86;
const USB_PRODUCT_ID: u16 = 0x7523;

pub struct MainBoardTestExecutor {
	adc: adc::Ads1115<i2c::I2c>,
	esp: esp::ESP,
	logger: sync::Arc<sync::Mutex<logger::Logger>>,
	options: Options,
}

impl MainBoardTestExecutor {
	pub fn new(
		i2c: i2c::I2c,
		gpio: gpio::Gpio,
		logger: sync::Arc<sync::Mutex<logger::Logger>>,
		options: Options,
	) -> Self {
		let adc = adc::Ads1115::new(i2c).unwrap();

		let esp = {
			let rst_pin = gpio.get(6).unwrap().into_output_high();
			let flash_pin = gpio.get(22).unwrap().into_output_high();

			esp::ESP::new(rst_pin, flash_pin)
		};

		Self {
			adc,
			esp,
			logger,
			options,
		}
	}
}

impl TestExecutor for MainBoardTestExecutor {
	fn wait_for_device_connect(&mut self) {
		usb::wait_until_device_is_connected(USB_VENDOR_ID, USB_PRODUCT_ID);
	}

	fn wait_for_device_disconnect(&mut self) {
		usb::wait_until_device_is_disconnected(USB_VENDOR_ID, USB_PRODUCT_ID);
	}

	fn run(&mut self) -> TestResult {
		thread::sleep(time::Duration::from_millis(250));

		let mut board = Board::new();

		let err = {
			let start = chrono::Utc::now();

			match self.adc.measure(ChannelSelection::SingleA2) {
				Ok(v) => {
					board.values.push(api::TestReportValue::new(
						"Measure VOUT",
						"none",
						v.to_string() + "V",
						None::<&str>,
						false,
						start,
						chrono::Utc::now(),
					));
				}
				Err(e) => match e {
					nb::Error::WouldBlock => {
						board.values.push(api::TestReportValue::new(
							"Measure VOUT",
							"none",
							"N/A",
							Some("err: would block"),
							false,
							start,
							chrono::Utc::now(),
						));
					}
					nb::Error::Other(e) => match e {
						ads1x1x::Error::I2C(e) => {
							board.values.push(api::TestReportValue::new(
								"Measure VOUT",
								"none",
								"N/A",
								Some(&format!("err: i2c: {}", e)),
								false,
								start,
								chrono::Utc::now(),
							));
						}
						ads1x1x::Error::InvalidInputData => {
							board.values.push(api::TestReportValue::new(
								"Measure VOUT",
								"none",
								"N/A",
								Some("err: invalid input data"),
								false,
								start,
								chrono::Utc::now(),
							));
						}
					},
				},
			};

			{
				let mut l = self.logger.lock().unwrap();
				l.in_progress("Measuring B+...");
			}

			let start = chrono::Utc::now();
			let bplus_err = match self.adc.measure(ChannelSelection::SingleA3) {
				Ok(v) => {
					let bplus_err = v < 4.0;
					{
						let mut l = self.logger.lock().unwrap();
						if bplus_err {
							l.error(&format!("B+ voltage: {}V (> 4.0V)", v));
						} else {
							l.success(&format!("B+ voltage: {}V", v));
						}
					}

					board.values.push(api::TestReportValue::new(
						"Measure B+",
						"B+ > 4.0V",
						v.to_string() + "V",
						None::<&str>,
						bplus_err,
						start,
						chrono::Utc::now(),
					));

					bplus_err
				}
				Err(e) => match e {
					nb::Error::WouldBlock => {
						board.values.push(api::TestReportValue::new(
							"Measure B+",
							"B+ > 4.0V",
							"N/A",
							Some("err: would block"),
							true,
							start,
							chrono::Utc::now(),
						));

						true
					}
					nb::Error::Other(e) => match e {
						ads1x1x::Error::I2C(e) => {
							board.values.push(api::TestReportValue::new(
								"Measure B+",
								"B+ > 4.0V",
								"N/A",
								Some(&format!("err: i2c: {}", e)),
								true,
								start,
								chrono::Utc::now(),
							));

							true
						}
						ads1x1x::Error::InvalidInputData => {
							board.values.push(api::TestReportValue::new(
								"Measure B+",
								"B+ > 4.0V",
								"N/A",
								Some("err: invalid input data"),
								true,
								start,
								chrono::Utc::now(),
							));

							true
						}
					},
				},
			};

			{
				let mut l = self.logger.lock().unwrap();
				l.in_progress("Measuring 3V3...");
			}

			let start = chrono::Utc::now();
			let r3v3_err = match self.adc.measure(ChannelSelection::SingleA0) {
				Ok(v) => {
					let r3v3_err = v < 2.8 || v > 3.2;
					{
						let mut l = self.logger.lock().unwrap();
						if r3v3_err {
							l.error(&format!("3V3 voltage: {}V (> 2.8V < 3.2V)", v));
						} else {
							l.success(&format!("3V3 voltage: {}V", v));
						}
					}

					board.values.push(api::TestReportValue::new(
						"Measure 3V3",
						"2.8V > 3V3 < 3.2V",
						v.to_string() + "V",
						None::<&str>,
						r3v3_err,
						start,
						chrono::Utc::now(),
					));

					r3v3_err
				}
				Err(e) => match e {
					nb::Error::WouldBlock => {
						board.values.push(api::TestReportValue::new(
							"Measure 3V3",
							"2.8V > 3V3 < 3.2V",
							"N/A",
							Some("err: would block"),
							true,
							start,
							chrono::Utc::now(),
						));

						true
					}
					nb::Error::Other(e) => match e {
						ads1x1x::Error::I2C(e) => {
							board.values.push(api::TestReportValue::new(
								"Measure 3V3",
								"2.8V > 3V3 < 3.2V",
								"N/A",
								Some(&format!("err: i2c: {}", e)),
								true,
								start,
								chrono::Utc::now(),
							));

							true
						}
						ads1x1x::Error::InvalidInputData => {
							board.values.push(api::TestReportValue::new(
								"Measure 3V3",
								"2.8V > 3V3 < 3.2V",
								"N/A",
								Some("err: invalid input data"),
								true,
								start,
								chrono::Utc::now(),
							));

							true
						}
					},
				},
			};

			bplus_err || r3v3_err
		};

		if err {
			{
				let mut l = self.logger.lock().unwrap();
				l.error("-> Faulty power circuit");
			}

			board.ended_at = chrono::Utc::now();
			return TestResult::Failed(board);
		}

		{
			{
				let mut l = self.logger.lock().unwrap();
				l.in_progress("Reading MAC address...");
			}

			let start = chrono::Utc::now();
			match esptool::read_mac_address(&mut self.esp) {
				Ok(esptool::ReadMacAddressResult { mac, log }) => {
					board.id = Some(mac.clone());
					board.values.push(api::TestReportValue::new(
						"Read MAC address",
						"MAC address should be readable",
						mac.clone(),
						Some(log),
						false,
						start,
						chrono::Utc::now(),
					));

					{
						let mut l = self.logger.lock().unwrap();
						l.success(&format!("Read MAC address: {}", mac));
					}
				}
				Err(e) => {
					board.values.push(api::TestReportValue::new(
						"Read MAC address",
						"MAC address should be readable",
						"N/A",
						Some(e.to_string()),
						true,
						start,
						chrono::Utc::now(),
					));

					{
						let mut l = self.logger.lock().unwrap();
						l.error(&format!("Failed to read MAC address: {}", e));
						l.error("-> ESP8266 faulty");
					}

					board.ended_at = chrono::Utc::now();
					return TestResult::Failed(board);
				}
			}
		};

		{
			{
				let mut l = self.logger.lock().unwrap();
				l.in_progress("Flashing...");
			}

			let start = chrono::Utc::now();
			let result = match self.options.flash_with {
				options::FlashWith::ESPTool => esptool::write_flash(
					"slimevr-tracker-esp/.pio/build/esp12e/firmware.bin",
					&mut self.esp,
					self.options.flash_baudrate,
				),
				options::FlashWith::PlatformIO => pio::flash("esp12e", &mut self.esp),
			};
			let end = chrono::Utc::now();

			match result {
				Ok(logs) => {
					board.values.push(api::TestReportValue::new(
						"Flashing",
						"Flashing should work",
						true,
						Some(logs),
						false,
						start,
						end,
					));

					{
						let mut l = self.logger.lock().unwrap();
						l.success("Flashing successful");
					}
				}
				Err(e) => {
					board.values.push(api::TestReportValue::new(
						"Flashing",
						"Flashing should work",
						false,
						Some(e.to_string()),
						true,
						start,
						end,
					));

					{
						let mut l = self.logger.lock().unwrap();
						l.error(&format!("Flashing: {}", e));
						l.error("-> Flashing failed");
					}

					board.ended_at = chrono::Utc::now();
					return TestResult::Failed(board);
				}
			}
		};

		{
			{
				let mut l = self.logger.lock().unwrap();
				l.in_progress("Connecting to serial port...");
			}

			let start = chrono::Utc::now();
			let serial = serialport::new("/dev/ttyUSB0", 115200)
				.timeout(time::Duration::from_millis(10000))
				.data_bits(serialport::DataBits::Eight)
				.open();
			let end = chrono::Utc::now();

			let mut serial = match serial {
				Ok(serial) => {
					{
						let mut l = self.logger.lock().unwrap();
						l.success("Serial port opened");
					}

					board.values.push(api::TestReportValue::new(
						"Serial",
						"Serial should work",
						true,
						None::<&str>,
						false,
						start,
						end,
					));

					serial
				}
				Err(error) => {
					{
						let mut l = self.logger.lock().unwrap();
						l.error(&format!("Failed to open serial port: {}", error));
						l.error("-> Serial port failed");
					}

					board.values.push(api::TestReportValue::new(
						"Serial",
						"Serial should work",
						false,
						Some(error),
						true,
						start,
						end,
					));

					board.ended_at = chrono::Utc::now();
					return TestResult::Failed(board);
				}
			};

			if let Err(e) = serial.clear(serialport::ClearBuffer::All) {
				println!("(warn) failed to clear serial port: {}", e);
			}

			// Read serial logs for checking the logs for sensor errors
			{
				{
					let mut l = self.logger.lock().unwrap();
					l.in_progress("Checking I2C connection to IMU...");
				}

				self.esp.reset_no_delay().unwrap();

				let start = chrono::Utc::now();
				match serial::read_string_until(
					&mut serial,
					vec!["[INFO ] [BNO080Sensor:0] Connected to BNO085 on 0x4a"],
					vec!["ERR", "[FATAL"],
				) {
					Ok(logs) => {
						{
							let mut l = self.logger.lock().unwrap();
							l.success("I2C to IMU working");
						}

						board.values.push(api::TestReportValue::new(
							"I2C to IMU",
							"I2C to IMU should work",
							true,
							Some(logs),
							false,
							start,
							chrono::Utc::now(),
						));
					}
					Err(logs) => {
						{
							let mut l = self.logger.lock().unwrap();
							l.error("I2C to IMU faulty");
							l.error(&logs);
						}

						board.values.push(api::TestReportValue::new(
							"I2C to IMU",
							"I2C to IMU should work",
							false,
							Some(logs),
							true,
							start,
							chrono::Utc::now(),
						));

						board.ended_at = chrono::Utc::now();
						return TestResult::Failed(board);
					}
				};
			};

			thread::sleep(time::Duration::from_millis(100));

			{
				{
					let mut l = self.logger.lock().unwrap();
					l.in_progress("Checking IMU via `GET TEST` command...");
				}

				let start = chrono::Utc::now();
				if let Err(e) = serial::write(&mut serial, b"GET TEST\n") {
					{
						let mut l = self.logger.lock().unwrap();
						l.error(&format!("Failed to write to serial port: {}", e));
					}

					board.values.push(api::TestReportValue::new(
						"IMU test",
						"IMU test should work",
						false,
						Some(format!("Failed to write to serial port: {}", e)),
						true,
						start,
						chrono::Utc::now(),
					));

					board.ended_at = chrono::Utc::now();
					return TestResult::Failed(board);
				}

				match serial::read_string_until(
					&mut serial,
					vec!["Sensor 1 sent some data, looks working."],
					vec!["Sensor 1 didn't send any data yet!"],
				) {
					Ok(logs) => {
						{
							let mut l = self.logger.lock().unwrap();
							l.success("IMU test successful");
						}

						board.values.push(api::TestReportValue::new(
							"IMU test",
							"IMU test should work",
							true,
							Some(logs),
							false,
							start,
							chrono::Utc::now(),
						));
					}
					Err(logs) => {
						{
							let mut l = self.logger.lock().unwrap();
							l.error("IMU test failed");
							l.error(&logs);
						}

						board.values.push(api::TestReportValue::new(
							"IMU test",
							"IMU test should work",
							false,
							Some(logs),
							true,
							start,
							chrono::Utc::now(),
						));

						board.ended_at = chrono::Utc::now();
						return TestResult::Failed(board);
					}
				};
			};
		}

		board.ended_at = chrono::Utc::now();
		TestResult::Passed(board)
	}
}
