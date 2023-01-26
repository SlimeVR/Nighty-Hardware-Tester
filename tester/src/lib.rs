pub mod api;
mod helpers;
pub mod options;
pub mod test_executors;

pub use helpers::*;

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct Board {
	pub id: Option<String>,
	pub values: Vec<api::TestReportValue>,
	pub started_at: chrono::DateTime<chrono::Utc>,
	pub ended_at: chrono::DateTime<chrono::Utc>,
}

impl Board {
	pub fn new() -> Board {
		Board {
			id: None,
			values: Vec::new(),
			started_at: chrono::Utc::now(),
			ended_at: chrono::DateTime::<chrono::Utc>::MIN_UTC,
		}
	}

	pub fn add_value(&mut self, value: api::TestReportValue) {
		self.values.push(value);
	}
}

pub enum TestResult {
	Failed(Board),
	Passed(Board),
}
