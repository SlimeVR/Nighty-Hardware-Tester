use serde::{Deserialize, Serialize};

#[derive(Serialize)]
pub struct ApiRequestBody<'a> {
    pub method: &'a str,
    pub params: ApiRequestBodyParams<'a>,
}

#[derive(Serialize)]
#[serde(untagged)]
pub enum ApiRequestBodyParams<'a> {
    ApiRequestBodyInsertTestReport(TestReport<'a>),
}

#[derive(Serialize)]
pub struct TestReport<'a> {
    pub id: &'a str,
    #[serde(rename = "type")]
    pub _type: &'a str,
    pub values: Vec<TestReportValue>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TestReportValue {
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
            step: step.to_string(),
            condition: condition.to_string(),
            value: value.to_string(),
            logs: logs.map(|x| x.to_string()),
            failed,
        }
    }
}
