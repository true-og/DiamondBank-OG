package net.trueog.diamondbankog

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

object Helper {
    suspend fun withdrawFromPlayer(sender: Player, amount: Long): Long? {
        val somethingWentWrongMessage =
            DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong.")

        val senderBalance = DiamondBankOG.postgreSQL.getPlayerBalance(sender.uniqueId, PostgreSQL.BalanceType.ALL)
        if (senderBalance.bankBalance == null || senderBalance.inventoryBalance == null || senderBalance.enderChestBalance == null) {
            sender.sendMessage(somethingWentWrongMessage)
            return null
        }

        // Withdraw everything
        if (amount == -1L) {
            var error = DiamondBankOG.postgreSQL.subtractFromPlayerBalance(
                sender.uniqueId,
                senderBalance.bankBalance,
                PostgreSQL.BalanceType.BANK_BALANCE
            )
            if (error) {
                // TODO: Houston, we have an issue
                sender.sendMessage(somethingWentWrongMessage)
                return null
            }

            error = withdrawFromInventory(sender, senderBalance.inventoryBalance, WithdrawType.INVENTORY)
            if (error) {
                sender.sendMessage(somethingWentWrongMessage)
                return null
            }

            error = withdrawFromInventory(sender, senderBalance.enderChestBalance, WithdrawType.ENDER_CHEST)
            if (error) {
                sender.sendMessage(somethingWentWrongMessage)
                return null
            }
            return senderBalance.bankBalance + senderBalance.inventoryBalance + senderBalance.enderChestBalance
        }

        if (amount > senderBalance.bankBalance + senderBalance.inventoryBalance + senderBalance.enderChestBalance) {
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Cannot withdraw <yellow>$amount <aqua>${if (amount == 1L) "Diamond" else "Diamonds"} <red>because your bank only contains <yellow>${senderBalance.bankBalance + senderBalance.inventoryBalance} <aqua>${if (senderBalance.bankBalance + senderBalance.inventoryBalance == 1L) "Diamond" else "Diamonds"}<red>."))
            return null
        }

        if (amount <= senderBalance.bankBalance) {
            val error = DiamondBankOG.postgreSQL.subtractFromPlayerBalance(
                sender.uniqueId,
                amount,
                PostgreSQL.BalanceType.BANK_BALANCE
            )
            if (error) {
                // TODO: Houston, we have an issue
                sender.sendMessage(somethingWentWrongMessage)
                return null
            }
            return amount
        }

        if (amount <= senderBalance.bankBalance + senderBalance.inventoryBalance) {
            var error = DiamondBankOG.postgreSQL.subtractFromPlayerBalance(
                sender.uniqueId,
                senderBalance.bankBalance,
                PostgreSQL.BalanceType.BANK_BALANCE
            )
            if (error) {
                // TODO: Houston, we have an issue
                sender.sendMessage(somethingWentWrongMessage)
                return null
            }

            error = withdrawFromInventory(sender, amount - senderBalance.bankBalance, WithdrawType.INVENTORY)
            if (error) {
                sender.sendMessage(somethingWentWrongMessage)
                return null
            }
            return amount
        }

        var error = DiamondBankOG.postgreSQL.subtractFromPlayerBalance(
            sender.uniqueId,
            senderBalance.bankBalance,
            PostgreSQL.BalanceType.BANK_BALANCE
        )
        if (error) {
            // TODO: Houston, we have an issue
            sender.sendMessage(somethingWentWrongMessage)
            return null
        }

        error = withdrawFromInventory(sender, senderBalance.inventoryBalance, WithdrawType.INVENTORY)
        if (error) {
            sender.sendMessage(somethingWentWrongMessage)
            return null
        }

        error = withdrawFromInventory(
            sender,
            amount - (senderBalance.bankBalance + senderBalance.inventoryBalance),
            WithdrawType.ENDER_CHEST
        )
        if (error) {
            sender.sendMessage(somethingWentWrongMessage)
            return null
        }

        return amount
    }

    private enum class WithdrawType {
        ENDER_CHEST, INVENTORY
    }

    private suspend fun withdrawFromInventory(player: Player, amount: Long, type: WithdrawType): Boolean {
        DiamondBankOG.blockInventoryFor.add(player.uniqueId)
        val inventory = if (type == WithdrawType.INVENTORY) {
            player.inventory
        } else {
            player.enderChest
        }

        val inventoryType = if (type == WithdrawType.INVENTORY) {
            InventoryType.PLAYER
        } else {
            InventoryType.ENDER_CHEST
        }

        val balanceType = if (type == WithdrawType.INVENTORY) {
            PostgreSQL.BalanceType.INVENTORY_BALANCE
        } else {
            PostgreSQL.BalanceType.ENDER_CHEST_BALANCE
        }

        val inventoryCopy: Inventory = Bukkit.createInventory(null, inventoryType)
        inventoryCopy.contents = inventory.contents
        val removeItemMap = inventoryCopy.removeItem(ItemStack(Material.DIAMOND, amount.toInt()))
        if (removeItemMap.isNotEmpty()) {
            val playerBalance = DiamondBankOG.postgreSQL.getPlayerBalance(player.uniqueId, balanceType)
            val balance = if (balanceType == PostgreSQL.BalanceType.INVENTORY_BALANCE) {
                playerBalance.inventoryBalance
            } else playerBalance.enderChestBalance

            if (balance == null) {
                // TODO: Houston, we have an issue
                DiamondBankOG.blockInventoryFor.remove(player.uniqueId)
                return true
            }

            DiamondBankOG.postgreSQL.setPlayerBalance(
                player.uniqueId,
                balance - removeItemMap.size.toLong(),
                balanceType
            )
        }


        val error = DiamondBankOG.postgreSQL.subtractFromPlayerBalance(
            player.uniqueId,
            amount,
            balanceType
        )
        if (error) {
            // TODO: Houston, we have an issue
            DiamondBankOG.blockInventoryFor.remove(player.uniqueId)
            return true
        }

        inventory.removeItem(ItemStack(Material.DIAMOND, amount.toInt()))
        DiamondBankOG.blockInventoryFor.remove(player.uniqueId)

        return false
    }
}