package fritzctl.commands.wifi

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import fritzctl.FritzConfig
import fritzctl.Output
import fritzctl.OutputFormat
import fritzctl.api.Tr064Client
import fritzctl.api.Tr064Exception
import fritzctl.api.WifiNetwork
import kotlin.system.exitProcess

class WifiStatusCommand : CliktCommand(name = "status") {

    override fun commandHelp(context: Context) =
        "Zeigt alle WLAN-Netze mit SSID, Band, Kanal, Standard und Status."

    override fun run() {
        val terminal = Terminal()
        val config = currentContext.findObject<FritzConfig>()
        val output = config?.output ?: Output(null, terminal)
        val verbose = config?.verbose ?: false
        val log: (String) -> Unit = { if (verbose) terminal.println(cyan("  [debug] $it")) }

        try {
            val networks = Tr064Client(config?.host ?: "fritz.box", log).getWifiNetworks()

            when (output.format) {
                OutputFormat.JSON -> println(toJson(networks))
                OutputFormat.YAML -> println(toYaml(networks))
                null -> printTable(terminal, networks)
            }
        } catch (e: Tr064Exception) {
            output.error(e.message ?: "Unbekannter Fehler")
            exitProcess(1)
        }
    }

    private fun printTable(terminal: Terminal, networks: List<WifiNetwork>) {
        if (networks.isEmpty()) {
            terminal.println("Keine WLAN-Konfigurationen gefunden.")
            return
        }
        terminal.println(table {
            header { row("Band", "SSID", "Kanal", "Standard", "Status") }
            body {
                networks.forEach { n ->
                    row(
                        n.band,
                        n.ssid.ifBlank { "-" },
                        n.channel.ifBlank { "-" },
                        n.standard.ifBlank { "-" },
                        if (n.enabled) green("aktiv") else red("inaktiv"),
                    )
                }
            }
        })
    }

    private fun toJson(networks: List<WifiNetwork>): String = buildString {
        appendLine("{")
        append("  \"status\": \"ok\",\n  \"networks\": [")
        networks.forEachIndexed { i, n ->
            if (i > 0) append(",")
            append("\n    {")
            append("\"band\": ${q(n.band)}, ")
            append("\"ssid\": ${q(n.ssid)}, ")
            append("\"channel\": ${q(n.channel)}, ")
            append("\"standard\": ${q(n.standard)}, ")
            append("\"enabled\": ${n.enabled}")
            append("}")
        }
        append("\n  ]\n}")
    }

    private fun toYaml(networks: List<WifiNetwork>): String = buildString {
        appendLine("status: ok")
        appendLine("networks:")
        networks.forEach { n ->
            appendLine("  - band: ${n.band}")
            appendLine("    ssid: ${q(n.ssid)}")
            appendLine("    channel: ${q(n.channel)}")
            appendLine("    standard: ${q(n.standard)}")
            appendLine("    enabled: ${n.enabled}")
        }
    }.trimEnd()

    private fun q(v: String) = "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
