use std::{process, thread, time};

use rppal::gpio;

pub fn build(environment: &str) -> gpio::Result<()> {
    process::Command::new("pio")
        .arg("run")
        .arg("-e")
        .arg(environment)
        .current_dir("/home/pi/slimevr-tracker-esp")
        .output()?;

    Ok(())
}

pub fn flash(
    environment: &str,
    flash_pin: &mut gpio::OutputPin,
    rst_pin: &mut gpio::OutputPin,
    enable_flashing: &dyn Fn(&mut gpio::OutputPin, &mut gpio::OutputPin) -> gpio::Result<()>,
    reset_chip: &dyn Fn(&mut gpio::OutputPin) -> gpio::Result<()>,
) -> gpio::Result<()> {
    enable_flashing(flash_pin, rst_pin)?;

    let s = process::Command::new("pio")
        .arg("run")
        .arg("-t")
        .arg("upload")
        .arg("-e")
        .arg(environment)
        .arg("--upload-port")
        .arg("/dev/ttyUSB0")
        .current_dir("/home/pi/slimevr-tracker-esp")
        .output()?;

    thread::sleep(time::Duration::from_millis(100));

    reset_chip(rst_pin)?;

    Ok(())
}
