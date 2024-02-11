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

class Deposit : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        GlobalScope.launch {
            if (sender !is Player) {
                sender.sendMessage("You can only execute this command as a player.")
                return@launch
            }

            if (!sender.hasPermission("diamondbank-og.deposit")) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You do not have permission to use this command."))
                return@launch
            }

            if (args == null || args.isEmpty()) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You did not provide the amount of <aqua>dDiamonds <red>that you want to deposit."))
                return@launch
            }
            if (args.size != 1) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Please (only) provide the amount of <aqua>dDiamonds <red>you want to deposit. Either a number or \"all\"."))
                return@launch
            }

//            DiamondBankOG.blockFor.add(sender.uniqueId)
//            val allDiamonds = sender.inventory.all(Material.DIAMOND).values.sumOf { it.amount }
            val playerBalance =
                DiamondBankOG.postgreSQL.getPlayerBalance(sender.uniqueId, PostgreSQL.BalanceType.INVENTORY_BALANCE)
            if (playerBalance.inventoryBalance == null) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to get your balance."))
                return@launch
            }
            if (playerBalance.inventoryBalance == 0L) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You don't have any <aqua>Diamonds <red>to deposit."))
                return@launch
            }

            var diamondsToDeposit = playerBalance.inventoryBalance

            if (args[0] != "all") {
                val amount: Long
                try {
                    amount = args[0].toLong()
                    if (amount <= 0) {
                        sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You cannot deposit a negative amount."))
                        return@launch
                    }
                } catch (_: Exception) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Invalid argument."))
                    return@launch
                }

                if (amount > playerBalance.inventoryBalance) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You do not have <yellow>$amount <aqua>${if (amount == 1L) "Diamond" else "Diamonds"} <red>in your inventory."))
                    return@launch
                }
                diamondsToDeposit = amount
            }

            DiamondBankOG.blockInventoryFor.add(sender.uniqueId)
            val inventoryCopy: Inventory = Bukkit.createInventory(null, InventoryType.PLAYER)
            inventoryCopy.contents = sender.inventory.contents
            val removeItemMap = inventoryCopy.removeItem(ItemStack(Material.DIAMOND, diamondsToDeposit.toInt()))
            if (removeItemMap.isNotEmpty()) {
                DiamondBankOG.postgreSQL.setPlayerBalance(
                    sender.uniqueId,
                    playerBalance.inventoryBalance - removeItemMap.size.toLong(),
                    PostgreSQL.BalanceType.INVENTORY_BALANCE
                )
            }

            var error = DiamondBankOG.postgreSQL.subtractFromPlayerBalance(
                sender.uniqueId,
                diamondsToDeposit,
                PostgreSQL.BalanceType.INVENTORY_BALANCE
            )
            if (error) {
                // TODO: Houston, we have an issue
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to deposit."))
                DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
                return@launch
            }

            error = DiamondBankOG.postgreSQL.addToPlayerBalance(
                sender.uniqueId,
                diamondsToDeposit,
                PostgreSQL.BalanceType.BANK_BALANCE
            )
            if (error) {
                // TODO: Houston, we have an issue
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to deposit."))
                DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
                return@launch
            }

            sender.inventory.removeItem(ItemStack(Material.DIAMOND, diamondsToDeposit.toInt()))
            DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <green>Successfully deposited <yellow>$diamondsToDeposit <aqua>${if (diamondsToDeposit == 1L) "Diamond" else "Diamonds"} <green>into your bank account."))
        }
        return true
    }
}