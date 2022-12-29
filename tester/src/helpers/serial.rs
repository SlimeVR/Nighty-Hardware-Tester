use std::io::Write;

use colored::Colorize;

type ReadBuffer = [u8; 256];
type Serial = Box<dyn serialport::SerialPort>;

fn read(serial: &mut Serial) -> Result<(ReadBuffer, usize), String> {
    let mut buf = [0u8; 256];

    match serial.read(&mut buf) {
        Ok(bytes_read) => Ok((buf, bytes_read)),
        Err(e) => return Err(format!("could not read from serial port: {}", e)),
    }
}

pub fn read_string(serial: &mut Serial) -> Result<String, String> {
    let (buf, bytes_read) = read(serial)?;

    Ok(String::from_utf8_lossy(&buf[..bytes_read])
        .to_string()
        .replace("\u{0000}", ""))
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

pub fn read_line(serial: &mut Serial) -> Result<String, String> {
    let mut buf = String::new();

    loop {
        let str = read_string(serial)?;

        buf.push_str(&str);

        if str.ends_with("\n") {
            break;
        }
    }

    Ok(buf)
}

pub fn read_string_until(
    serial: &mut Serial,
    positive: Vec<&str>,
    negative: Vec<&str>,
) -> Result<String, String> {
    let mut lines = Vec::new();

    println!("Streaming logs from serial port...");
    println!("==================================");

    loop {
        let line = read_line(serial)?;
        lines.push(line.clone());

        for p in &positive {
            if line.contains(p) {
                println!("> {}", line.color(colored::Color::Green));

                return Ok(lines.join("\n"));
            }
        }

        for n in &negative {
            if line.contains(n) {
                println!("> {}", line.color(colored::Color::BrightBlack));

                return Err(lines.join("\n") + &format!("\nnegative match: {}", line));
            }
        }

        println!("> {}", line.color(colored::Color::BrightBlack));
    }
}
