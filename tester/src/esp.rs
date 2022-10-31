use std::process;

use rppal::gpio;

use crate::log;

pub fn read_chip_id(
    l: &mut log::LogCtx,
    flash_pin: &mut gpio::OutputPin,
    rst_pin: &mut gpio::OutputPin,
    enable_flashing: &dyn Fn(
        &mut log::LogCtx,
        &mut gpio::OutputPin,
        &mut gpio::OutputPin,
    ) -> gpio::Result<()>,
) -> gpio::Result<String> {
    l.enter("read_chip_id");

    l.dbg("Reading chip ID");

    enable_flashing(l, flash_pin, rst_pin)?;

    l.dbg("Running esptool chip_id");
    l.trc("=======================");
    let c = process::Command::new("esptool")
        .arg("--port")
        .arg("/dev/ttyUSB0")
        .arg("chip_id")
        .stderr(process::Stdio::inherit())
        .output()?;

    let output = String::from_utf8_lossy(&c.stdout);
    println!("{}", output.trim());

    l.trc("=======================");

    let chip_id = output
        .lines()
        .filter(|l| l.contains("Chip ID:"))
        .nth(0)
        .unwrap()
        .split("Chip ID: ")
        .nth(1)
        .unwrap();

    l.inf(format!("Chip ID: {}", chip_id).as_str());

    l.leave("read_chip_id");

    Ok(chip_id.to_string())
}

pub fn read_mac_address(
    l: &mut log::LogCtx,
    flash_pin: &mut gpio::OutputPin,
    rst_pin: &mut gpio::OutputPin,
    enable_flashing: &dyn Fn(
        &mut log::LogCtx,
        &mut gpio::OutputPin,
        &mut gpio::OutputPin,
    ) -> gpio::Result<()>,
) -> gpio::Result<String> {
    l.enter("read_mac_address");

    l.dbg("Reading MAC address");

    enable_flashing(l, flash_pin, rst_pin)?;

    l.dbg("Running esptool read_mac");
    l.trc("========================");
    let c = process::Command::new("esptool")
        .arg("--port")
        .arg("/dev/ttyUSB0")
        .arg("read_mac")
        .stderr(process::Stdio::inherit())
        .output()?;

    let output = String::from_utf8_lossy(&c.stdout);
    println!("{}", output.trim());

    l.trc("========================");

    let mac_address = output
        .lines()
        .filter(|l| l.contains("MAC:"))
        .nth(0)
        .unwrap()
        .split("MAC: ")
        .nth(1)
        .unwrap();

    l.inf(format!("MAC address: {}", mac_address).as_str());

    Ok(mac_address.to_string())
}
