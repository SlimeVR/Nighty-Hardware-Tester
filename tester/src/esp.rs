use std::process;

use rppal::gpio;

pub fn read_chip_id(
    flash_pin: &mut gpio::OutputPin,
    rst_pin: &mut gpio::OutputPin,
    enable_flashing: &dyn Fn(&mut gpio::OutputPin, &mut gpio::OutputPin) -> gpio::Result<()>,
    reset_chip: &dyn Fn(&mut gpio::OutputPin) -> gpio::Result<()>,
) -> gpio::Result<String> {
    let s = tracing::span!(tracing::Level::DEBUG, "read_chip_id");
    let _enter = s.enter();

    log::debug!("Reading chip ID");

    enable_flashing(flash_pin, rst_pin)?;

    log::debug!("Running esptool chip_id");
    log::trace!("=======================");
    let c = process::Command::new("esptool")
        .arg("--port")
        .arg("/dev/ttyUSB0")
        .arg("chip_id")
        .stderr(process::Stdio::inherit())
        .output()?;

    let output = String::from_utf8_lossy(&c.stdout);
    println!("{}", output.trim());

    log::trace!("=======================");

    let chip_id = output
        .lines()
        .filter(|l| l.contains("Chip ID:"))
        .nth(0)
        .unwrap()
        .split("Chip ID: ")
        .nth(1)
        .unwrap();

    log::info!("Chip ID: {}", chip_id);

    reset_chip(rst_pin)?;

    Ok(chip_id.to_string())
}

pub fn read_mac_address(
    flash_pin: &mut gpio::OutputPin,
    rst_pin: &mut gpio::OutputPin,
    enable_flashing: &dyn Fn(&mut gpio::OutputPin, &mut gpio::OutputPin) -> gpio::Result<()>,
    reset_chip: &dyn Fn(&mut gpio::OutputPin) -> gpio::Result<()>,
) -> gpio::Result<String> {
    let s = tracing::span!(tracing::Level::DEBUG, "read_mac_address");
    let _enter = s.enter();

    log::debug!("Reading MAC address");

    enable_flashing(flash_pin, rst_pin)?;

    log::debug!("Running esptool read_mac");
    log::trace!("========================");
    let c = process::Command::new("esptool")
        .arg("--port")
        .arg("/dev/ttyUSB0")
        .arg("read_mac")
        .stderr(process::Stdio::inherit())
        .output()?;

    let output = String::from_utf8_lossy(&c.stdout);
    println!("{}", output.trim());

    log::trace!("========================");

    let mac_address = output
        .lines()
        .filter(|l| l.contains("MAC:"))
        .nth(0)
        .unwrap()
        .split("MAC: ")
        .nth(1)
        .unwrap();

    log::info!("MAC address: {}", mac_address);

    reset_chip(rst_pin)?;

    Ok(mac_address.to_string())
}
