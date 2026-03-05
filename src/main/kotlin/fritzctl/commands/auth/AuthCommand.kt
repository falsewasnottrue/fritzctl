package fritzctl.commands.auth

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands

class AuthCommand : CliktCommand(name = "auth") {

    override fun commandHelp(context: Context) = "Authentication-Befehle."

    init {
        subcommands(LoginCommand(), LogoutCommand())
    }

    override fun run() = Unit
}
