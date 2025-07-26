package net.trueog.diamondbankog.commands

import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.DiamondBankOG.Companion.mm
import net.trueog.diamondbankog.DiamondBankOG.Companion.redis
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

internal class AutoDeposit : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (economyDisabled) {
            sender.sendMessage(
                mm.deserialize("${config.prefix}<reset>: <red>The economy is disabled. Please notify a staff member.")
            )
            return true
        }

        if (sender !is Player) {
            sender.sendMessage("You can only execute this command as a player.")
            return true
        }

        if (!sender.hasPermission("diamondbank-og.deposit")) {
            sender.sendMessage(
                mm.deserialize("${config.prefix}<reset>: <red>You do not have permission to use this command.")
            )
            return true
        }

        if (redis.getValue("diamondbankog:${sender.uniqueId}:autodeposit") == "true") {
            redis.setValue("diamondbankog:${sender.uniqueId}:autodeposit", "false")
            sender.sendMessage(
                mm.deserialize("${config.prefix}<reset>: <green>Successfully turned auto-deposit <red>off<green>.")
            )
        } else {
            if (redis.getValue("diamondbankog:${sender.uniqueId}:autocompress") == "true") {
                sender.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <#FFA500>You cannot have both auto-deposit and auto-compress on at the same time, so auto-compress has been turned <red>off<#FFA500>."
                    )
                )
                redis.setValue("diamondbankog:${sender.uniqueId}:autocompress", "false")
            }
            redis.setValue("diamondbankog:${sender.uniqueId}:autodeposit", "true")
            sender.sendMessage(mm.deserialize("${config.prefix}<reset>: <green>Successfully turned auto-deposit on."))
        }
        return true
    }
}
