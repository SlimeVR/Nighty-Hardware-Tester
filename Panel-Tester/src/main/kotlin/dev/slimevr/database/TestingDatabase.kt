package dev.slimevr.database

import dev.slimevr.testing.DeviceTest

interface TestingDatabase {

    fun sendTestData(device: DeviceTest): String
}
