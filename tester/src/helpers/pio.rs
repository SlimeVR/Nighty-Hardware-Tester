use std::{io, process};

use rppal::gpio;

use crate::esp;

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

pub fn flash(environment: &str, esp: &mut esp::ESP) -> gpio::Result<String> {
    esp.reset_for_upload()?;

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

    esp.reset()?;

    if !c.status.success() {
        return Err(gpio::Error::Io(io::Error::new(
            io::ErrorKind::Other,
            format!("`pio` exited with non-zero exit code: {output}"),
        )));
    }

    Ok(output.clone().to_string())
}
