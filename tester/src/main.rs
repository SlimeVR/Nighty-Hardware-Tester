use ads1x1x::ChannelSelection;
use rppal::{
    gpio::{Gpio, OutputPin, Result},
    i2c::I2c,
};
use std::{
    thread::{sleep, spawn},
    time::Duration,
};
use tester::{adc, esptool, pio, tui, usb};

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

fn main() {
    let t = tui::TUI::new().unwrap();

    let (mut renderer, mut reporter) = t.split();

    spawn(move || {
        reporter.in_progress("Building firmware...");
        pio::build("esp12e").unwrap();
        reporter.reset();

        let i2c = I2c::with_bus(1).unwrap();
        let gpio = Gpio::new().unwrap();

        let mut rst_pin = gpio.get(6).unwrap().into_output_high();
        let mut flash_pin = gpio.get(22).unwrap().into_output_high();

        let mut adc = adc::Ads1115::new(i2c).unwrap();

        loop {
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

            let (vout, r3v3, bplus, err) = {
                reporter.in_progress("Measuring VOUT...");
                let vout_voltage = adc.measure(ChannelSelection::SingleA2).unwrap();
                let vout_err = vout_voltage < 4.5 || vout_voltage > 5.2;
                if vout_err {
                    reporter.error(&format!("VOUT voltage: {}V (> 4.5V < 5.2V)", vout_voltage));
                } else {
                    reporter.success(&format!("VOUT voltage: {}V", vout_voltage));
                }

                reporter.in_progress("Measuring B+...");
                let bplus_voltage = adc.measure(ChannelSelection::SingleA3).unwrap();
                reporter.success(&format!("B+ voltage: {}V", bplus_voltage));

                reporter.in_progress("Measuring 3V3...");
                let r3v3_voltage = adc.measure(ChannelSelection::SingleA0).unwrap();
                let r3v3_err = r3v3_voltage < 2.8 || r3v3_voltage > 3.2;
                if r3v3_err {
                    reporter.error(&format!("3V3 voltage: {}V (> 2.8V < 3.2V)", r3v3_voltage));
                } else {
                    reporter.success(&format!("3V3 voltage: {}V", r3v3_voltage));
                }

                (vout_err, r3v3_err, bplus_voltage, vout_err || r3v3_err)
            };

            if err {
                reporter.error("-> Faulty power circuit");

                wait_for_next_board(&mut reporter);
                continue;
            }

            let (mac) = {
                reporter.in_progress("Reading MAC address...");
                let mac = match esptool::read_mac_address(
                    &mut flash_pin,
                    &mut rst_pin,
                    &enable_flashing,
                    &reset_esp,
                ) {
                    Ok(mac) => {
                        // TODO: Check if we already have this MAC address in the database

                        reporter.success(&format!("Read MAC address: {}", mac));

                        Ok(mac)
                    }
                    Err(e) => {
                        reporter.error(&format!("Failed to read MAC address: {}", e));

                        Err(e)
                    }
                };

                (mac)
            };

            if mac.is_err() {
                reporter.error("-> ESP8266 faulty");

                wait_for_next_board(&mut reporter);
                continue;
            }

            let flash = {
                reporter.in_progress("Flashing...");
                match esptool::write_flash(
                    "slimevr-tracker-esp/.pio/build/esp12e/firmware.bin",
                    &mut flash_pin,
                    &mut rst_pin,
                    &enable_flashing,
                    &reset_esp,
                ) {
                    Ok(_) => {
                        reporter.success("Flashed");
                        Ok(())
                    }
                    Err(e) => {
                        reporter.error(&format!("Flashing: {}", e));
                        Err(e)
                    }
                }
            };

            if flash.is_err() {
                reporter.error("-> Flashing failed");

                wait_for_next_board(&mut reporter);
                continue;
            }

            let serial = serialport::new("/dev/ttyUSB0", 115200)
                .timeout(Duration::from_millis(10000))
                .data_bits(serialport::DataBits::Seven)
                .open();

            if serial.is_err() {
                reporter.error("Failed to read logs: could not open serial port");
                continue;
            }

            let mut serial = serial.unwrap();

            println!("Streaming logs from serial port...");
            println!("==================================");

            let mut buffer = String::new();
            let mut buf = [0; 128];
            while let Ok(bytes_read) = serial.read(&mut buf) {
                if bytes_read == 0 {
                    break;
                }

                buffer += &String::from_utf8_lossy(&buf[..bytes_read]);

                if let Some(i) = buffer.rfind('\n') {
                    let (line, rest) = buffer.split_at(i + 1);
                    let l = line.to_owned();
                    buffer = rest.to_string();

                    println!("{}", l);

                    if l.contains("[FATAL] [BNO080Sensor:0] Can't connect to") {
                        reporter.error("I2C to IMU faulty");
                        break;
                    }

                    if l.contains("[INFO ] [BNO080Sensor:0] Connected to") {
                        reporter.success("I2C to IMU working");
                        break;
                    }
                }
            }

            wait_for_next_board(&mut reporter);
        }
    });

    renderer.run().unwrap();

    // The rest of the code will only run if you press `q` or `Esc` to exit the TUI.
}
