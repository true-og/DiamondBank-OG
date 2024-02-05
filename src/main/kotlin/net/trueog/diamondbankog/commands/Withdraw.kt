package net.trueog.diamondbankog.commands

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class Withdraw : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (sender !is Player) {
            sender.sendMessage("You can only execute this command as a player.")
            return true
        }

        if (!sender.hasPermission("diamondbank-og.withdraw")) {
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You do not have permission to use this command."))
            return true
        }

        if (args == null || args.isEmpty()) {
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You did not provide the amount of <aqua>Diamonds <red>that you want to withdraw."))
            return true
        }
        if (args.size != 1) {
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Please (only) provide the amount of <aqua>Diamonds <red>you want to withdraw. Either a number or \"all\"."))
            return true
        }

        var amount = -1
        if (args[0] != "all") {
            try {
                amount = args[0].toInt()
                if (amount <= 0) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You cannot withdraw a negative amount."))
                    return true
                }
            } catch (_: Exception) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Invalid argument."))
                return true
            }
        }

        GlobalScope.launch {
            val playerBalance = DiamondBankOG.postgreSQL.getPlayerBalance(sender.uniqueId)
            if (playerBalance == -1L) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to get your balance."))
                return@launch
            }

            if (amount > playerBalance) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Cannot withdraw <yellow>$amount <aqua>${if (amount == 1) "Diamond" else "Diamonds"} <red>because your bank only contains <yellow>$playerBalance <aqua>${if (playerBalance == 1L) "diamond" else "diamonds"}<red>."))
                return@launch
            }

            val inventoryCopy = sender.inventory
            val diamondItemStack = ItemStack(Material.DIAMOND, amount)
            val addItemMap = inventoryCopy.addItem(diamondItemStack)
            if (addItemMap.isNotEmpty()) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You don't have enough inventory space to withdraw <yellow>$amount <aqua>${if (amount == 1) "Diamond" else "Diamonds"}<red>."))
                return@launch
            }
            val error = DiamondBankOG.postgreSQL.setPlayerBalance(
                sender.uniqueId,
                playerBalance - if (amount == -1) playerBalance else amount.toLong()
            )
            if (error) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to withdraw."))
                return@launch
            }
            sender.inventory.addItem(diamondItemStack)
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <green>Successfully withdrew <yellow>$amount <aqua>${if (amount == 1) "Diamond" else "Diamonds"} <green>from your bank account."))
        }

        return true
    }
}