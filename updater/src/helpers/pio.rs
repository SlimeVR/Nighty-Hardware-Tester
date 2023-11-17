use std::io;
use std::process;

use super::ESP;

pub fn build(environment: &str) -> Result<(), io::Error> {
	let status = process::Command::new("pio")
		.arg("run")
		.arg("-e")
		.arg(environment)
		.current_dir("./slimevr-tracker-esp")
		.stdout(process::Stdio::inherit())
		.stderr(process::Stdio::inherit())
		.status()?;

	if !status.success() {
		return Err(io::Error::new(
			io::ErrorKind::Other,
			"`pio` exited with non-zero exit code".to_string(),
		));
	}

	Ok(())
}

pub fn flash(environment: &str, esp: &mut ESP) -> Result<(), io::Error> {
	let status = process::Command::new("pio")
		.arg("run")
		.arg("-t")
		.arg("upload")
		.arg("-e")
		.arg(environment)
		.arg("--upload-port")
		.arg(esp.ip.clone())
		.current_dir("./slimevr-tracker-esp")
		.stdout(process::Stdio::inherit())
		.stderr(process::Stdio::inherit())
		.status()?;

	if !status.success() {
		return Err(io::Error::new(
			io::ErrorKind::Other,
			"`pio` exited with non-zero exit code".to_string(),
		));
	}
	Ok(())
}
