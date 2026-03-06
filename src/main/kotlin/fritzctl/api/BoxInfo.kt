package fritzctl.api

data class BoxInfo(
    val manufacturer: String,
    val modelName: String,
    val serialNumber: String,
    val firmwareVersion: String,
    val hardwareVersion: String,
    val uptimeSeconds: Long?,
)
