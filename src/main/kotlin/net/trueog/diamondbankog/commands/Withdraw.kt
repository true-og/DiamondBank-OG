package net.trueog.diamondbankog.commands

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.PostgreSQL
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class Withdraw : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        GlobalScope.launch {
            if (sender !is Player) {
                sender.sendMessage("You can only execute this command as a player.")
                return@launch
            }

            if (!sender.hasPermission("diamondbank-og.withdraw")) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You do not have permission to use this command."))
                return@launch
            }

            if (args == null || args.isEmpty()) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You did not provide the amount of <aqua>Diamonds <red>that you want to withdraw."))
                return@launch
            }
            if (args.size != 1) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Please (only) provide the amount of <aqua>Diamonds <red>you want to withdraw. Either a number or \"all\"."))
                return@launch
            }

            var amount = -1
            if (args[0] != "all") {
                try {
                    amount = args[0].toInt()
                    if (amount <= 0) {
                        sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You cannot withdraw a negative amount."))
                        return@launch
                    }
                } catch (_: Exception) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Invalid argument."))
                    return@launch
                }
            }

            val playerBalance =
                DiamondBankOG.postgreSQL.getPlayerBalance(sender.uniqueId, PostgreSQL.BalanceType.BANK_BALANCE)
            if (playerBalance.bankBalance == null) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to get your balance."))
                return@launch
            }

            if (amount > playerBalance.bankBalance) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Cannot withdraw <yellow>$amount <aqua>${if (amount == 1) "Diamond" else "Diamonds"} <red>because your bank only contains <yellow>$playerBalance <aqua>${if (playerBalance.bankBalance == 1L) "diamond" else "diamonds"}<red>."))
                return@launch
            }

            DiamondBankOG.blockInventoryFor.add(sender.uniqueId)
            val inventoryCopy: Inventory = Bukkit.createInventory(null, InventoryType.PLAYER)
            inventoryCopy.contents = sender.inventory.contents
            val diamondItemStack = ItemStack(Material.DIAMOND, amount)
            val addItemMap = inventoryCopy.addItem(diamondItemStack)
            if (addItemMap.isNotEmpty()) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You don't have enough inventory space to withdraw <yellow>$amount <aqua>${if (amount == 1) "Diamond" else "Diamonds"}<red>."))
                DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
                return@launch
            }

            val error = DiamondBankOG.postgreSQL.subtractFromPlayerBalance(
                sender.uniqueId,
                if (amount == -1) playerBalance.bankBalance else amount.toLong(),
                PostgreSQL.BalanceType.BANK_BALANCE
            )
            if (error) {
                // TODO: Houston, we have an issue
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to withdraw."))
                DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
                return@launch
            }

            sender.inventory.addItem(diamondItemStack)
            DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <green>Successfully withdrew <yellow>$amount <aqua>${if (amount == 1) "Diamond" else "Diamonds"} <green>from your bank account."))
        }
        return true
    }
}