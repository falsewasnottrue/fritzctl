package fritzctl

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option

class FritzCtl : CliktCommand(name = "fritzctl") {

    override fun commandHelp(context: Context) = "CLI-Tool zur Verwaltung der Fritzbox."

    val host: String by option("--host", "-H", help = "Hostname oder IP der Fritzbox")
        .default("fritz.box")

    override fun run() = Unit
}
