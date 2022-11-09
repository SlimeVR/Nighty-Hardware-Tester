use ads1x1x::{ic, interface, mode, DynamicOneShot};

const LSB_SIZE: f32 = 187.5;

pub struct Ads1115<I2C> {
    instance: ads1x1x::Ads1x1x<
        interface::I2cInterface<I2C>,
        ic::Ads1115,
        ic::Resolution16Bit,
        mode::OneShot,
    >,
}

impl<I2C, E> Ads1115<I2C>
where
    I2C: embedded_hal::blocking::i2c::Write<Error = E>
        + embedded_hal::blocking::i2c::WriteRead<Error = E>
        + embedded_hal::blocking::i2c::Read<Error = E>,
{
    pub fn new(i2c: I2C) -> Result<Ads1115<I2C>, ads1x1x::Error<E>> {
        let mut adc = ads1x1x::Ads1x1x::new_ads1115(i2c, ads1x1x::SlaveAddr::default());
        adc.set_full_scale_range(ads1x1x::FullScaleRange::Within6_144V)?;
        adc.disable_comparator()?;
        adc.set_data_rate(ads1x1x::DataRate16Bit::Sps860)?;

        Ok(Ads1115 { instance: adc })
    }

    pub fn measure(
        &mut self,
        channel: ads1x1x::ChannelSelection,
    ) -> nb::Result<f32, ads1x1x::Error<E>> {
        let value = nb::block!(self.instance.read(channel))?;

        let voltage = value as f32 * LSB_SIZE / 1000000.0;

        Ok(voltage)
    }
}
