use std::env;

pub enum FlashWith {
    PlatformIO,
    ESPTool,
}

pub struct Options {
    pub no_build: bool,
    pub flash_with: FlashWith,
    pub flash_baudrate: u32,
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

        let flash_baudrate = env::var("TESTER_FLASH_BAUDRATE")
            .map(|v| v.parse::<u32>().unwrap())
            .unwrap_or(921600);

        let rpc_url =
            env::var("TESTER_RPC_URL").unwrap_or("https://localhost:3000/api/rpc".to_string());
        let rpc_password = env::var("TESTER_RPC_PASSWORD").unwrap_or("password".to_string());

        Self {
            no_build,
            flash_with,
            flash_baudrate,
            rpc_url,
            rpc_password,
        }
    }
}
