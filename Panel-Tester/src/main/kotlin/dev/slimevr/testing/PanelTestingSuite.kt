package dev.slimevr.testing

interface PanelTestingSuite {
    fun isReady(): Boolean
    fun btnPressed()
    fun startTest(vararg devices: Int)
    fun getFailedDevices(): List<Int>
}
