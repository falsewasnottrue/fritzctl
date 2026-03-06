package fritzctl.commands.net

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.terminal.Terminal
import fritzctl.FritzConfig
import fritzctl.Output
import fritzctl.OutputFormat
import fritzctl.api.Tr064Client
import fritzctl.api.Tr064Exception
import fritzctl.api.WanStatus
import kotlin.system.exitProcess

class NetWanCommand : CliktCommand(name = "wan") {

    override fun commandHelp(context: Context) =
        "Zeigt den WAN-Status (externe IP, Verbindungstyp, Up-/Download-Geschwindigkeit, Uptime)."

    override fun run() {
        val terminal = Terminal()
        val config = currentContext.findObject<FritzConfig>()
        val output = config?.output ?: Output(null, terminal)
        val verbose = config?.verbose ?: false
        val log: (String) -> Unit = { if (verbose) terminal.println(cyan("  [debug] $it")) }

        try {
            val wan = Tr064Client(config?.host ?: "fritz.box", log).getWanStatus()

            when (output.format) {
                OutputFormat.JSON -> println(wanToJson(wan))
                OutputFormat.YAML -> println(wanToYaml(wan))
                null -> printHuman(terminal, wan)
            }
        } catch (e: Tr064Exception) {
            output.error(e.message ?: "Unbekannter Fehler")
            exitProcess(1)
        }
    }

    private fun printHuman(terminal: Terminal, w: WanStatus) {
        val statusStr = if (w.linkStatus.equals("Up", ignoreCase = true)) green(w.linkStatus) else red(w.linkStatus)
        terminal.println("Verbindungstyp:  ${w.accessType}")
        terminal.println("Status:          $statusStr")
        w.externalIp?.let { terminal.println("Externe IP:      $it") }
        w.upstreamMaxBps?.let { terminal.println("Max. Upload:     ${formatMbps(it)}") }
        w.downstreamMaxBps?.let { terminal.println("Max. Download:   ${formatMbps(it)}") }
        w.upstreamCurrentBps?.let { terminal.println("Akt. Upload:     ${formatMbps(it)}") }
        w.downstreamCurrentBps?.let { terminal.println("Akt. Download:   ${formatMbps(it)}") }
        w.uptimeSeconds?.let { terminal.println("Verbunden seit:  ${formatUptime(it)}") }
    }

    private fun wanToJson(w: WanStatus): String = buildString {
        appendLine("{")
        append("  \"status\": \"ok\"")
        append(",\n  \"accessType\": ${jsonStr(w.accessType)}")
        append(",\n  \"linkStatus\": ${jsonStr(w.linkStatus)}")
        w.externalIp?.let { append(",\n  \"externalIp\": ${jsonStr(it)}") }
        w.upstreamMaxBps?.let { append(",\n  \"upstreamMaxBps\": $it") }
        w.downstreamMaxBps?.let { append(",\n  \"downstreamMaxBps\": $it") }
        w.upstreamCurrentBps?.let { append(",\n  \"upstreamCurrentBps\": $it") }
        w.downstreamCurrentBps?.let { append(",\n  \"downstreamCurrentBps\": $it") }
        w.uptimeSeconds?.let { append(",\n  \"uptimeSeconds\": $it") }
        append("\n}")
    }

    private fun wanToYaml(w: WanStatus): String = buildString {
        appendLine("status: ok")
        appendLine("accessType: ${w.accessType}")
        appendLine("linkStatus: ${w.linkStatus}")
        w.externalIp?.let { appendLine("externalIp: $it") }
        w.upstreamMaxBps?.let { appendLine("upstreamMaxBps: $it") }
        w.downstreamMaxBps?.let { appendLine("downstreamMaxBps: $it") }
        w.upstreamCurrentBps?.let { appendLine("upstreamCurrentBps: $it") }
        w.downstreamCurrentBps?.let { appendLine("downstreamCurrentBps: $it") }
        w.uptimeSeconds?.let { appendLine("uptimeSeconds: $it") }
    }.trimEnd()

    private fun formatMbps(bps: Long): String {
        val mbps = bps / 1_000_000.0
        return if (mbps >= 1) "%.1f Mbit/s".format(mbps) else "%.0f kbit/s".format(bps / 1_000.0)
    }

    private fun formatUptime(seconds: Long): String {
        val d = seconds / 86400
        val h = (seconds % 86400) / 3600
        val m = (seconds % 3600) / 60
        return buildString {
            if (d > 0) append("${d}d ")
            if (h > 0 || d > 0) append("${h}h ")
            append("${m}min")
        }
    }

    private fun jsonStr(v: String) = "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
