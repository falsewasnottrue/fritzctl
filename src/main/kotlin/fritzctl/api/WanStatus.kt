package fritzctl.api

data class WanStatus(
    val accessType: String,          // "DSL", "Ethernet", "X_AVM_ISDN", ...
    val linkStatus: String,          // "Up", "Down"
    val upstreamMaxBps: Long?,       // max upstream in bit/s
    val downstreamMaxBps: Long?,     // max downstream in bit/s
    val upstreamCurrentBps: Long?,   // current upstream in bit/s
    val downstreamCurrentBps: Long?, // current downstream in bit/s
    val externalIp: String?,
    val uptimeSeconds: Long?,
)
