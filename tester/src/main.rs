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

fn flash(flash_pin: &mut OutputPin, rst_pin: &mut OutputPin) -> Result<()> {
    enable_flashing(flash_pin, rst_pin)?;

    let mut s = process::Command::new("pio")
        .arg("run")
        .arg("-t")
        .arg("upload")
        .current_dir("/home/pi/slimevr-tracker-esp")
        .stderr(process::Stdio::inherit())
        .stdout(process::Stdio::inherit())
        .spawn()?;

    s.wait()?;

    Ok(())
}

fn read_voltage(
    channel: ChannelSelection,
    adc: &mut ads1x1x::Ads1x1x<I2cInterface<I2c>, Ads1115, Resolution16Bit, OneShot>,
) -> nb::Result<f32, ads1x1x::Error<i2c::Error>> {
    let value = block!(adc.read(channel))?;

    Ok(value as f32 * LSB_SIZE / 1000000.0)
}

fn find_usb_device() -> bool {
    for device in rusb::devices().unwrap().iter() {
        let device_desc = device.device_descriptor().unwrap();

        if (device_desc.vendor_id() == USB_VENDOR_ID)
            && (device_desc.product_id() == USB_PRODUCT_ID)
        {
            return true;
        }
    }

    false
}

fn main() {
    let i2c = I2c::with_bus(1).unwrap();
    let gpio = Gpio::new().unwrap();

    let mut rst_pin = gpio.get(4).unwrap().into_output_high();
    let mut flash_pin = gpio.get(17).unwrap().into_output_high();

    let mut adc = ads1x1x::Ads1x1x::new_ads1115(i2c, ads1x1x::SlaveAddr::default());
    adc.set_full_scale_range(FullScaleRange::Within6_144V)
        .unwrap();
    adc.disable_comparator().unwrap();
    adc.set_data_rate(ads1x1x::DataRate16Bit::Sps860).unwrap();

    while !find_usb_device() {
        println!("Waiting for USB device to be connected...");

        thread::sleep(Duration::from_secs(1));
    }

    let r3v3_voltage = read_voltage(ChannelSelection::SingleA2, &mut adc).unwrap();
    let vcc_voltage = read_voltage(ChannelSelection::SingleA3, &mut adc).unwrap();

    println!("3V3: {}V", r3v3_voltage);
    println!("VCC: {}V", vcc_voltage);

    flash(&mut flash_pin, &mut rst_pin).unwrap();
}
