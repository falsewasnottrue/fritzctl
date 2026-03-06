package fritzctl.api

data class NetHost(
    val mac: String,
    val ip: String,
    val name: String,
    val interfaceType: String,  // "Ethernet", "802.11" (WiFi), "HomePlug"
    val active: Boolean,
)
