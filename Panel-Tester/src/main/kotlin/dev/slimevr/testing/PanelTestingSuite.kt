package dev.slimevr.testing

interface PanelTestingSuite {
    fun isReady(): Boolean
    fun btnPressed()
    fun startTest(vararg devices: Int)
    fun getFailedDevices(): List<Int>
}

/**
 * Some panels are symmetric to rotation and can be rotated in the test bed.
 */
interface RotatablePanelTestingSuite {
    /**
     * Update device indices after the panel was rotated manually.
     */
    fun transposeDevices()
}
