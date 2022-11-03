use ads1x1x::ChannelSelection;
use log;
use rppal::{
    gpio::{Gpio, OutputPin, Result},
    i2c::I2c,
};
use std::{process, thread, time::Duration};
use tester::{adc, esp, tui, usb};

const USB_VENDOR_ID: u16 = 0x1a86;
const USB_PRODUCT_ID: u16 = 0x7523;

fn reset_esp(pin: &mut OutputPin) -> Result<()> {
    let s = tracing::span!(tracing::Level::DEBUG, "reset_esp");
    let _enter = s.enter();

    log::debug!("Resetting ESP");

    pin.set_low();

    thread::sleep(Duration::from_millis(100));

    pin.set_high();

    log::info!("Reset ESP");

    Ok(())
}

fn enable_flashing(flash_pin: &mut OutputPin, rst_pin: &mut OutputPin) -> Result<()> {
    let s = tracing::span!(tracing::Level::DEBUG, "enable_flashing");
    let _enter = s.enter();

    log::debug!("Enabling flashing");

    flash_pin.set_low();

    reset_esp(rst_pin)?;

    thread::sleep(Duration::from_millis(100));

    flash_pin.set_high();

    log::info!("Flashing mode enabled");

    Ok(())
}

fn flash_esp(flash_pin: &mut OutputPin, rst_pin: &mut OutputPin) -> Result<()> {
    let s = tracing::span!(tracing::Level::INFO, "flash_esp");
    let _enter = s.enter();

    enable_flashing(flash_pin, rst_pin)?;

    log::debug!("Running pio run -t upload");
    log::trace!("=========================");
    let mut s = process::Command::new("pio")
        .arg("run")
        .arg("-t")
        .arg("upload")
        .arg("--upload-port")
        .arg("/dev/ttyUSB0")
        .current_dir("/home/pi/slimevr-tracker-esp")
        .stderr(process::Stdio::inherit())
        .stdout(process::Stdio::inherit())
        .spawn()?;

    s.wait()?;
    log::trace!("=========================");

    log::info!("Flashing complete");

    thread::sleep(Duration::from_millis(100));

    reset_esp(rst_pin)?;

    Ok(())
}

fn main() {
    let t = tui::TUI::new().unwrap();

    let (mut renderer, mut input_reader, mut ticker) = t.split();

    thread::spawn(move || {
        input_reader.run().unwrap();
    });

    thread::spawn(move || {
        ticker.run().unwrap();
    });

    // TODO: Implement test runner inside of thread
    // TODO: Implement logging inside of TUI

    renderer.run().unwrap();

    // The rest of the code will only run if you press `q` or `Esc` to exit the TUI.

    tracing_subscriber::fmt::Subscriber::builder()
        .with_env_filter(tracing_subscriber::EnvFilter::from_default_env())
        .without_time()
        .with_max_level(tracing::Level::TRACE)
        .init();

    log::info!("Starting");

    {
        let s = tracing::span!(tracing::Level::INFO, "test");
        let _enter = s.enter();

        let i2c = I2c::with_bus(1).unwrap();
        let gpio = Gpio::new().unwrap();

        let mut rst_pin = gpio.get(4).unwrap().into_output_high();
        let mut flash_pin = gpio.get(17).unwrap().into_output_high();

        let mut adc = adc::Ads1115::new(i2c).unwrap();

        log::info!("Waiting for USB device to be plugged in...");
        while !usb::find_device(USB_VENDOR_ID, USB_PRODUCT_ID) {
            thread::sleep(Duration::from_secs(1));
        }
        log::info!("USB device plugged in");

        {
            let s = tracing::span!(tracing::Level::INFO, "measure_voltages");
            let _enter = s.enter();

            let vout_voltage = adc.measure(ChannelSelection::SingleA3).unwrap();
            let r3v3_voltage = adc.measure(ChannelSelection::SingleA2).unwrap();

            log::info!("VOUT: {}V", vout_voltage);
            log::info!("3V3: {}V", r3v3_voltage);
        }

        {
            let s = tracing::span!(tracing::Level::INFO, "read_esp_info");
            let _enter = s.enter();

            log::debug!("Reading ESP info...");
            let mac =
                esp::read_mac_address(&mut flash_pin, &mut rst_pin, &enable_flashing, &reset_esp)
                    .unwrap();
            let chip_id =
                esp::read_chip_id(&mut flash_pin, &mut rst_pin, &enable_flashing, &reset_esp)
                    .unwrap();
            let board_id = format!("{}-{}", mac, chip_id);

            log::info!("MAC: {}", mac);
            log::info!("Chip ID: {}", chip_id);
            log::info!("Board ID: {}", board_id);
        }

        {
            let s = tracing::span!(tracing::Level::INFO, "flash_esp");
            let _enter = s.enter();

            log::debug!("Flashing ESP...");
            flash_esp(&mut flash_pin, &mut rst_pin).unwrap();
            log::info!("ESP flashed");
        }

        {
            log::debug!("Connecting to serial port...");
            let mut serial = serialport::new("/dev/ttyUSB0", 115200)
                .timeout(Duration::from_millis(10000))
                .data_bits(serialport::DataBits::Seven)
                .open()
                .unwrap();

            log::info!("Streaming logs from serial port...");
            log::trace!("==================================");

            let mut buf = [0; 128];
            loop {
                let bytes_read = serial.read(&mut buf).unwrap();
                print!("{}", String::from_utf8_lossy(&buf[..bytes_read]));
            }
        }
    }
}
