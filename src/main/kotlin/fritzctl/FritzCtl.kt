package fritzctl

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.terminal.Terminal

class FritzCtl : CliktCommand(name = "fritzctl") {

    override fun commandHelp(context: Context) = "CLI-Tool zur Verwaltung der Fritzbox."

    val host: String by option("--host", "-H", help = "Hostname oder IP der Fritzbox")
        .default("fritz.box")

    val verbose: Boolean by option("--verbose", "-v", help = "Ausführliche Debug-Ausgaben aktivieren")
        .flag()

    val outputFormat: OutputFormat? by option(
        "-o", "--output",
        help = "Ausgabeformat für maschinelle Weiterverarbeitung (erlaubte Werte: ${OutputFormat.entries.joinToString { it.id }})",
    ).choice(OutputFormat.entries.associate { it.id to it })

    override fun run() {
        currentContext.obj = FritzConfig(
            host = host,
            verbose = verbose,
            output = Output(outputFormat, Terminal()),
        )
    }
}
