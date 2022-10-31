use std::{process, thread, time::Duration};

use ads1x1x::{
    ic::{Ads1115, Resolution16Bit},
    interface::I2cInterface,
    mode::OneShot,
    ChannelSelection, DynamicOneShot, FullScaleRange,
};
use nb::block;
use rppal::{
    gpio::{Gpio, OutputPin, Result},
    i2c::{self, I2c},
};

const LSB_SIZE: f32 = 187.5;
const USB_VENDOR_ID: u16 = 0x1a86;
const USB_PRODUCT_ID: u16 = 0x7523;

struct LogCtx {
    indent: usize,
}

impl LogCtx {
    fn log(&self, level: &str, msg: &str) {
        println!("{} > {}{}", level, " ".repeat(self.indent), msg);
    }

    fn enter(&mut self, msg: &str) {
        self.log(">>>", msg);
        self.indent += 2;
    }

    fn leave(&mut self, msg: &str) {
        self.indent -= 2;
        self.log("<<<", msg);
    }

    fn inf(&self, msg: &str) {
        self.log("INF", msg);
    }

    fn wrn(&self, msg: &str) {
        self.log("WRN", msg);
    }

    pub fn err(&self, msg: &str) {
        self.log("ERR", msg);
    }

    pub fn ftl(&self, msg: &str) {
        self.log("FTL", msg);
    }

    pub fn dbg(&self, msg: &str) {
        self.log("DBG", msg);
    }

    pub fn trc(&self, msg: &str) {
        self.log("TRC", msg);
    }
}

fn reset_esp(l: &mut LogCtx, pin: &mut OutputPin) -> Result<()> {
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
    l: &mut LogCtx,
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

fn flash_esp(l: &mut LogCtx, flash_pin: &mut OutputPin, rst_pin: &mut OutputPin) -> Result<()> {
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

fn measure_voltage(
    l: &mut LogCtx,
    channel: ChannelSelection,
    adc: &mut ads1x1x::Ads1x1x<I2cInterface<I2c>, Ads1115, Resolution16Bit, OneShot>,
) -> nb::Result<f32, ads1x1x::Error<i2c::Error>> {
    l.enter("read_voltage");

    l.dbg(format!("Reading voltage from channel {:?}", channel).as_str());

    let value = block!(adc.read(channel))?;

    let voltage = value as f32 * LSB_SIZE / 1000000.0;

    l.inf(format!("Voltage: {}V", voltage).as_str());

    l.leave("read_voltage");

    Ok(voltage)
}

fn find_usb_device(l: &mut LogCtx) -> bool {
    l.enter("find_usb_device");

    for device in rusb::devices().unwrap().iter() {
        let device_desc = device.device_descriptor().unwrap();

        if (device_desc.vendor_id() == USB_VENDOR_ID)
            && (device_desc.product_id() == USB_PRODUCT_ID)
        {
            l.inf("Found USB device");

            l.leave("find_usb_device");

            return true;
        }
    }

    l.err("USB device not found");

    l.leave("find_usb_device");

    false
}

fn read_chip_id(
    l: &mut LogCtx,
    flash_pin: &mut OutputPin,
    rst_pin: &mut OutputPin,
) -> Result<String> {
    l.enter("read_chip_id");

    l.dbg("Reading chip ID");

    enable_flashing(l, flash_pin, rst_pin)?;

    l.dbg("Running esptool chip_id");
    l.trc("=======================");
    let c = process::Command::new("esptool")
        .arg("--port")
        .arg("/dev/ttyUSB0")
        .arg("chip_id")
        .stderr(process::Stdio::inherit())
        .stdout(process::Stdio::inherit())
        .output()?;
    l.trc("=======================");

    let output = String::from_utf8_lossy(&c.stdout);

    let chip_id = output
        .lines()
        .filter(|l| l.contains("Chip ID:"))
        .nth(0)
        .unwrap()
        .split("Chip ID: ")
        .nth(1)
        .unwrap();

    l.inf(format!("Chip ID: {}", chip_id).as_str());

    l.leave("read_chip_id");

    Ok(chip_id.to_string())
}

fn read_mac_address(
    l: &mut LogCtx,
    flash_pin: &mut OutputPin,
    rst_pin: &mut OutputPin,
) -> Result<String> {
    l.enter("read_mac_address");

    l.dbg("Reading MAC address");

    enable_flashing(l, flash_pin, rst_pin)?;

    l.dbg("Running esptool read_mac");
    l.trc("========================");
    let c = process::Command::new("esptool")
        .arg("--port")
        .arg("/dev/ttyUSB0")
        .arg("read_mac")
        .stderr(process::Stdio::inherit())
        .stdout(process::Stdio::inherit())
        .output()?;
    l.trc("========================");

    let output = String::from_utf8_lossy(&c.stdout);

    let mac_address = output
        .lines()
        .filter(|l| l.contains("MAC:"))
        .nth(0)
        .unwrap()
        .split("MAC: ")
        .nth(1)
        .unwrap();

    l.inf(format!("MAC address: {}", mac_address).as_str());

    Ok(mac_address.to_string())
}

fn main() {
    let mut l = LogCtx { indent: 0 };

    l.inf("Starting");

    let i2c = I2c::with_bus(1).unwrap();
    let gpio = Gpio::new().unwrap();

    let mut rst_pin = gpio.get(4).unwrap().into_output_high();
    let mut flash_pin = gpio.get(17).unwrap().into_output_high();

    let mut adc = ads1x1x::Ads1x1x::new_ads1115(i2c, ads1x1x::SlaveAddr::default());
    adc.set_full_scale_range(FullScaleRange::Within6_144V)
        .unwrap();
    adc.disable_comparator().unwrap();
    adc.set_data_rate(ads1x1x::DataRate16Bit::Sps860).unwrap();

    {
        l.inf("Waiting for USB device to be plugged in...");
        while !find_usb_device(&mut l) {
            thread::sleep(Duration::from_secs(1));
        }
        l.inf("USB device plugged in");
    }

    {
        l.enter("measure_voltages");
        l.dbg("Measuring voltage...");
        let r3v3_voltage = measure_voltage(&mut l, ChannelSelection::SingleA2, &mut adc).unwrap();
        let vcc_voltage = measure_voltage(&mut l, ChannelSelection::SingleA3, &mut adc).unwrap();

        l.inf(format!("3V3: {}V", r3v3_voltage).as_str());
        l.inf(format!("VCC: {}V", vcc_voltage).as_str());
        l.leave("measure_voltages");
    }

    {
        l.enter("read_esp_info");
        l.dbg("Reading ESP info...");
        let mac = read_mac_address(&mut l, &mut flash_pin, &mut rst_pin).unwrap();
        let chip_id = read_chip_id(&mut l, &mut flash_pin, &mut rst_pin).unwrap();
        let board_id = format!("{}-{}", mac, chip_id);

        l.inf(format!("MAC: {}", mac).as_str());
        l.inf(format!("Chip ID: {}", chip_id).as_str());
        l.inf(format!("Board ID: {}", board_id).as_str());
        l.leave("read_esp_info");
    }

    {
        l.enter("flash_esp");
        l.dbg("Flashing ESP...");
        flash_esp(&mut l, &mut flash_pin, &mut rst_pin).unwrap();
        l.inf("ESP flashed");
        l.leave("flash_esp");
    }

    {
        l.dbg("Connecting to serial port...");
        let mut serial = serialport::new("/dev/ttyUSB0", 115200)
            .timeout(Duration::from_millis(10))
            .data_bits(serialport::DataBits::Seven)
            .open()
            .unwrap();

        l.inf("Streaming logs from serial port...");
        l.trc("==================================");

        let mut buf = [0; 128];
        loop {
            let bytes_read = serial.read(&mut buf).unwrap();
            println!("{}", String::from_utf8_lossy(&buf[..bytes_read]));
        }
    }
}
