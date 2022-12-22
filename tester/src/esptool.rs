use std::{io, process};

use rppal::gpio;

pub struct ReadMacAddressResult {
    pub mac: String,
    pub log: String,
}

pub fn read_mac_address(
    flash_pin: &mut gpio::OutputPin,
    rst_pin: &mut gpio::OutputPin,
    enable_flashing: &dyn Fn(&mut gpio::OutputPin, &mut gpio::OutputPin) -> gpio::Result<()>,
    reset_chip: &dyn Fn(&mut gpio::OutputPin) -> gpio::Result<()>,
) -> gpio::Result<ReadMacAddressResult> {
    enable_flashing(flash_pin, rst_pin)?;

    let c = process::Command::new("esptool")
        .arg("--port")
        .arg("/dev/ttyUSB0")
        .arg("read_mac")
        .output()?;

    let output = String::from_utf8_lossy(&c.stdout);
    println!("{}", output);

    reset_chip(rst_pin)?;

    if !c.status.success() {
        return Err(gpio::Error::Io(io::Error::new(
            io::ErrorKind::Other,
            format!("`esptool` exited with non-zero exit code: {output}"),
        )));
    }

    let mac_address = output.lines().filter(|l| l.contains("MAC:")).nth(0);
    if mac_address.is_none() {
        return Err(gpio::Error::Io(io::Error::new(
            io::ErrorKind::Interrupted,
            "No MAC address found",
        )));
    }

    let mac_address = mac_address.unwrap().split("MAC: ").nth(1);
    if mac_address.is_none() {
        return Err(gpio::Error::Io(io::Error::new(
            io::ErrorKind::Interrupted,
            "No MAC address found",
        )));
    }

    let mac_address = mac_address.unwrap();

    Ok(ReadMacAddressResult {
        mac: mac_address.to_string(),
        log: output.clone().to_string(),
    })
}

pub fn write_flash(
    file: &str,
    flash_pin: &mut gpio::OutputPin,
    rst_pin: &mut gpio::OutputPin,
    enable_flashing: &dyn Fn(&mut gpio::OutputPin, &mut gpio::OutputPin) -> gpio::Result<()>,
    reset_chip: &dyn Fn(&mut gpio::OutputPin) -> gpio::Result<()>,
) -> gpio::Result<String> {
    enable_flashing(flash_pin, rst_pin)?;

    let c = process::Command::new("/usr/bin/python3")
        .arg("/home/pi/.platformio/packages/tool-esptoolpy/esptool.py")
        .arg("--before")
        .arg("no_reset")
        .arg("--after")
        .arg("no_reset")
        .arg("--chip")
        .arg("esp8266")
        .arg("--port")
        .arg("/dev/ttyUSB0")
        .arg("--baud")
        .arg("921600")
        .arg("write_flash")
        .arg("-fm")
        .arg("qio")
        .arg("0x0000")
        .arg(file)
        .output()?;

    let output = String::from_utf8_lossy(&c.stdout);
    println!("{}", output);

    reset_chip(rst_pin)?;

    if !c.status.success() {
        return Err(gpio::Error::Io(io::Error::new(
            io::ErrorKind::Other,
            format!("`esptool` exited with non-zero exit code: {output}"),
        )));
    }

    Ok(output.clone().to_string())
}
