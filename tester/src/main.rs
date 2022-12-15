use ads1x1x::ChannelSelection;
use rppal::{
    gpio::{Gpio, OutputPin, Result},
    i2c::I2c,
};
use std::{thread, time::Duration};
use tester::{adc, esptool, pio, tui, usb};

const USB_VENDOR_ID: u16 = 0x1a86;
const USB_PRODUCT_ID: u16 = 0x7523;

fn reset_esp(pin: &mut OutputPin) -> Result<()> {
    pin.set_low();

    thread::sleep(Duration::from_millis(100));

    pin.set_high();

    Ok(())
}

fn enable_flashing(flash_pin: &mut OutputPin, rst_pin: &mut OutputPin) -> Result<()> {
    flash_pin.set_low();

    reset_esp(rst_pin)?;

    thread::sleep(Duration::from_millis(100));

    flash_pin.set_high();

    Ok(())
}

fn main() {
    let t = tui::TUI::new().unwrap();

    let (mut renderer, mut reporter) = t.split();

    thread::spawn(move || {
        let i2c = I2c::with_bus(1).unwrap();
        let gpio = Gpio::new().unwrap();

        let mut rst_pin = gpio.get(4).unwrap().into_output_high();
        let mut flash_pin = gpio.get(17).unwrap().into_output_high();

        let mut adc = adc::Ads1115::new(i2c).unwrap();

        loop {
            reporter.action("[ Please connect the device ]".to_string());

            usb::wait_until_device_is_connected(USB_VENDOR_ID, USB_PRODUCT_ID);

            let wait_for_next_board = |reporter: &mut tui::Reporter| {
                reporter.action("[ Please disconnect the device ]".to_string());

                usb::wait_until_device_is_disconnected(USB_VENDOR_ID, USB_PRODUCT_ID);

                reporter.reset();
            };

            reporter.reset();

            let (vout, r3v3, err) = {
                reporter.in_progress("Measuring VOUT...");
                let vout_voltage = adc.measure(ChannelSelection::SingleA3).unwrap();
                let vout_err = vout_voltage < 4.5 || vout_voltage > 5.2;
                if vout_err {
                    reporter.error(format!("╳ VOUT voltage: {}V (> 4.6V < 5.1V)", vout_voltage));
                } else {
                    reporter.success(format!("✓ VOUT voltage: {}V", vout_voltage));
                }

                reporter.in_progress("Measuring 3V3...");
                let r3v3_voltage = adc.measure(ChannelSelection::SingleA2).unwrap();
                let r3v3_err = r3v3_voltage < 2.8 || r3v3_voltage > 3.2;
                if r3v3_err {
                    reporter.error(format!("╳ 3V3 voltage: {}V (> 2.8V < 3.2V)", r3v3_voltage));
                } else {
                    reporter.success(format!("✓ 3V3 voltage: {}V", r3v3_voltage));
                }

                (vout_err, r3v3_err, vout_err || r3v3_err)
            };

            if err {
                reporter.error("-> Faulty power circuit".to_string());

                wait_for_next_board(&mut reporter);
                continue;
            }

            let (mac, chip_id) = {
                reporter.in_progress("Reading MAC address...");
                let mac = match esptool::read_mac_address(
                    &mut flash_pin,
                    &mut rst_pin,
                    &enable_flashing,
                    &reset_esp,
                ) {
                    Ok(mac) => {
                        // TODO: Check if we already have this MAC address in the database

                        reporter.success(format!("✓ MAC: {}", mac));

                        Ok(mac)
                    }
                    Err(e) => {
                        reporter.error(format!("╳ MAC address: {}", e));

                        Err(e)
                    }
                };

                reporter.in_progress("Reading chip ID...");
                let chip_id = match esptool::read_chip_id(
                    &mut flash_pin,
                    &mut rst_pin,
                    &enable_flashing,
                    &reset_esp,
                ) {
                    Ok(chip_id) => {
                        reporter.success(format!("✓ Chip ID: {}", chip_id));
                        Ok(chip_id)
                    }
                    Err(e) => {
                        reporter.error(format!("╳ Chip ID: {}", e));
                        Err(e)
                    }
                };

                (mac, chip_id)
            };

            if mac.is_err() || chip_id.is_err() {
                reporter.error("-> ESP8266 faulty".to_string());

                wait_for_next_board(&mut reporter);
                continue;
            }

            let board_id = format!("{}-{}", mac.unwrap(), chip_id.unwrap());

            let flash = {
                reporter.in_progress("Flashing...");
                match pio::flash(
                    "esp12e",
                    &mut flash_pin,
                    &mut rst_pin,
                    &enable_flashing,
                    &reset_esp,
                ) {
                    Ok(_) => {
                        reporter.success("✓ Flashed".to_string());
                        Ok(())
                    }
                    Err(e) => {
                        reporter.error(format!("╳ Flashing: {}", e));
                        Err(e)
                    }
                }
            };

            if flash.is_err() {
                reporter.error("-> Flashing failed".to_string());

                wait_for_next_board(&mut reporter);
                continue;
            }

            let serial = serialport::new("/dev/ttyUSB0", 115200)
                .timeout(Duration::from_millis(10000))
                .data_bits(serialport::DataBits::Seven)
                .open();

            if serial.is_err() {
                reporter.error("╳ Serial port: Could not open serial port".to_string());
                continue;
            }

            let mut serial = serial.unwrap();

            reporter.action("Streaming logs from serial port...");
            reporter.action("==================================");

            let mut buf = [0; 128];
            while let Ok(bytes_read) = serial.read(&mut buf) {
                print!("{}", String::from_utf8_lossy(&buf[..bytes_read]));
            }

            wait_for_next_board(&mut reporter);
        }
    });

    renderer.run().unwrap();

    // The rest of the code will only run if you press `q` or `Esc` to exit the TUI.
}
