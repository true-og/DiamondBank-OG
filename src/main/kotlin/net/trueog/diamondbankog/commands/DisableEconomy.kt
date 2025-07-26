package net.trueog.diamondbankog.commands

import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.DiamondBankOG.Companion.mm
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

internal class DisableEconomy : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (!sender.hasPermission("diamondbank-og.admin")) {
            sender.sendMessage(
                mm.deserialize("${config.prefix}<reset>: <red>You do not have permission to use this command.")
            )
            return true
        }

        economyDisabled = true
        sender.sendMessage(
            mm.deserialize("${config.prefix}<reset>: <green>Successfully <red>disabled <green>the economy.")
        )
        return true
    }
}
