package net.trueog.diamondbankog.commands

import net.trueog.diamondbankog.Config
import net.trueog.diamondbankog.DiamondBankOG
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class DiamondBankHelp : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>?
    ): Boolean {
        sender.sendMessage(
            DiamondBankOG.mm.deserialize(
                "<gray>---- ${Config.prefix}<reset> Help <gray>----<reset>\n" +
                        "<bold>How does the <aqua>Diamonds<white> currency work?<reset>\n" +
                        "A Diamond is made out of 9 Shards, just like Diamond Blocks are made out of 9 Diamonds.\n" +
                        "For example 1.8 Diamonds are 1 Diamond and 8 Shards, but for example 1.9 Diamonds are the same as 2 Diamonds."
            )
        )
        return true
    }
}