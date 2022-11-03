pub fn find_device(vendor_id: u16, product_id: u16) -> bool {
    let s = tracing::span!(tracing::Level::DEBUG, "find_usb_device");
    let _enter = s.enter();

    for device in rusb::devices().unwrap().iter() {
        let device_desc = device.device_descriptor().unwrap();

        if (device_desc.vendor_id() == vendor_id) && (device_desc.product_id() == product_id) {
            log::info!("Found USB device");

            return true;
        }
    }

    log::error!("USB device not found");

    false
}
