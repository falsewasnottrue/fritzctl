package fritzctl.commands.device

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal

class DeviceListCommand : CliktCommand(name = "list") {

    override fun commandHelp(context: Context) = "Listet alle bekannten Geräte auf."

    private val activeOnly: Boolean by option("--active", "-a", help = "Nur aktive Geräte anzeigen")
        .flag()

    override fun run() {
        val terminal = Terminal()

        // Platzhalterdaten – später durch echte Fritzbox-API ersetzen
        val devices = listOf(
            Triple("Mein iPhone",     "192.168.1.42", true),
            Triple("Laptop-Work",     "192.168.1.10", true),
            Triple("Smart-TV",        "192.168.1.55", false),
            Triple("Raspberry Pi",    "192.168.1.20", true),
        ).filter { if (activeOnly) it.third else true }

        terminal.println(
            table {
                header { row("Name", "IP-Adresse", "Status") }
                body {
                    devices.forEach { (name, ip, active) ->
                        row(
                            yellow(name),
                            ip,
                            if (active) green("online") else red("offline"),
                        )
                    }
                }
            }
        )
    }
}
