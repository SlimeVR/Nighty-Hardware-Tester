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
    pub step: String,
    pub condition: String,
    pub value: String,
    pub logs: Option<String>,
    pub failed: bool,
}

impl TestReportValue {
    pub fn new(
        step: impl ToString,
        condition: impl ToString,
        value: impl ToString,
        logs: Option<impl ToString>,
        failed: bool,
    ) -> TestReportValue {
        TestReportValue {
            id: Uuid::new_v4().to_string(),
            step: step.to_string(),
            condition: condition.to_string(),
            value: value.to_string(),
            logs: logs.map(|x| x.to_string()),
            failed,
        }
    }
}
