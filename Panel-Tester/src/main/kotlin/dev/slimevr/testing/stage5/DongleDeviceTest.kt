package dev.slimevr.testing.stage5

import dev.slimevr.testing.DeviceTest
import java.util.logging.Level
import java.util.logging.Logger

class DongleDeviceTest: DeviceTest
{
    constructor(
        deviceNum: Int = 0,
        logger: Logger = Logger.getLogger("dongle")
    ) : super(deviceNum, logger)

    var channel: Int = 0
}
