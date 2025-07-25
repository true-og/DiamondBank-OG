package net.trueog.diamondbankog.commands

import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

internal class EnableEconomy : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (!sender.hasPermission("diamondbank-og.admin")) {
            sender.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${config.prefix}<reset>: <red>You do not have permission to use this command."
                )
            )
            return true
        }

        DiamondBankOG.economyDisabled = false
        sender.sendMessage(
            DiamondBankOG.mm.deserialize("${config.prefix}<reset>: <green>Successfully enabled the economy.")
        )
        return true
    }
}
