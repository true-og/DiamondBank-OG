package net.trueog.diamondbankog.commands

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class Pay : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (sender !is Player) {
            sender.sendMessage("You can only execute this command as a player.")
            return true
        }

        if (!sender.hasPermission("diamondbank-og.pay")) {
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

        val amount: Long
        try {
            amount = args[1].toLong()
            if (amount <= 0) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You cannot pay a negative amount."))
                return true
            }
        } catch (_: Exception) {
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Invalid argument."))
            return true
        }

        GlobalScope.launch {
            val senderBalance = DiamondBankOG.postgreSQL.getPlayerBalance(sender.uniqueId)
            if (senderBalance == -1L) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to set that player's balance."))
                return@launch
            }

            if (amount > senderBalance) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Cannot pay <yellow>$amount <aqua>${if (amount == 1L) "Diamond" else "Diamonds"} <red>to <green>${player.name} <red>because your bank only contains <yellow>$senderBalance <aqua>${if (senderBalance == 1L) "diamond" else "diamonds"}<red>."))
                return@launch
            }

            var error = DiamondBankOG.postgreSQL.setPlayerBalance(sender.uniqueId, senderBalance - amount)
            if (error) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to set that player's balance."))
                return@launch
            }

            error = DiamondBankOG.postgreSQL.depositToPlayerBalance(player.uniqueId, amount)
            if (error) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to set that player's balance."))
                return@launch
            }
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <green>Successfully paid <yellow>$amount <aqua>${if (amount == 1L) "Diamond" else "Diamonds"} <green>to <red>${player.name}<green>."))

            if (player.isOnline) {
                player.player?.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <green>${sender.name} has paid you <yellow>$amount <aqua>${if (amount == 1L) "Diamond" else "Diamonds"}<green>."))
            }
        }

        return true
    }
}