use reqwest::blocking;

use crate::options;

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

    pub fn from_options(options: &options::Options) -> Client {
        Client::new(options.rpc_url.clone(), options.rpc_password.clone())
    }

    pub fn send_test_report(
        &self,
        _type: &str,
        id: impl AsRef<str>,
        tester: impl AsRef<str>,
        values: &[TestReportValue],
        started_at: chrono::DateTime<chrono::Utc>,
        ended_at: chrono::DateTime<chrono::Utc>,
    ) -> Result<blocking::Response, reqwest::Error> {
        let body = ApiRequestBody {
            method: "insert_test_report",
            params: ApiRequestBodyInsertTestReport(TestReport {
                id: id.as_ref(),
                _type,
                tester: tester.as_ref(),
                values: values.to_vec(),
                started_at,
                ended_at,
            }),
        };

        self.client
            .post(&self.rpc_url)
            .header("authorization", &self.rpc_password)
            .json(&body)
            .send()
    }
}
