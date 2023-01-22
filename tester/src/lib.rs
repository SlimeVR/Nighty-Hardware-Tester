pub mod api;
mod helpers;
pub mod options;
pub mod test_executors;

pub use helpers::*;

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct Board {
    pub id: Option<String>,
    pub values: Vec<api::TestReportValue>,
}

pub enum TestResult {
    Failed(Board),
    Passed(Board),
}
