package fritzctl.commands.net

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

class NetHostsCommand : CliktCommand(name = "hosts") {

    override fun commandHelp(context: Context) =
        "Listet alle verbundenen Netzwerk-Geräte auf (Name, IP, MAC, Verbindungstyp, Status)."

    override fun run() {
        val terminal = Terminal()
        val config = currentContext.findObject<FritzConfig>()
        val output = config?.output ?: Output(null, terminal)
        val verbose = config?.verbose ?: false
        val log: (String) -> Unit = { if (verbose) terminal.println(cyan("  [debug] $it")) }

        try {
            val hosts = Tr064Client(config?.host ?: "fritz.box", log).getHosts()

            when (output.format) {
                OutputFormat.JSON -> println(hostsToJson(hosts))
                OutputFormat.YAML -> println(hostsToYaml(hosts))
                null -> printTable(terminal, hosts)
            }
        } catch (e: Tr064Exception) {
            output.error(e.message ?: "Unbekannter Fehler")
            exitProcess(1)
        }
    }

    private fun printTable(terminal: Terminal, hosts: List<NetHost>) {
        if (hosts.isEmpty()) {
            terminal.println("Keine Hosts gefunden.")
            return
        }
        val sorted = hosts.sortedWith(compareByDescending<NetHost> { it.active }.thenBy { it.name })
        terminal.println(table {
            header { row("Name", "IP-Adresse", "MAC-Adresse", "Typ", "Status") }
            body {
                sorted.forEach { h ->
                    row(
                        h.name.ifBlank { "-" },
                        h.ip.ifBlank { "-" },
                        h.mac,
                        friendlyType(h.interfaceType),
                        if (h.active) green("online") else red("offline"),
                    )
                }
            }
        })
        val online = hosts.count { it.active }
        terminal.println("$online von ${hosts.size} Geräten online.")
    }

    private fun friendlyType(type: String) = when (type.lowercase()) {
        "ethernet" -> "LAN"
        "802.11" -> "WLAN"
        "homeplug" -> "Powerline"
        else -> type.ifBlank { "?" }
    }

    private fun hostsToJson(hosts: List<NetHost>): String = buildString {
        appendLine("{")
        append("  \"status\": \"ok\",\n  \"hosts\": [")
        hosts.forEachIndexed { i, h ->
            if (i > 0) append(",")
            append("\n    {")
            append("\"name\": ${jsonStr(h.name)}, ")
            append("\"ip\": ${jsonStr(h.ip)}, ")
            append("\"mac\": ${jsonStr(h.mac)}, ")
            append("\"type\": ${jsonStr(h.interfaceType)}, ")
            append("\"active\": ${h.active}")
            append("}")
        }
        append("\n  ]\n}")
    }

    private fun hostsToYaml(hosts: List<NetHost>): String = buildString {
        appendLine("status: ok")
        appendLine("hosts:")
        hosts.forEach { h ->
            appendLine("  - name: ${yamlStr(h.name)}")
            appendLine("    ip: ${yamlStr(h.ip)}")
            appendLine("    mac: ${yamlStr(h.mac)}")
            appendLine("    type: ${yamlStr(h.interfaceType)}")
            appendLine("    active: ${h.active}")
        }
    }.trimEnd()

    private fun jsonStr(v: String) = "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    private fun yamlStr(v: String): String {
        val needsQuoting = v.any { it in ":#{},[]|>&*!'\"%@`\n\r\t" } || v.isBlank()
        return if (needsQuoting) "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\"" else v
    }
}
