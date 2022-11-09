use ads1x1x::ChannelSelection;
use rppal::{
    gpio::{Gpio, OutputPin, Result},
    i2c::I2c,
};
use std::{thread, time::Duration};
use tester::{adc, esptool, tui, usb};

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
            reporter.warn("Connect device".to_string());

            usb::wait_until_device_is_connected(USB_VENDOR_ID, USB_PRODUCT_ID);

            {
                let vout_voltage = adc.measure(ChannelSelection::SingleA3).unwrap();
                reporter.success(format!("VOUT voltage: {}V", vout_voltage));

                let r3v3_voltage = adc.measure(ChannelSelection::SingleA2).unwrap();
                reporter.success(format!("3V3 voltage: {}V", r3v3_voltage));
            }

            {
                let mac = esptool::read_mac_address(
                    &mut flash_pin,
                    &mut rst_pin,
                    &enable_flashing,
                    &reset_esp,
                )
                .unwrap();
                reporter.success(format!("MAC: {}", mac));

                let chip_id = esptool::read_chip_id(
                    &mut flash_pin,
                    &mut rst_pin,
                    &enable_flashing,
                    &reset_esp,
                )
                .unwrap();
                reporter.success(format!("Chip ID: {}", chip_id));

                // let board_id = format!("{}-{}", mac, chip_id);
            }

            // {
            //     let s = tracing::span!(tracing::Level::INFO, "flash_esp");
            //     let _enter = s.enter();

            //     log::debug!("Flashing ESP...");
            //     flash_esp(&mut flash_pin, &mut rst_pin).unwrap();
            //     log::info!("ESP flashed");
            // }

            // {
            //     log::debug!("Connecting to serial port...");
            //     let mut serial = serialport::new("/dev/ttyUSB0", 115200)
            //         .timeout(Duration::from_millis(10000))
            //         .data_bits(serialport::DataBits::Seven)
            //         .open()
            //         .unwrap();

            //     log::info!("Streaming logs from serial port...");
            //     log::trace!("==================================");

            //     let mut buf = [0; 128];
            //     loop {
            //         let bytes_read = serial.read(&mut buf).unwrap();
            //         print!("{}", String::from_utf8_lossy(&buf[..bytes_read]));
            //     }
            // }

            reporter.warn("Disconnect device".to_string());

            usb::wait_until_device_is_disconnected(USB_VENDOR_ID, USB_PRODUCT_ID);

            reporter.reset();
        }
    });

    renderer.run().unwrap();

    // The rest of the code will only run if you press `q` or `Esc` to exit the TUI.
}
