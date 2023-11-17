pub mod logger;
pub mod pio;
pub mod serial;
pub mod usb;

pub struct ESP {
	pub(crate) serial: serial::Serial,
	pub(crate) ip: String,
}
