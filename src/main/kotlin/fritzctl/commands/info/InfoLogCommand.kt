package fritzctl.commands.info

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.terminal.Terminal
import fritzctl.FritzConfig
import fritzctl.Output
import fritzctl.OutputFormat
import fritzctl.api.Tr064Client
import fritzctl.api.Tr064Exception
import kotlin.system.exitProcess

class InfoLogCommand : CliktCommand(name = "log") {

    override fun commandHelp(context: Context) =
        "Gibt das Systemprotokoll der Fritz!Box aus (neueste Einträge zuerst)."

    private val limit by option(
        "--limit", "-n",
        help = "Maximale Anzahl der angezeigten Einträge (Standard: 50, 0 = alle)",
    ).int().default(50)

    override fun run() {
        val terminal = Terminal()
        val config = currentContext.findObject<FritzConfig>()
        val output = config?.output ?: Output(null, terminal)
        val verbose = config?.verbose ?: false
        val log: (String) -> Unit = { if (verbose) terminal.println(cyan("  [debug] $it")) }

        try {
            var entries = Tr064Client(config?.host ?: "fritz.box", log).getDeviceLog()
            if (limit > 0) entries = entries.take(limit)

            when (output.format) {
                OutputFormat.JSON -> println(toJson(entries))
                OutputFormat.YAML -> println(toYaml(entries))
                null -> entries.forEach { terminal.println(it) }
            }
        } catch (e: Tr064Exception) {
            output.error(e.message ?: "Unbekannter Fehler")
            exitProcess(1)
        }
    }

    private fun toJson(entries: List<String>): String = buildString {
        appendLine("{")
        append("  \"status\": \"ok\",\n  \"log\": [")
        entries.forEachIndexed { i, line ->
            if (i > 0) append(",")
            append("\n    ${q(line)}")
        }
        append("\n  ]\n}")
    }

    private fun toYaml(entries: List<String>): String = buildString {
        appendLine("status: ok")
        appendLine("log:")
        entries.forEach { appendLine("  - ${q(it)}") }
    }.trimEnd()

    private fun q(v: String) = "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
