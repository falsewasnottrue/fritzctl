package fritzctl.commands.call

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import fritzctl.FritzConfig
import fritzctl.Output
import fritzctl.OutputFormat
import fritzctl.api.CallEntry
import fritzctl.api.Tr064Client
import fritzctl.api.Tr064Exception
import kotlin.system.exitProcess

class CallListCommand : CliktCommand(name = "list") {

    override fun commandHelp(context: Context) =
        "Gibt die Anrufliste aus. Ohne Filter werden alle Anrufe angezeigt."

    private val missed by option("--missed", "-m", help = "Nur verpasste Anrufe").flag()
    private val incoming by option("--in", "-i", help = "Nur eingehende Anrufe").flag()
    private val outgoing by option("--out", "-o", help = "Nur ausgehende Anrufe").flag()
    private val limit by option(
        "--limit", "-n",
        help = "Maximale Anzahl Einträge (Standard: 30, 0 = alle)",
    ).int().default(30)

    override fun run() {
        val terminal = Terminal()
        val config = currentContext.findObject<FritzConfig>()
        val output = config?.output ?: Output(null, terminal)
        val verbose = config?.verbose ?: false
        val log: (String) -> Unit = { if (verbose) terminal.println(cyan("  [debug] $it")) }

        try {
            var calls = Tr064Client(config?.host ?: "fritz.box", log).getCallList()

            if (missed) calls = calls.filter { it.type in listOf(2, 10) }
            else if (incoming) calls = calls.filter { it.type in listOf(1, 4) }
            else if (outgoing) calls = calls.filter { it.type in listOf(3, 9) }

            if (limit > 0) calls = calls.take(limit)

            when (output.format) {
                OutputFormat.JSON -> println(toJson(calls))
                OutputFormat.YAML -> println(toYaml(calls))
                null -> printTable(terminal, calls)
            }
        } catch (e: Tr064Exception) {
            output.error(e.message ?: "Unbekannter Fehler")
            exitProcess(1)
        }
    }

    private fun printTable(terminal: Terminal, calls: List<CallEntry>) {
        if (calls.isEmpty()) {
            terminal.println("Keine Anrufe gefunden.")
            return
        }
        terminal.println(table {
            header { row("Datum", "Typ", "Nummer / Name", "Dauer", "Gerät") }
            body {
                calls.forEach { c ->
                    val typeStr = when (c.type) {
                        1, 4 -> green(c.typeLabel)
                        2, 10 -> red(c.typeLabel)
                        else -> yellow(c.typeLabel)
                    }
                    val display = if (c.name.isNotBlank()) "${c.partner} (${c.name})" else c.partner
                    row(c.date, typeStr, display.ifBlank { "-" }, c.duration.ifBlank { "-" }, c.device.ifBlank { "-" })
                }
            }
        })
    }

    private fun toJson(calls: List<CallEntry>): String = buildString {
        appendLine("{")
        append("  \"status\": \"ok\",\n  \"calls\": [")
        calls.forEachIndexed { i, c ->
            if (i > 0) append(",")
            append("\n    {")
            append("\"date\": ${q(c.date)}, ")
            append("\"type\": ${q(c.typeLabel)}, ")
            append("\"partner\": ${q(c.partner)}, ")
            append("\"name\": ${q(c.name)}, ")
            append("\"duration\": ${q(c.duration)}, ")
            append("\"device\": ${q(c.device)}")
            append("}")
        }
        append("\n  ]\n}")
    }

    private fun toYaml(calls: List<CallEntry>): String = buildString {
        appendLine("status: ok")
        appendLine("calls:")
        calls.forEach { c ->
            appendLine("  - date: ${q(c.date)}")
            appendLine("    type: ${q(c.typeLabel)}")
            appendLine("    partner: ${q(c.partner)}")
            if (c.name.isNotBlank()) appendLine("    name: ${q(c.name)}")
            appendLine("    duration: ${q(c.duration)}")
            if (c.device.isNotBlank()) appendLine("    device: ${q(c.device)}")
        }
    }.trimEnd()

    private fun q(v: String) = "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
