use std::env;

pub enum FlashWith {
    PlatformIO,
    ESPTool,
}

pub struct Options {
    pub no_build: bool,
    pub flash_with: FlashWith,
    pub rpc_url: String,
    pub rpc_password: String,
}

impl Options {
    pub fn parse() -> Self {
        let no_build = env::var("TESTER_BUILD").map(|v| v == "no").unwrap_or(false);

        let flash_with = env::var("TESTER_FLASH_WITH")
            .map(|v| match v.as_ref() {
                "pio" => FlashWith::PlatformIO,
                "esptool" => FlashWith::ESPTool,
                _ => panic!("TESTER_FLASH_WITH must be either 'pio' or 'esptool'"),
            })
            .unwrap_or(FlashWith::ESPTool);

        let rpc_url =
            env::var("TESTER_RPC_URL").unwrap_or("https://localhost:3000/api/rpc".to_string());
        let rpc_password = env::var("TESTER_RPC_PASSWORD").unwrap_or("password".to_string());

        Self {
            no_build,
            flash_with,
            rpc_url,
            rpc_password,
        }
    }
}
