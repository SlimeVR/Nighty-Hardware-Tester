use std::{process, thread::sleep, time::Duration};

use rppal::gpio;

pub fn read_mac_address(
    flash_pin: &mut gpio::OutputPin,
    rst_pin: &mut gpio::OutputPin,
    enable_flashing: &dyn Fn(&mut gpio::OutputPin, &mut gpio::OutputPin) -> gpio::Result<()>,
    reset_chip: &dyn Fn(&mut gpio::OutputPin) -> gpio::Result<()>,
) -> gpio::Result<String> {
    enable_flashing(flash_pin, rst_pin)?;

    let c = process::Command::new("esptool")
        .arg("--port")
        .arg("/dev/ttyUSB0")
        .arg("read_mac")
        .output()?;

    let output = String::from_utf8_lossy(&c.stdout);

    println!("{}", output);

    let mac_address = output
        .lines()
        .filter(|l| l.contains("MAC:"))
        .nth(0)
        .unwrap()
        .split("MAC: ")
        .nth(1)
        .unwrap();

    reset_chip(rst_pin)?;

    sleep(Duration::from_millis(500));

    Ok(mac_address.to_string())
}
