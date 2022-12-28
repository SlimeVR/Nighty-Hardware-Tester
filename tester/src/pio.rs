use std::{io, process};

use rppal::gpio;

pub fn build(environment: &str) -> gpio::Result<()> {
    let c = process::Command::new("pio")
        .arg("run")
        .arg("-e")
        .arg(environment)
        .current_dir("/home/pi/slimevr-tracker-esp")
        .output()?;

    let output = String::from_utf8_lossy(&c.stdout);
    println!("{}", output);

    if !c.status.success() {
        return Err(gpio::Error::Io(io::Error::new(
            io::ErrorKind::Other,
            format!("`pio` exited with non-zero exit code: {output}"),
        )));
    }

    Ok(())
}

pub fn flash(
    environment: &str,
    flash_pin: &mut gpio::OutputPin,
    rst_pin: &mut gpio::OutputPin,
    enable_flashing: impl Fn(&mut gpio::OutputPin, &mut gpio::OutputPin) -> gpio::Result<()>,
    reset_chip: impl Fn(&mut gpio::OutputPin) -> gpio::Result<()>,
) -> gpio::Result<String> {
    enable_flashing(flash_pin, rst_pin)?;

    let c = process::Command::new("pio")
        .arg("run")
        .arg("-t")
        .arg("upload")
        .arg("-e")
        .arg(environment)
        .arg("--upload-port")
        .arg("/dev/ttyUSB0")
        .current_dir("/home/pi/slimevr-tracker-esp")
        .output()?;

    let output = String::from_utf8_lossy(&c.stdout);
    println!("{}", output);

    reset_chip(rst_pin)?;

    if !c.status.success() {
        return Err(gpio::Error::Io(io::Error::new(
            io::ErrorKind::Other,
            format!("`pio` exited with non-zero exit code: {output}"),
        )));
    }

    Ok(output.clone().to_string())
}
