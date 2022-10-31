use std::{process, thread, time::Duration};

use tester::{adc, esp, log, usb};

use ads1x1x::ChannelSelection;

use rppal::{
    gpio::{Gpio, OutputPin, Result},
    i2c::I2c,
};

const USB_VENDOR_ID: u16 = 0x1a86;
const USB_PRODUCT_ID: u16 = 0x7523;
fn reset_esp(l: &mut log::LogCtx, pin: &mut OutputPin) -> Result<()> {
    l.enter("reset_esp");

    l.dbg("Resetting ESP");

    pin.set_low();

    thread::sleep(Duration::from_millis(100));

    pin.set_high();

    l.inf("Reset ESP");

    l.leave("reset_esp");

    Ok(())
}

fn enable_flashing(
    l: &mut log::LogCtx,
    flash_pin: &mut OutputPin,
    rst_pin: &mut OutputPin,
) -> Result<()> {
    l.enter("enable_flashing");

    l.dbg("Enabling flashing");

    flash_pin.set_low();

    reset_esp(l, rst_pin)?;

    thread::sleep(Duration::from_millis(100));

    flash_pin.set_high();

    l.inf("Flashing mode enabled");

    l.leave("enable_flashing");

    Ok(())
}

fn flash_esp(
    l: &mut log::LogCtx,
    flash_pin: &mut OutputPin,
    rst_pin: &mut OutputPin,
) -> Result<()> {
    l.enter("flash_esp");

    enable_flashing(l, flash_pin, rst_pin)?;

    l.dbg("Running pio run -t upload");
    l.trc("=========================");
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
    l.trc("=========================");

    l.inf("Flashing complete");

    thread::sleep(Duration::from_millis(100));

    reset_esp(l, rst_pin)?;

    l.leave("flash_esp");

    Ok(())
}

fn main() {
    let mut l = log::LogCtx::new();

    l.inf("Starting");

    let i2c = I2c::with_bus(1).unwrap();
    let gpio = Gpio::new().unwrap();

    let mut rst_pin = gpio.get(4).unwrap().into_output_high();
    let mut flash_pin = gpio.get(17).unwrap().into_output_high();

    let mut adc = adc::Ads1115::new(i2c).unwrap();

    {
        l.inf("Waiting for USB device to be plugged in...");
        while !usb::find_device(&mut l, USB_VENDOR_ID, USB_PRODUCT_ID) {
            thread::sleep(Duration::from_secs(1));
        }
        l.inf("USB device plugged in");
    }

    {
        l.enter("measure_voltages");

        let vout_voltage = adc.measure(&mut l, ChannelSelection::SingleA3).unwrap();
        let r3v3_voltage = adc.measure(&mut l, ChannelSelection::SingleA2).unwrap();

        l.inf(format!("VOUT: {}V", vout_voltage).as_str());
        l.inf(format!("3V3: {}V", r3v3_voltage).as_str());

        l.leave("measure_voltages");
    }

    {
        l.enter("read_esp_info");
        l.dbg("Reading ESP info...");
        let mac =
            esp::read_mac_address(&mut l, &mut flash_pin, &mut rst_pin, &enable_flashing).unwrap();
        let chip_id =
            esp::read_chip_id(&mut l, &mut flash_pin, &mut rst_pin, &enable_flashing).unwrap();
        let board_id = format!("{}-{}", mac, chip_id);

        l.inf(format!("MAC: {}", mac).as_str());
        l.inf(format!("Chip ID: {}", chip_id).as_str());
        l.inf(format!("Board ID: {}", board_id).as_str());
        l.leave("read_esp_info");
    }

    // {
    //     l.enter("flash_esp");
    //     l.dbg("Flashing ESP...");
    //     flash_esp(&mut l, &mut flash_pin, &mut rst_pin).unwrap();
    //     l.inf("ESP flashed");
    //     l.leave("flash_esp");
    // }

    {
        l.dbg("Connecting to serial port...");
        let mut serial = serialport::new("/dev/ttyUSB0", 115200)
            .timeout(Duration::from_millis(10000))
            .data_bits(serialport::DataBits::Seven)
            .open()
            .unwrap();

        l.inf("Streaming logs from serial port...");
        l.trc("==================================");

        let mut buf = [0; 128];
        loop {
            let bytes_read = serial.read(&mut buf).unwrap();
            print!("{}", String::from_utf8_lossy(&buf[..bytes_read]));
        }
    }
}
