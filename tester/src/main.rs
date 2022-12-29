use ads1x1x::ChannelSelection;
use rppal::{gpio, i2c};
use serde::{Deserialize, Serialize};
use std::{
    env,
    fs::{read_to_string, File},
    io::Write,
    sync::{Arc, Mutex},
    thread::{sleep, spawn},
    time::Duration,
};
use tester::{adc, api, esp, esptool, logger, pio, serial, usb};

const USB_VENDOR_ID: u16 = 0x1a86;
const USB_PRODUCT_ID: u16 = 0x7523;

#[derive(Debug, Clone, Serialize, Deserialize)]
struct Board {
    mac: Option<String>,
    values: Vec<api::TestReportValue>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct BoardUploadFailure {
    board: Board,
    error: String,
}

fn main() {
    let flash_with_pio = env::var("TESTER_FLASH_WITH_PIO").unwrap_or("0".to_string()) == "1";
    let rpc_url = env::var("TESTER_RPC_URL").unwrap();
    let rpc_password = env::var("TESTER_RPC_PASSWORD").unwrap();

    let (mut renderer, mut logger) = logger::LoggerBuilder::split();

    let boards_to_upload: Arc<Mutex<Vec<Board>>> = Arc::new(Mutex::new(Vec::new()));

    let reports_to_upload_clone = boards_to_upload.clone();
    spawn(move || {
        let reports_to_upload = reports_to_upload_clone;

        let wait_for_next_board = |logger: &mut logger::Logger, board: Board| {
            {
                let mut reports = reports_to_upload.lock().unwrap();
                reports.push(board);
            }

            logger.action("[ Please disconnect the device ]".to_string());

            usb::wait_until_device_is_disconnected(USB_VENDOR_ID, USB_PRODUCT_ID);

            logger.reset();
        };

        if flash_with_pio {
            logger.in_progress("Skipping firmware build, flashing with PlatformIO...");
            sleep(Duration::from_millis(500));
            logger.reset();
        } else {
            if env::var("TESTER_NO_BUILD").unwrap_or("0".to_string()) == "1" {
                logger.in_progress("Skipping firmware build, building disabled via env...");
                sleep(Duration::from_millis(500));
                logger.reset();
            } else {
                logger.in_progress("Building firmware...");
                pio::build("esp12e").unwrap();
                logger.reset();
            }
        }

        let i2c = i2c::I2c::with_bus(1).unwrap();
        let gpio = gpio::Gpio::new().unwrap();

        let mut esp = {
            let rst_pin = gpio.get(6).unwrap().into_output_high();
            let flash_pin = gpio.get(22).unwrap().into_output_high();

            esp::ESP::new(rst_pin, flash_pin)
        };

        let mut adc = adc::Ads1115::new(i2c).unwrap();

        loop {
            let mut board = Board {
                mac: None,
                values: Vec::new(),
            };

            logger.action("[ Please connect the device ]".to_string());

            usb::wait_until_device_is_connected(USB_VENDOR_ID, USB_PRODUCT_ID);

            sleep(Duration::from_millis(250));

            logger.reset();

            logger.success("Device connected");

            let err = {
                match adc.measure(ChannelSelection::SingleA2) {
                    Ok(v) => {
                        board.values.push(api::TestReportValue::new(
                            "Measure VOUT",
                            "none",
                            v.to_string() + "V",
                            None::<&str>,
                            false,
                        ));
                    }
                    Err(e) => match e {
                        nb::Error::WouldBlock => {
                            board.values.push(api::TestReportValue::new(
                                "Measure VOUT",
                                "none",
                                "N/A",
                                Some("err: would block"),
                                false,
                            ));
                        }
                        nb::Error::Other(e) => match e {
                            ads1x1x::Error::I2C(e) => {
                                board.values.push(api::TestReportValue::new(
                                    "Measure VOUT",
                                    "none",
                                    "N/A",
                                    Some(&format!("err: i2c: {}", e)),
                                    false,
                                ));
                            }
                            ads1x1x::Error::InvalidInputData => {
                                board.values.push(api::TestReportValue::new(
                                    "Measure VOUT",
                                    "none",
                                    "N/A",
                                    Some("err: invalid input data"),
                                    false,
                                ));
                            }
                        },
                    },
                };

                logger.in_progress("Measuring B+...");
                let bplus_err = match adc.measure(ChannelSelection::SingleA3) {
                    Ok(v) => {
                        let bplus_err = v < 4.0;
                        if bplus_err {
                            logger.error(&format!("B+ voltage: {}V (> 4.0V)", v));
                        } else {
                            logger.success(&format!("B+ voltage: {}V", v));
                        }

                        board.values.push(api::TestReportValue::new(
                            "Measure B+",
                            "B+ > 4.0V",
                            v.to_string() + "V",
                            None::<&str>,
                            bplus_err,
                        ));

                        bplus_err
                    }
                    Err(e) => match e {
                        nb::Error::WouldBlock => {
                            board.values.push(api::TestReportValue::new(
                                "Measure B+",
                                "B+ > 4.0V",
                                "N/A",
                                Some("err: would block"),
                                true,
                            ));

                            true
                        }
                        nb::Error::Other(e) => match e {
                            ads1x1x::Error::I2C(e) => {
                                board.values.push(api::TestReportValue::new(
                                    "Measure B+",
                                    "B+ > 4.0V",
                                    "N/A",
                                    Some(&format!("err: i2c: {}", e)),
                                    true,
                                ));

                                true
                            }
                            ads1x1x::Error::InvalidInputData => {
                                board.values.push(api::TestReportValue::new(
                                    "Measure B+",
                                    "B+ > 4.0V",
                                    "N/A",
                                    Some("err: invalid input data"),
                                    true,
                                ));

                                true
                            }
                        },
                    },
                };

                logger.in_progress("Measuring 3V3...");
                let r3v3_err = match adc.measure(ChannelSelection::SingleA0) {
                    Ok(v) => {
                        let r3v3_err = v < 2.8 || v > 3.2;
                        if r3v3_err {
                            logger.error(&format!("3V3 voltage: {}V (> 2.8V < 3.2V)", v));
                        } else {
                            logger.success(&format!("3V3 voltage: {}V", v));
                        }

                        board.values.push(api::TestReportValue::new(
                            "Measure 3V3",
                            "2.8V > 3V3 < 3.2V",
                            v.to_string() + "V",
                            None::<&str>,
                            r3v3_err,
                        ));

                        r3v3_err
                    }
                    Err(e) => match e {
                        nb::Error::WouldBlock => {
                            board.values.push(api::TestReportValue::new(
                                "Measure 3V3",
                                "2.8V > 3V3 < 3.2V",
                                "N/A",
                                Some("err: would block"),
                                true,
                            ));

                            true
                        }
                        nb::Error::Other(e) => match e {
                            ads1x1x::Error::I2C(e) => {
                                board.values.push(api::TestReportValue::new(
                                    "Measure 3V3",
                                    "2.8V > 3V3 < 3.2V",
                                    "N/A",
                                    Some(&format!("err: i2c: {}", e)),
                                    true,
                                ));

                                true
                            }
                            ads1x1x::Error::InvalidInputData => {
                                board.values.push(api::TestReportValue::new(
                                    "Measure 3V3",
                                    "2.8V > 3V3 < 3.2V",
                                    "N/A",
                                    Some("err: invalid input data"),
                                    true,
                                ));

                                true
                            }
                        },
                    },
                };

                bplus_err || r3v3_err
            };

            if err {
                logger.error("-> Faulty power circuit");

                logger.fill(logger::Color::Red);
                sleep(Duration::from_secs(1));

                wait_for_next_board(&mut logger, board);
                continue;
            }

            {
                logger.in_progress("Reading MAC address...");
                match esptool::read_mac_address(&mut esp) {
                    Ok(esptool::ReadMacAddressResult { mac, log }) => {
                        board.mac = Some(mac.clone());
                        board.values.push(api::TestReportValue::new(
                            "Read MAC address",
                            "MAC address should be readable",
                            mac.clone(),
                            Some(log),
                            false,
                        ));

                        logger.success(&format!("Read MAC address: {}", mac));
                    }
                    Err(e) => {
                        board.values.push(api::TestReportValue::new(
                            "Read MAC address",
                            "MAC address should be readable",
                            "N/A",
                            Some(e.to_string()),
                            true,
                        ));

                        logger.error(&format!("Failed to read MAC address: {}", e));
                        logger.error("-> ESP8266 faulty");

                        logger.fill(logger::Color::Red);
                        sleep(Duration::from_secs(1));

                        wait_for_next_board(&mut logger, board);

                        continue;
                    }
                }
            };

            {
                logger.in_progress("Flashing...");
                match if flash_with_pio {
                    pio::flash("esp12e", &mut esp)
                } else {
                    esptool::write_flash(
                        "slimevr-tracker-esp/.pio/build/esp12e/firmware.bin",
                        &mut esp,
                    )
                } {
                    Ok(logs) => {
                        board.values.push(api::TestReportValue::new(
                            "Flashing",
                            "Flashing should work",
                            true,
                            Some(logs),
                            false,
                        ));

                        logger.success("Flashed");
                    }
                    Err(e) => {
                        board.values.push(api::TestReportValue::new(
                            "Flashing",
                            "Flashing should work",
                            false,
                            Some(e.to_string()),
                            true,
                        ));

                        logger.error(&format!("Flashing: {}", e));
                        logger.error("-> Flashing failed");

                        logger.fill(logger::Color::Red);
                        sleep(Duration::from_secs(1));

                        wait_for_next_board(&mut logger, board);

                        continue;
                    }
                }
            };

            {
                logger.in_progress("Connecting to serial port...");

                let serial = serialport::new("/dev/ttyUSB0", 115200)
                    .timeout(Duration::from_millis(10000))
                    .data_bits(serialport::DataBits::Eight)
                    .open();

                let mut serial = match serial {
                    Ok(serial) => {
                        board.values.push(api::TestReportValue::new(
                            "Serial",
                            "Serial should work",
                            true,
                            None::<&str>,
                            false,
                        ));

                        logger.success("Serial port opened");

                        serial
                    }
                    Err(error) => {
                        logger.error(format!("Failed to open serial port: {}", error).as_str());

                        board.values.push(api::TestReportValue::new(
                            "Serial",
                            "Serial should work",
                            false,
                            Some(error),
                            true,
                        ));

                        logger.fill(logger::Color::Red);
                        sleep(Duration::from_secs(1));

                        wait_for_next_board(&mut logger, board);

                        continue;
                    }
                };

                if let Err(e) = serial.clear(serialport::ClearBuffer::All) {
                    println!("(warn) failed to clear serial port: {}", e);
                }

                // Read serial logs for checking the logs for sensor errors
                {
                    logger.in_progress("Checking I2C connection to IMU...");

                    esp.reset_no_delay().unwrap();

                    let mut full_logs = String::new();

                    let err = {
                        let mut buffer = String::new();

                        println!("Streaming logs from serial port...");
                        println!("==================================");

                        loop {
                            let read = serial::read_string(&mut serial);
                            if let Err(e) = read {
                                logger.error(&format!("Failed to read logs: {}", e));
                                break Err(format!("Failed to read logs: {}\n{}", e, full_logs));
                            }

                            let str = read.unwrap();

                            full_logs += &str;
                            buffer += &str;

                            if let Some(i) = buffer.rfind('\n') {
                                let (line, rest) = buffer.split_at(i + 1);
                                let l = line.to_owned();
                                buffer = rest.to_string();

                                println!("{}", l);

                                if l.contains("[ERR] I2C: Can't find I2C device on provided addresses, scanning for all I2C devices and returning") ||
                                l.contains("[FATAL] [BNO080Sensor:0] Can't connect to"
                            ) {
                                logger.error("I2C to IMU faulty");
                                break Err(full_logs);
                            }

                                if l.contains("[INFO ] [BNO080Sensor:0] Connected to") {
                                    logger.success("I2C to IMU working");
                                    break Ok(full_logs);
                                }
                            }
                        }
                    };

                    match err {
                        Ok(logs) => {
                            board.values.push(api::TestReportValue::new(
                                "I2C to IMU",
                                "I2C to IMU should work",
                                true,
                                Some(logs),
                                false,
                            ));
                        }
                        Err(logs) => {
                            logger.error(&logs);

                            board.values.push(api::TestReportValue::new(
                                "I2C to IMU",
                                "I2C to IMU should work",
                                false,
                                Some(logs),
                                true,
                            ));

                            logger.fill(logger::Color::Red);
                            sleep(Duration::from_secs(1));

                            wait_for_next_board(&mut logger, board);

                            continue;
                        }
                    };
                };

                sleep(Duration::from_millis(100));

                {
                    logger.in_progress("Checking IMU via `GET TEST` command...");

                    let mut full_logs = String::new();

                    if let Err(e) = serial::write(&mut serial, b"GET TEST\n") {
                        logger.error(&format!("Failed to write to serial port: {}", e));

                        board.values.push(api::TestReportValue::new(
                            "IMU test",
                            "IMU test should work",
                            false,
                            Some(format!("Failed to write to serial port: {}", e)),
                            true,
                        ));

                        logger.fill(logger::Color::Red);
                        sleep(Duration::from_secs(1));

                        wait_for_next_board(&mut logger, board);

                        continue;
                    }

                    let err = {
                        let mut buffer = String::new();

                        println!("Streaming logs from serial port...");
                        println!("==================================");

                        loop {
                            let read = serial::read_string(&mut serial);
                            if let Err(e) = read {
                                logger.error(&format!("Failed to read logs: {}", e));
                                break Err(format!("Failed to read logs: {}\n{}", e, full_logs));
                            }

                            let str = read.unwrap();

                            full_logs += &str;
                            buffer += &str;

                            if let Some(i) = buffer.rfind('\n') {
                                let (line, rest) = buffer.split_at(i + 1);
                                let l = line.to_owned();
                                buffer = rest.to_string();

                                println!("{}", l);

                                if l.contains("Sensor 1 didn't send any data yet!") {
                                    logger.error("IMU faulty");
                                    break Err(full_logs);
                                }

                                if l.contains("Sensor 1 sent some data, looks working.") {
                                    logger.error("IMU working");
                                    break Ok(full_logs);
                                }
                            }
                        }
                    };

                    match err {
                        Ok(logs) => {
                            board.values.push(api::TestReportValue::new(
                                "IMU test",
                                "IMU test should work",
                                true,
                                Some(logs),
                                false,
                            ));
                        }
                        Err(logs) => {
                            logger.error(&logs);

                            board.values.push(api::TestReportValue::new(
                                "IMU test",
                                "IMU test should work",
                                false,
                                Some(logs),
                                true,
                            ));

                            logger.fill(logger::Color::Red);
                            sleep(Duration::from_secs(1));

                            wait_for_next_board(&mut logger, board);

                            continue;
                        }
                    };
                };
            }

            logger.success("Board tested successfully");

            logger.fill(logger::Color::Green);
            sleep(Duration::from_secs(1));

            wait_for_next_board(&mut logger, board);
        }
    });

    spawn(move || {
        let client = api::Client::new(rpc_url, rpc_password);

        let mut upload_failed_boards = {
            let failed_boards = read_to_string("failed_boards.json").unwrap_or("[]".to_string());

            serde_json::from_str::<Vec<BoardUploadFailure>>(&failed_boards)
                .unwrap_or_else(|_| Vec::new())
        };

        loop {
            let board_tests_to_upload = {
                let mut reports_to_upload = boards_to_upload.lock().unwrap();
                let boards_to_upload = reports_to_upload.clone();
                reports_to_upload.clear();

                boards_to_upload
            };

            for board in board_tests_to_upload {
                if let None = board.mac {
                    println!("Board has no MAC address, skipping upload");
                    continue;
                }

                match client.send_test_report(
                    "main-board",
                    board.mac.clone().unwrap(),
                    &board.values,
                ) {
                    Ok(s) => {
                        if s.status() != reqwest::StatusCode::OK {
                            let e = s.text().unwrap();

                            println!("Failed to upload report: {}", e);

                            upload_failed_boards.push(BoardUploadFailure { board, error: e });

                            continue;
                        }

                        let e = s.text().unwrap();

                        println!("{}", e);
                    }
                    Err(e) => {
                        println!("Failed to upload report: {}", e);

                        upload_failed_boards.push(BoardUploadFailure {
                            board,
                            error: e.to_string(),
                        });
                    }
                }
            }

            {
                let mut failed_boards_file = File::create("failed_boards.json").unwrap();
                failed_boards_file
                    .write_all(
                        serde_json::to_string_pretty(&upload_failed_boards)
                            .unwrap()
                            .as_bytes(),
                    )
                    .unwrap();
            }

            sleep(Duration::from_secs(1));
        }
    });

    renderer.run().unwrap();
}
