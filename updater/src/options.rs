use std::env;

#[derive(Clone)]
pub enum FlashWith {
	PlatformIO,
}

#[derive(Clone)]
pub struct Options {
	pub no_build: bool,
	pub ssid: String,
	pub password: String,
}

impl Options {
	pub fn parse() -> Self {
		let no_build = env::var("BUILD").map(|v| v == "no").unwrap_or(false);
		let ssid = env::var("SSID").unwrap_or("".to_string());
		let password = env::var("PASSWORD").unwrap_or("".to_string());

		Self {
			no_build,
			ssid,
			password,
		}
	}
}
