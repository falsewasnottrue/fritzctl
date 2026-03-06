package fritzctl

import com.github.ajalt.clikt.core.subcommands
import fritzctl.commands.auth.AuthCommand

fun main(args: Array<String>) {
    FritzCtl()
        .subcommands(AuthCommand())
        .main(args)
}
