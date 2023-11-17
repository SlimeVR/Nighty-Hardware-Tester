use colored::Colorize;
use std::io::Write;

use super::logger;

type ReadBuffer = [u8; 256];
pub type Serial = Box<dyn serialport::SerialPort>;

fn read(serial: &mut Serial) -> Result<(ReadBuffer, usize), String> {
	let mut buf = [0u8; 256];

	match serial.read(&mut buf) {
		Ok(bytes_read) => Ok((buf, bytes_read)),
		Err(e) => Err(format!("could not read from serial port: {}", e)),
	}
}

pub fn read_string(serial: &mut Serial) -> Result<String, String> {
	let (buf, bytes_read) = read(serial)?;

	Ok(String::from_utf8_lossy(&buf[..bytes_read])
		.to_string()
		.replace('\u{0000}', ""))
}

pub fn write(serial: &mut Serial, data: &[u8]) -> Result<(), String> {
	if let Err(e) = serial.write_all(data) {
		return Err(format!("could not write to serial port: {}", e));
	}

	if let Err(e) = serial.flush() {
		return Err(format!("could not flush serial port: {}", e));
	}

	Ok(())
}

pub fn read_some(serial: &mut Serial, prev: String) -> Result<(Vec<String>, String), String> {
	let mut buf = prev;

	loop {
		let str = match read_string(serial) {
			Ok(str) => str,
			Err(e) => return Err("Buffer: ".to_string() + &buf + "\nError: " + &e),
		};

		buf.push_str(&str);

		print!("{}", str);

		if buf.contains('\n') {
			let mut lines = buf.split('\n').map(|v| v.to_string()).collect::<Vec<_>>();

			let last = lines.pop().unwrap();

			return Ok((lines, last));
		}
	}
}

pub fn read_string_until(
	serial: &mut Serial,
	positive: Vec<&str>,
	negative: Vec<&str>,
) -> Result<String, String> {
	let mut lines: Vec<String> = Vec::new();

	logger::in_progress("Streaming logs from serial port...");
	logger::in_progress("==================================");

	let mut buf = String::new();

	loop {
		let (new_lines, rest) = match read_some(serial, buf.clone()) {
			Ok(data) => data,
			Err(e) => return Err("Lines: ".to_string() + &lines.join("\n") + "\nError: " + &e),
		};

		buf = rest;

		lines.extend(new_lines.clone());

		for p in &positive {
			for line in &new_lines {
				if line.contains(p) {
					logger::debug(&format!("> {}", line.color(colored::Color::Green)));

					return Ok(lines.join("\n"));
				}
			}
		}

		for n in &negative {
			for line in &new_lines {
				if line.contains(n) {
					logger::debug(&format!("> {}", line.color(colored::Color::BrightBlack)));

					return Err(lines.join("\n") + &format!("\nnegative match: {}", line));
				}
			}
		}
	}
}
