package org.comdex.community.comdex

/** Manufacturer FAQ (March 2017) and troubleshooting guide (September 2016).
 * Pure functions so native/widget command gates can be tested without a radio.
 */
object DeviceRules {
    fun matchesName(kind: String, name: String?): Boolean {
        val value = name?.trim() ?: return false
        return when (kind) {
            "comdex" -> Regex("COM-DEX(?: [0-9]+)?", RegexOption.IGNORE_CASE).matches(value)
            "cdrm" -> Regex("(?:CDRM|COM-DEX Remote Mic)(?: [0-9]+)?", RegexOption.IGNORE_CASE).matches(value)
            else -> false
        }
    }

    fun transport(mode: String): Int = when(mode) {
        "blue" -> 2 // BluetoothDevice.TRANSPORT_LE
        "green" -> 1 // BluetoothDevice.TRANSPORT_BREDR, as in original GattManager
        else -> error("Неизвестный режим COM-DEX")
    }
    fun commandError(kind: String, name: String, value: Int?, profile: Int?, parentReady: Boolean): String? {
        if(kind == "cdrm" && name == "stream" && value == 2) {
            if(!parentReady) return "Для запуска Remote Mic сначала подключите COM-DEX и закройте петлю"
            if(profile == 2) return "Remote Mic нельзя запускать во время телефонного разговора"
            if(profile == null || profile == 0) return "Нет аудиосоединения COM-DEX"
        }
        if(kind == "comdex" && name == "boost" && profile == 2) return "Усиление мультимедиа недоступно во время телефонного разговора"
        return null
    }
    fun batteryWarning(level: Int?): Int = when {
        level == null || level !in 0..100 -> 0
        level < 5 -> 2
        level <= 20 -> 1
        else -> 0
    }
}
