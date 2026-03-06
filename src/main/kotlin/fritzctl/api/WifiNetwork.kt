package fritzctl.api

data class WifiNetwork(
    val index: Int,       // 1=2,4 GHz, 2=5 GHz, 3=Gast
    val ssid: String,
    val enabled: Boolean,
    val channel: String,
    val standard: String, // "n", "ac", "ax", ...
    val band: String,     // "2,4 GHz", "5 GHz", "Gast"
)
