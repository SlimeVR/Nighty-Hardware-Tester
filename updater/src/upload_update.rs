use regex::Regex;

use crate::{helpers::ESP, options::Options};

use super::helpers::{logger, pio, serial, usb};

use std::{io, thread, time};

const USB_VENDOR_ID: u16 = 0x1a86;
const USB_PRODUCT_ID: u16 = 0x7523;

pub struct UploadUpdateExecutor {
	options: Options,
	ip_regex: Regex,
}

impl UploadUpdateExecutor {
	pub fn new(options: Options) -> Self {
		Self {
			options,
			ip_regex: Regex::new("((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}").unwrap(),
		}
	}

	pub fn wait_for_device_connect(&mut self) {
		usb::wait_until_device_is_connected(USB_VENDOR_ID, USB_PRODUCT_ID);
	}

	pub fn wait_for_device_disconnect(&mut self) {
		usb::wait_until_device_is_disconnected(USB_VENDOR_ID, USB_PRODUCT_ID);
	}

	pub fn run(&mut self) -> Result<(), io::Error> {
		thread::sleep(time::Duration::from_millis(250));

		let mut serial = {
			logger::in_progress("Connecting to serial port...");

			let serial = serialport::new("COM3", 115200)
				.timeout(time::Duration::from_millis(10000))
				.data_bits(serialport::DataBits::Eight)
				.open();

			let serial = match serial {
				Ok(serial) => {
					logger::success("Serial port opened");

					serial
				}
				Err(error) => {
					logger::error(&format!("Failed to open serial port: {}", error));
					logger::error("-> Serial port failed");

					return Err(io::Error::new(
						match error.kind {
							serialport::ErrorKind::Io(e) => e,
							_ => io::ErrorKind::Other,
						},
						error.description,
					));
				}
			};

			if let Err(e) = serial.clear(serialport::ClearBuffer::All) {
				println!("(warn) failed to clear serial port: {}", e);
			}

			serial
		};

		serial
			.clear(serialport::ClearBuffer::All)
			.map_err(|v| io::Error::new(io::ErrorKind::Other, v))?;

		logger::in_progress("Setting WiFi credentials...");
		serial::write(
			&mut serial,
			format!(
				"SET WIFI \"{}\" \"{}\"\n",
				self.options.ssid, self.options.password
			)
			.as_bytes(),
		)
		.map_err(|v| io::Error::new(io::ErrorKind::Other, v))?;

		let ip = {
			logger::in_progress("Waiting for network connection");

			match serial::read_string_until(
				&mut serial,
				vec!["Connected successfully to SSID"],
				vec![],
			) {
				Ok(logs) => {
					logger::success("Found network connection message");

					self.ip_regex.find(&logs).unwrap().as_str().to_string()
				}
				Err(_) => unreachable!(),
			}
		};

		thread::sleep(time::Duration::from_secs(2));

		let mut esp = ESP { serial, ip };

		{
			logger::in_progress("Flashing...");

			let result = pio::flash("esp12e", &mut esp);

			match result {
				Ok(_) => logger::success("Flashing successful"),
				Err(e) => {
					logger::error(&format!("Flashing: {}", e));
					logger::error("-> Flashing failed");

					return Err(e);
				}
			}
		};

		thread::sleep(time::Duration::from_secs(2));

		logger::in_progress("Resetting device to factory defaults...");
		serial::write(&mut esp.serial, b"FRST\n")
			.map_err(|v| io::Error::new(io::ErrorKind::Other, v))?;

		logger::in_progress("Waiting for startup message...");
		match serial::read_string_until(&mut esp.serial, vec!["starting up"], vec![]) {
			Ok(_) => (),
			Err(_) => unreachable!(),
		};

		Ok(())
	}
}
