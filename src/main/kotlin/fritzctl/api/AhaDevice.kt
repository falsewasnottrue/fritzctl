package fritzctl.api

data class AhaDevice(
    val ain: String,
    val name: String,
    val present: Boolean,
    val functionbitmask: Int,
    val switchState: Boolean?,
    val temperatureCelsius: Double?,
    val powerMilliWatt: Int?,
    val energyWh: Int?,
) {
    val isSwitch: Boolean get() = functionbitmask and 0b10 != 0
    val hasPowermeter: Boolean get() = functionbitmask and 0b1000 != 0
    val hasTemperature: Boolean get() = functionbitmask and 0b100 != 0
}
