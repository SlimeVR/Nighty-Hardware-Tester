use ads1x1x::ChannelSelection;
use rppal::{
    gpio::{Gpio, OutputPin, Result},
    i2c::I2c,
};
use std::{
    env,
    sync::{Arc, Mutex},
    thread::{sleep, spawn},
    time::Duration,
};
use tester::{
    adc,
    api::{
        ApiRequestBody, ApiRequestBodyParams::ApiRequestBodyInsertTestReport, TestReport,
        TestReportValue,
    },
    esptool, pio, tui, usb,
};
use uuid::Uuid;

const USB_VENDOR_ID: u16 = 0x1a86;
const USB_PRODUCT_ID: u16 = 0x7523;

fn reset_esp(pin: &mut OutputPin) -> Result<()> {
    pin.set_low();

    sleep(Duration::from_millis(500));

    pin.set_high();

    Ok(())
}

fn enable_flashing(flash_pin: &mut OutputPin, rst_pin: &mut OutputPin) -> Result<()> {
    flash_pin.set_low();

    reset_esp(rst_pin)?;

    sleep(Duration::from_millis(500));

    flash_pin.set_high();

    Ok(())
}

#[derive(Clone)]
struct Board {
    mac: Option<String>,
    values: Vec<TestReportValue>,
}

fn main() {
    let flash_with_pio = env::var("TESTER_FLASH_WITH_PIO").unwrap_or("0".to_string()) == "1";

    let t = tui::TUI::new().unwrap();

    let (mut renderer, mut reporter) = t.split();
    let boards_to_upload: Arc<Mutex<Vec<Board>>> = Arc::new(Mutex::new(Vec::new()));

    let reports_to_upload_clone = boards_to_upload.clone();
    spawn(move || {
        if flash_with_pio {
            reporter.in_progress("Skipping firmware build, flashing with PlatformIO...");
            sleep(Duration::from_millis(500));
            reporter.reset();
        } else {
            if env::var("TESTER_NO_BUILD").unwrap_or("0".to_string()) == "1" {
                reporter.in_progress("Skipping firmware build, building disabled via env...");
                sleep(Duration::from_millis(500));
                reporter.reset();
            } else {
        reporter.in_progress("Building firmware...");
        pio::build("esp12e").unwrap();
        reporter.reset();
            }
        }

        let i2c = I2c::with_bus(1).unwrap();
        let gpio = Gpio::new().unwrap();

        let mut rst_pin = gpio.get(6).unwrap().into_output_high();
        let mut flash_pin = gpio.get(22).unwrap().into_output_high();

        let mut adc = adc::Ads1115::new(i2c).unwrap();

        loop {
            let mut board = Board {
                mac: None,
                values: Vec::new(),
            };

            reporter.action("[ Please connect the device ]".to_string());

            usb::wait_until_device_is_connected(USB_VENDOR_ID, USB_PRODUCT_ID);

            let wait_for_next_board = |reporter: &mut tui::Reporter| {
                reporter.action("[ Please disconnect the device ]".to_string());

                usb::wait_until_device_is_disconnected(USB_VENDOR_ID, USB_PRODUCT_ID);

                reporter.reset();
            };

            sleep(Duration::from_millis(1000));

            reporter.reset();

            reporter.success("Device connected");

            let err = {
                reporter.in_progress("Measuring VOUT...");
                let vout_err = match adc.measure(ChannelSelection::SingleA2) {
                    Ok(v) => {
                        let vout_err = v < 4.5 || v > 5.2;
                        if vout_err {
                            reporter.error(&format!("VOUT voltage: {}V (> 4.5V < 5.2V)", v));
                        } else {
                            reporter.success(&format!("VOUT voltage: {}V", v));
                        }
                        board.values.push(TestReportValue::new(
                            vout_err,
                            "".to_string(),
                            "4.5V > VOUT < 5.2V".to_string(),
                            v.to_string(),
                        ));

                        vout_err
                    }
                    Err(e) => match e {
                        nb::Error::WouldBlock => {
                            board.values.push(TestReportValue::new(
                                true,
                                "err: would block".to_string(),
                                "4.5V > VOUT < 5.2V".to_string(),
                                "".to_string(),
                            ));

                            true
                        }
                        nb::Error::Other(e) => match e {
                            ads1x1x::Error::I2C(e) => {
                                board.values.push(TestReportValue::new(
                                    true,
                                    format!("err: i2c: {}", e),
                                    "4.5V > VOUT < 5.2V".to_string(),
                                    "".to_string(),
                                ));

                                true
                            }
                            ads1x1x::Error::InvalidInputData => {
                                board.values.push(TestReportValue::new(
                                    true,
                                    "err: invalid channel".to_string(),
                                    "4.5V > VOUT < 5.2V".to_string(),
                                    "".to_string(),
                                ));

                                true
                            }
                        },
                    },
                };

                reporter.in_progress("Measuring B+...");
                let bplus_err = match adc.measure(ChannelSelection::SingleA3) {
                    Ok(v) => {
                        let bplus_err = v < 4.0;
                        if bplus_err {
                            reporter.error(&format!("B+ voltage: {}V (> 4.0V)", v));
                        } else {
                            reporter.success(&format!("B+ voltage: {}V", v));
                        }

                        board.values.push(TestReportValue::new(
                            bplus_err,
                            "".to_string(),
                            "4.0V < B+".to_string(),
                            v.to_string(),
                        ));

                        bplus_err
                    }
                    Err(e) => match e {
                        nb::Error::WouldBlock => {
                            board.values.push(TestReportValue::new(
                                true,
                                "err: would block".to_string(),
                                "4.0V < B+".to_string(),
                                "".to_string(),
                            ));

                            true
                        }
                        nb::Error::Other(e) => match e {
                            ads1x1x::Error::I2C(e) => {
                                board.values.push(TestReportValue::new(
                                    true,
                                    format!("err: i2c: {}", e),
                                    "4.0V < B+".to_string(),
                                    "".to_string(),
                                ));

                                true
                            }
                            ads1x1x::Error::InvalidInputData => {
                                board.values.push(TestReportValue::new(
                                    true,
                                    "err: invalid channel".to_string(),
                                    "4.0V < B+".to_string(),
                                    "".to_string(),
                                ));

                                true
                            }
                        },
                    },
                };

                reporter.in_progress("Measuring 3V3...");
                let r3v3_err = match adc.measure(ChannelSelection::SingleA0) {
                    Ok(v) => {
                        let r3v3_err = v < 2.8 || v > 3.2;
                        if r3v3_err {
                            reporter.error(&format!("3V3 voltage: {}V (> 2.8V < 3.2V)", v));
                        } else {
                            reporter.success(&format!("3V3 voltage: {}V", v));
                        }
                        board.values.push(TestReportValue::new(
                            r3v3_err,
                            "".to_string(),
                            "2.8V > 3V3 < 3.2V".to_string(),
                            v.to_string(),
                        ));

                        r3v3_err
                    }
                    Err(e) => match e {
                        nb::Error::WouldBlock => {
                            board.values.push(TestReportValue::new(
                                true,
                                "err: would block".to_string(),
                                "2.8V > 3V3 < 3.2V".to_string(),
                                "".to_string(),
                            ));

                            true
                        }
                        nb::Error::Other(e) => match e {
                            ads1x1x::Error::I2C(e) => {
                                board.values.push(TestReportValue::new(
                                    true,
                                    format!("err: i2c: {}", e),
                                    "2.8V > 3V3 < 3.2V".to_string(),
                                    "".to_string(),
                                ));

                                true
                            }
                            ads1x1x::Error::InvalidInputData => {
                                board.values.push(TestReportValue::new(
                                    true,
                                    "err: invalid channel".to_string(),
                                    "2.8V > 3V3 < 3.2V".to_string(),
                                    "".to_string(),
                                ));

                                true
                            }
                        },
                    },
                };

                vout_err || bplus_err || r3v3_err
            };

            if err {
                reporter.error("-> Faulty power circuit");

                wait_for_next_board(&mut reporter);
                continue;
            }

            {
                reporter.in_progress("Reading MAC address...");
                match esptool::read_mac_address(
                    &mut flash_pin,
                    &mut rst_pin,
                    &enable_flashing,
                    &reset_esp,
                ) {
                    Ok(mac) => {
                        board.values.push(TestReportValue::new(
                            false,
                            "MAC address can be read".to_string(),
                            "!!mac_adress".to_string(),
                            mac.clone(),
                        ));
                        board.mac = Some(mac.clone());

                        reporter.success(&format!("Read MAC address: {}", mac));
                    }
                    Err(e) => {
                        board.values.push(TestReportValue::new(
                            true,
                            e.to_string(),
                            "!!mac_adress".to_string(),
                            "N/A".to_string(),
                        ));

                        reporter.error(&format!("Failed to read MAC address: {}", e));
                        reporter.error("-> ESP8266 faulty");

                        wait_for_next_board(&mut reporter);

                        continue;
                    }
                }
            };

            {
                reporter.in_progress("Flashing...");
                match if flash_with_pio {
                    pio::flash(
                        "esp12e",
                        &mut flash_pin,
                        &mut rst_pin,
                        &enable_flashing,
                        &reset_esp,
                    )
                } else {
                    esptool::write_flash(
                    "slimevr-tracker-esp/.pio/build/esp12e/firmware.bin",
                    &mut flash_pin,
                    &mut rst_pin,
                    &enable_flashing,
                    &reset_esp,
                    )
                } {
                    Ok(logs) => {
                        board.values.push(TestReportValue::new(
                            false,
                            logs,
                            "!!flashing".to_string(),
                            "true".to_string(),
                        ));

                        reporter.success("Flashed");
                    }
                    Err(e) => {
                        board.values.push(TestReportValue::new(
                            true,
                            e.to_string(),
                            "!!flashing".to_string(),
                            "false".to_string(),
                        ));

                        reporter.error(&format!("Flashing: {}", e));
                        reporter.error("-> Flashing failed");

                        wait_for_next_board(&mut reporter);

                        continue;
                    }
                }
            };

            {
                let err = {
                    let serial = serialport::new("/dev/ttyUSB0", 115200)
                        .timeout(Duration::from_millis(10000))
                        .data_bits(serialport::DataBits::Seven)
                        .open();

                    if serial.is_err() {
                        reporter.error("Failed to read logs: could not open serial port");

                        return Err::<String, String>(
                            "Failed to read logs: could not open serial port".to_string(),
                        );
                    }

                    let mut serial = serial.unwrap();

                    println!("Streaming logs from serial port...");
                    println!("==================================");

                    let mut buffer = String::new();
                    let mut buf = [0; 128];
                    let i2c_logs = loop {
                        let Ok( bytes_read) = serial.read(&mut buf) else {
                            break Err("Failed to read logs: could not read from serial port".to_string());
                        };

                        if bytes_read == 0 {
                            continue;
                        }

                        buffer += &String::from_utf8_lossy(&buf[..bytes_read]);
                        let logs = buffer.clone();

                        if let Some(i) = buffer.rfind('\n') {
                            let (line, rest) = buffer.split_at(i + 1);
                            let l = line.to_owned();
                            buffer = rest.to_string();

                            println!("{}", l);

                            if l.contains("[ERR] I2C: Can't find I2C device on provided addresses, scanning for all I2C devices and returning") || l.contains("[FATAL] [BNO080Sensor:0] Can't connect to") {
                            reporter.error("I2C to IMU faulty");
                            break Err(logs);
                        }

                            if l.contains("[INFO ] [BNO080Sensor:0] Connected to") {
                                reporter.success("I2C to IMU working");
                                break Ok(logs);
                            }
                        }
                    };

                    i2c_logs
                };

                match err {
                    Ok(logs) => {
                        board.values.push(TestReportValue::new(
                            false,
                            logs,
                            "!!i2c".to_string(),
                            "true".to_string(),
                        ));
                    }
                    Err(logs) => {
                        reporter.error(&logs);

                        board.values.push(TestReportValue::new(
                            true,
                            logs,
                            "!!i2c".to_string(),
                            "false".to_string(),
                        ));

                        wait_for_next_board(&mut reporter);

                        continue;
                    }
                };
            }

            {
                let mut reports = reports_to_upload_clone.lock().unwrap();
                reports.push(board);
            }

            reporter.success("Board tested successfully");

            wait_for_next_board(&mut reporter);
        }
    });

    spawn(move || loop {
        let board_tests_to_upload = {
            let mut reports_to_upload = boards_to_upload.lock().unwrap();
            let boards_to_upload = reports_to_upload.clone();
            reports_to_upload.clear();

            boards_to_upload
        };

        for board in board_tests_to_upload {
            let body = ApiRequestBody {
                method: "insert_test_report".to_string(),
                params: ApiRequestBodyInsertTestReport(TestReport {
                    id: board.mac.unwrap_or(Uuid::new_v4().to_string()),
                    _type: "main-board".to_string(),
                    values: board.values,
                }),
            };

            let client = reqwest::blocking::Client::new();
            match client
                .post("http://172.30.0.97:3000/api/rpc")
                .header("authorization", env::var("RPC_PASSWORD").unwrap())
                .json(&body)
                .send()
            {
                Ok(s) => {
                    println!("{}", s.text().unwrap());
                }
                Err(e) => {
                    println!("Failed to upload report: {}", e);
                }
            }
        }

        sleep(Duration::from_secs(1));
    });

    renderer.run().unwrap();
}
