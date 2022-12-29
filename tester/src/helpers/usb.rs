use std::thread;
use std::time;

pub fn find_device(vendor_id: u16, product_id: u16) -> bool {
    for device in rusb::devices().unwrap().iter() {
        let device_desc = device.device_descriptor().unwrap();

        if (device_desc.vendor_id() == vendor_id) && (device_desc.product_id() == product_id) {
            return true;
        }
    }

    false
}

pub fn wait_until_device_is_connected(vendor_id: u16, product_id: u16) {
    while !find_device(vendor_id, product_id) {
        thread::sleep(time::Duration::from_secs(1));
    }
}

pub fn wait_until_device_is_disconnected(vendor_id: u16, product_id: u16) {
    while find_device(vendor_id, product_id) {
        thread::sleep(time::Duration::from_secs(1));
    }
}
