package net.trueog.diamondbankog.commands

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class SetBalance : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (!sender.hasPermission("diamondbank-og.setbalance")) {
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You do not have permission to use this command."))
            return true
        }

        if (args == null || args.isEmpty()) {
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You did not provide the name or the UUID of a player and the amount of <aqua>Diamonds<red>."))
            return true
        }
        if (args.size != 2) {
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Please (only) provide the name or the UUID of a player and the amount of <aqua>Diamonds<red>."))
            return true
        }

        val player = Bukkit.getOfflinePlayer(args[0])
        if (!player.hasPlayedBefore()) {
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>That player doesn't exist."))
            return true
        }

        val balance: Long
        try {
            balance = args[1].toLong()
        } catch (_: Exception) {
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Invalid argument."))
            return true
        }

        GlobalScope.launch {
            val error = DiamondBankOG.postgreSQL.setPlayerBalance(player.uniqueId, balance)
            if (error) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to set that player's balance."))
                return@launch
            }
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <green>Successfully set the balance of <red>${player.name} <green>to <yellow>$balance <aqua>${if (balance == 1L) "Diamond" else "Diamonds"}<green>."))
        }
        return true
    }
}