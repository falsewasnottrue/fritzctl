package fritzctl.api

data class CallEntry(
    val type: Int,       // 1=eingehend, 2=verpasst, 3=ausgehend, 4=aktiv-ein, 9=aktiv-aus, 10=abgelehnt
    val caller: String,
    val called: String,
    val name: String,
    val date: String,
    val duration: String,
    val device: String,
) {
    val typeLabel: String get() = when (type) {
        1, 4 -> "eingehend"
        2, 10 -> "verpasst"
        3, 9 -> "ausgehend"
        else -> "unbekannt"
    }
    val partner: String get() = when (type) {
        3, 9 -> called   // bei ausgehend: die gewählte Nummer
        else -> caller   // bei eingehend/verpasst: die anrufende Nummer
    }
}
