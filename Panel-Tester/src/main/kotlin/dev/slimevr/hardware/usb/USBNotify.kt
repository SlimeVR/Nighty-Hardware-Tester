package dev.slimevr.hardware.usb

interface USBNotify {

    /**
     * Dmesg callback on Serial port attach/detach events
     */
    fun setUSB(addr: String, tty: String)
}
