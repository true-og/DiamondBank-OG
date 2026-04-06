package net.trueog.diamondbankog

import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.mm
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class ToggleDebug : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (!sender.hasPermission("diamondbank-og.debug")) {
            sender.sendMessage(
                mm.deserialize("${config.prefix}<reset>: <red>You do not have permission to use this command.")
            )
            return true
        }

        DiamondBankOG.debug = !DiamondBankOG.debug
        sender.sendMessage(
            mm.deserialize(
                "${config.prefix}<reset>: Debug mode is now turned ${if (DiamondBankOG.debug) "<green>on" else "<red>off"}<reset>."
            )
        )
        return true
    }
}
