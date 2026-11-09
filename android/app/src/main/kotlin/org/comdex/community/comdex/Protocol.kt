package org.comdex.community.comdex

// Byte-for-byte from supplied APK; see docs/PROTOCOL.md.
object Protocol {
    val VOL_UP_CMD = byteArrayOf(162.toByte(), 0.toByte(), 2.toByte(), 128.toByte(), 0.toByte(), 39.toByte(), 118.toByte(), 127.toByte())
    val VOL_DW_CMD = byteArrayOf(162.toByte(), 0.toByte(), 2.toByte(), 128.toByte(), 0.toByte(), 40.toByte(), 118.toByte(), 112.toByte())
    val NEXT_PROGRAM = byteArrayOf(162.toByte(), 0.toByte(), 2.toByte(), 128.toByte(), 0.toByte(), 41.toByte(), 118.toByte(), 113.toByte())
    val CHANGE_PROGRAM_MODE = byteArrayOf(162.toByte(), 0.toByte(), 2.toByte(), 128.toByte(), 0.toByte(), 42.toByte(), 118.toByte(), 114.toByte())
    val ROOM_ON = byteArrayOf(162.toByte(), 1.toByte(), 64.toByte(), 168.toByte(), 32.toByte(), 17.toByte(), 0.toByte(), 10.toByte(), 8.toByte(), 0.toByte(), 4.toByte(), 0.toByte(), 22.toByte(), 100.toByte(), 141.toByte())
    val FREE_FOCUS_OMNI = byteArrayOf(162.toByte(), 1.toByte(), 64.toByte(), 168.toByte(), 32.toByte(), 17.toByte(), 0.toByte(), 16.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 19.toByte(), 100.toByte(), 134.toByte())
    val FREE_FOCUS_FRONT = byteArrayOf(162.toByte(), 1.toByte(), 64.toByte(), 168.toByte(), 32.toByte(), 17.toByte(), 0.toByte(), 16.toByte(), 0.toByte(), 0.toByte(), 12.toByte(), 16.toByte(), 19.toByte(), 27.toByte(), 67.toByte())
    val FREE_FOCUS_REAR = byteArrayOf(162.toByte(), 1.toByte(), 64.toByte(), 168.toByte(), 32.toByte(), 17.toByte(), 0.toByte(), 16.toByte(), 0.toByte(), 0.toByte(), 13.toByte(), 16.toByte(), 19.toByte(), 161.toByte(), 238.toByte())
    val FREE_FOCUS_LEFT = byteArrayOf(162.toByte(), 1.toByte(), 64.toByte(), 168.toByte(), 32.toByte(), 17.toByte(), 0.toByte(), 16.toByte(), 0.toByte(), 0.toByte(), 14.toByte(), 16.toByte(), 19.toByte(), 212.toByte(), 180.toByte())
    val FREE_FOCUS_RIGHT = byteArrayOf(162.toByte(), 1.toByte(), 64.toByte(), 168.toByte(), 32.toByte(), 17.toByte(), 0.toByte(), 16.toByte(), 0.toByte(), 0.toByte(), 15.toByte(), 16.toByte(), 19.toByte(), 110.toByte(), 25.toByte())
    fun uuid(tail: String) = "95986f2d-d705-4421-9890-2d32bff7$tail"
}
