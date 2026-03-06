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
import fritzctl.api.NetHost
import fritzctl.api.Tr064Client
import fritzctl.api.Tr064Exception
import kotlin.system.exitProcess

class WifiClientsCommand : CliktCommand(name = "clients") {

    override fun commandHelp(context: Context) =
        "Listet alle verbundenen WLAN-Geräte auf."

    override fun run() {
        val terminal = Terminal()
        val config = currentContext.findObject<FritzConfig>()
        val output = config?.output ?: Output(null, terminal)
        val verbose = config?.verbose ?: false
        val log: (String) -> Unit = { if (verbose) terminal.println(cyan("  [debug] $it")) }

        try {
            val clients = Tr064Client(config?.host ?: "fritz.box", log)
                .getHosts()
                .filter { it.interfaceType == "802.11" }
                .sortedWith(compareByDescending<NetHost> { it.active }.thenBy { it.name })

            when (output.format) {
                OutputFormat.JSON -> println(toJson(clients))
                OutputFormat.YAML -> println(toYaml(clients))
                null -> printTable(terminal, clients)
            }
        } catch (e: Tr064Exception) {
            output.error(e.message ?: "Unbekannter Fehler")
            exitProcess(1)
        }
    }

    private fun printTable(terminal: Terminal, clients: List<NetHost>) {
        if (clients.isEmpty()) {
            terminal.println("Keine WLAN-Geräte verbunden.")
            return
        }
        terminal.println(table {
            header { row("Name", "IP-Adresse", "MAC-Adresse", "Status") }
            body {
                clients.forEach { c ->
                    row(
                        c.name.ifBlank { "-" },
                        c.ip.ifBlank { "-" },
                        c.mac,
                        if (c.active) green("online") else red("offline"),
                    )
                }
            }
        })
        terminal.println("${clients.count { it.active }} von ${clients.size} WLAN-Geräten online.")
    }

    private fun toJson(clients: List<NetHost>): String = buildString {
        appendLine("{")
        append("  \"status\": \"ok\",\n  \"clients\": [")
        clients.forEachIndexed { i, c ->
            if (i > 0) append(",")
            append("\n    {")
            append("\"name\": ${q(c.name)}, ")
            append("\"ip\": ${q(c.ip)}, ")
            append("\"mac\": ${q(c.mac)}, ")
            append("\"active\": ${c.active}")
            append("}")
        }
        append("\n  ]\n}")
    }

    private fun toYaml(clients: List<NetHost>): String = buildString {
        appendLine("status: ok")
        appendLine("clients:")
        clients.forEach { c ->
            appendLine("  - name: ${q(c.name)}")
            appendLine("    ip: ${q(c.ip)}")
            appendLine("    mac: ${q(c.mac)}")
            appendLine("    active: ${c.active}")
        }
    }.trimEnd()

    private fun q(v: String) = "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
