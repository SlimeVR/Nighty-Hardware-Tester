use reqwest::blocking;

use super::{
    ApiRequestBody, ApiRequestBodyParams::ApiRequestBodyInsertTestReport, TestReport,
    TestReportValue,
};

pub struct Client {
    rpc_url: String,
    rpc_password: String,
    client: blocking::Client,
}

impl Client {
    pub fn new(rpc_url: String, rpc_password: String) -> Client {
        Client {
            rpc_url,
            rpc_password,
            client: blocking::Client::new(),
        }
    }

    pub fn send_test_report(
        &self,
        _type: &str,
        id: impl AsRef<str>,
        values: &[TestReportValue],
    ) -> Result<blocking::Response, reqwest::Error> {
        let body = ApiRequestBody {
            method: "insert_test_report",
            params: ApiRequestBodyInsertTestReport(TestReport {
                id: id.as_ref(),
                _type,
                values: values.to_vec(),
            }),
        };

        self.client
            .post(&self.rpc_url)
            .header("authorization", &self.rpc_password)
            .json(&body)
            .send()
    }
}
