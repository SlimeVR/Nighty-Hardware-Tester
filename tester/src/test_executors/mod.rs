use crate::TestResult;

pub mod auxboard;
pub mod mainboard;

pub trait TestExecutor {
	fn wait_for_device_connect(&mut self);
	fn run(&mut self) -> TestResult;
	fn wait_for_device_disconnect(&mut self);
}
