use std::{thread, time};

use rppal::gpio;

pub struct ESP {
    pub flash_pin: gpio::OutputPin,
    pub rst_pin: gpio::OutputPin,
}

impl ESP {
    pub fn new(flash_pin: gpio::OutputPin, rst_pin: gpio::OutputPin) -> Self {
        ESP { flash_pin, rst_pin }
    }

    pub fn reset_no_delay(&mut self) -> Result<(), gpio::Error> {
        self.rst_pin.set_low();

        thread::sleep(time::Duration::from_millis(200));

        self.rst_pin.set_high();

        Ok(())
    }

    pub fn reset(&mut self) -> Result<(), gpio::Error> {
        self.reset_no_delay()?;

        thread::sleep(time::Duration::from_millis(100));

        Ok(())
    }

    pub fn reset_for_upload(&mut self) -> gpio::Result<()> {
        self.flash_pin.set_low();

        self.reset()?;

        self.flash_pin.set_high();

        Ok(())
    }
}
