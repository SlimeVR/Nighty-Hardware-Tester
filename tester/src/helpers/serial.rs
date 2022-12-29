use std::io::Write;

type ReadBuffer = [u8; 256];

fn read(serial: &mut Box<dyn serialport::SerialPort>) -> Result<(ReadBuffer, usize), String> {
    let mut buf = [0u8; 256];

    match serial.read(&mut buf) {
        Ok(bytes_read) => Ok((buf, bytes_read)),
        Err(e) => return Err(format!("could not read from serial port: {}", e)),
    }
}

pub fn read_string(serial: &mut Box<dyn serialport::SerialPort>) -> Result<String, String> {
    let (buf, bytes_read) = read(serial)?;

    Ok(String::from_utf8_lossy(&buf[..bytes_read])
        .to_string()
        .replace("\u{0000}", ""))
}

pub fn write(serial: &mut Box<dyn serialport::SerialPort>, data: &[u8]) -> Result<(), String> {
    if let Err(e) = serial.write_all(data) {
        return Err(format!("could not write to serial port: {}", e));
    }

    if let Err(e) = serial.flush() {
        return Err(format!("could not flush serial port: {}", e));
    }

    Ok(())
}
