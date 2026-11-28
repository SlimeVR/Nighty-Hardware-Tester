package dev.slimevr.testing.stage5

import dev.slimevr.testing.DeviceTest
import java.util.logging.Level
import java.util.logging.Logger

class DongleDeviceTest: DeviceTest
{
    companion object {
        const val RSSI_INVALID = 255
        const val DONGLE_ADV_CHANNEL = 22
    }
    constructor(
        deviceNum: Int = 0,
        logger: Logger = Logger.getLogger("dongle")
    ) : super(deviceNum, logger)

    // Channel number (offset from 2400MHz)
    var channel: Int = 0
    var bestRssi: Int = RSSI_INVALID
    var powerTestRequired = true
    var radioTestRequired = true
}
