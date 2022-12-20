use serde::Serialize;
use uuid::Uuid;

#[derive(Serialize)]
pub struct ApiRequestBody {
    pub method: String,
    pub params: ApiRequestBodyParams,
}

#[derive(Serialize)]
#[serde(untagged)]
pub enum ApiRequestBodyParams {
    ApiRequestBodyInsertTestReport(TestReport),
}

#[derive(Serialize)]
pub struct TestReport {
    pub id: String,
    #[serde(rename = "type")]
    pub _type: String,
    pub values: Vec<TestReportValue>,
}

#[derive(Serialize, Clone)]
pub struct TestReportValue {
    pub id: String,
    pub failed: bool,
    pub message: String,
    pub condition: String,
    pub value: String,
}

impl TestReportValue {
    pub fn new(failed: bool, message: String, condition: String, value: String) -> TestReportValue {
        TestReportValue {
            id: Uuid::new_v4().to_string(),
            failed,
            message,
            condition,
            value,
        }
    }
}
