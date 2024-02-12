package net.trueog.diamondbankog

import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.User
import net.trueog.diamondbankog.Helper.PostgresFunction.SET_PLAYER_BALANCE
import net.trueog.diamondbankog.Helper.PostgresFunction.SUBTRACT_FROM_PLAYER_BALANCE
import net.trueog.diamondbankog.PostgreSQL.BalanceType.BANK_BALANCE
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import java.util.*

object Helper {
    enum class PostgresFunction(val string: String) {
        SET_PLAYER_BALANCE("setPlayerBalance"),
        ADD_TO_PLAYER_BALANCE("addToPlayerBalance"),
        SUBTRACT_FROM_PLAYER_BALANCE("subtractFromPlayerBalance")
    }

    suspend fun withdrawFromPlayer(player: Player, amount: Long): Long? {
        val somethingWentWrongMessage =
            DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong.")

        val playerBalance = DiamondBankOG.postgreSQL.getPlayerBalance(player.uniqueId, PostgreSQL.BalanceType.ALL)
        if (playerBalance.bankBalance == null || playerBalance.inventoryBalance == null || playerBalance.enderChestBalance == null) {
            player.sendMessage(somethingWentWrongMessage)
            return null
        }

        // Withdraw everything
        if (amount == -1L) {
            var error = DiamondBankOG.postgreSQL.subtractFromPlayerBalance(
                player.uniqueId,
                playerBalance.bankBalance,
                BANK_BALANCE
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    SUBTRACT_FROM_PLAYER_BALANCE, playerBalance.bankBalance, BANK_BALANCE,
                    playerBalance, "withdrawFromPlayer"
                )
                player.sendMessage(somethingWentWrongMessage)
                return null
            }

            error = player.inventory.withdraw(
                playerBalance.inventoryBalance,
                playerBalance
            )
            if (error) {
                player.sendMessage(somethingWentWrongMessage)
                return null
            }

            error = player.enderChest.withdraw(
                playerBalance.enderChestBalance,
                playerBalance
            )
            if (error) {
                player.sendMessage(somethingWentWrongMessage)
                return null
            }
            return playerBalance.bankBalance + playerBalance.inventoryBalance + playerBalance.enderChestBalance
        }

        if (amount > playerBalance.bankBalance + playerBalance.inventoryBalance + playerBalance.enderChestBalance) {
            player.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Cannot withdraw <yellow>$amount <aqua>${if (amount == 1L) "Diamond" else "Diamonds"} <red>because your bank only contains <yellow>${playerBalance.bankBalance + playerBalance.inventoryBalance} <aqua>${if (playerBalance.bankBalance + playerBalance.inventoryBalance == 1L) "Diamond" else "Diamonds"}<red>."))
            return null
        }

        if (amount <= playerBalance.bankBalance) {
            val error = DiamondBankOG.postgreSQL.subtractFromPlayerBalance(
                player.uniqueId,
                amount,
                BANK_BALANCE
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    SUBTRACT_FROM_PLAYER_BALANCE, amount, BANK_BALANCE,
                    playerBalance, "withdrawFromPlayer"
                )
                player.sendMessage(somethingWentWrongMessage)
                return null
            }
            return amount
        }

        if (amount <= playerBalance.bankBalance + playerBalance.inventoryBalance) {
            var error = DiamondBankOG.postgreSQL.subtractFromPlayerBalance(
                player.uniqueId,
                playerBalance.bankBalance,
                BANK_BALANCE
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    SUBTRACT_FROM_PLAYER_BALANCE, playerBalance.bankBalance, BANK_BALANCE,
                    playerBalance, "withdrawFromPlayer"
                )
                player.sendMessage(somethingWentWrongMessage)
                return null
            }

            error = player.inventory.withdraw(
                amount - playerBalance.bankBalance,
                playerBalance
            )
            if (error) {
                player.sendMessage(somethingWentWrongMessage)
                return null
            }
            return amount
        }

        var error = DiamondBankOG.postgreSQL.subtractFromPlayerBalance(
            player.uniqueId,
            playerBalance.bankBalance,
            BANK_BALANCE
        )
        if (error) {
            handleError(
                player.uniqueId,
                SUBTRACT_FROM_PLAYER_BALANCE, playerBalance.bankBalance, BANK_BALANCE,
                playerBalance, "withdrawFromPlayer"
            )
            player.sendMessage(somethingWentWrongMessage)
            return null
        }

        error = player.inventory.withdraw(playerBalance.inventoryBalance, playerBalance)
        if (error) {
            player.sendMessage(somethingWentWrongMessage)
            return null
        }

        error = player.enderChest.withdraw(
            amount - (playerBalance.bankBalance + playerBalance.inventoryBalance),
            playerBalance
        )
        if (error) {
            player.sendMessage(somethingWentWrongMessage)
            return null
        }

        return amount
    }

    suspend fun Inventory.withdraw(
        amount: Long,
        playerBalance: PostgreSQL.PlayerBalance
    ): Boolean {
        if (this.holder !is Player) return true

        val player = this.holder as Player

        DiamondBankOG.blockInventoryFor.add(player.uniqueId)
        val balance = if (this.type == InventoryType.PLAYER) {
            playerBalance.inventoryBalance!!
        } else {
            playerBalance.enderChestBalance!!
        }

        val balanceType = if (this.type == InventoryType.PLAYER) {
            PostgreSQL.BalanceType.INVENTORY_BALANCE
        } else {
            PostgreSQL.BalanceType.ENDER_CHEST_BALANCE
        }

        val inventoryDiamonds = this.countDiamonds()
        if (playerBalance.inventoryBalance != inventoryDiamonds) {
            val error = DiamondBankOG.postgreSQL.setPlayerBalance(
                player.uniqueId,
                balance - (amount - inventoryDiamonds),
                balanceType
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    SET_PLAYER_BALANCE,
                    amount,
                    balanceType,
                    playerBalance,
                    "withdrawFromInventory"
                )
                player.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong."))
                DiamondBankOG.blockInventoryFor.remove(player.uniqueId)
                return true
            }
        }

        val error = DiamondBankOG.postgreSQL.subtractFromPlayerBalance(
            player.uniqueId,
            amount,
            balanceType
        )
        if (error) {
            handleError(
                player.uniqueId,
                SUBTRACT_FROM_PLAYER_BALANCE,
                amount,
                balanceType,
                null,
                "withdrawFromInventory"
            )
            player.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong."))
            DiamondBankOG.blockInventoryFor.remove(player.uniqueId)
            return true
        }

        val removeMap = this.removeItem(ItemStack(Material.DIAMOND, amount.toInt()))
        if (removeMap.isNotEmpty()) {
            var toBeRemoved = removeMap[0]!!.amount
            val itemStacks = this.contents.filterNotNull().filter { it.type == Material.SHULKER_BOX }
            for (itemStack in itemStacks) {
                val blockStateMeta = (itemStack.itemMeta as BlockStateMeta)
                itemStack.itemMeta
                val shulkerBox = blockStateMeta.blockState as ShulkerBox
                val shulkerRemoveMap = shulkerBox.inventory.removeItem(ItemStack(Material.DIAMOND, toBeRemoved))

                blockStateMeta.blockState = shulkerBox
                itemStack.itemMeta = blockStateMeta

                if (shulkerRemoveMap.isNotEmpty()) {
                    toBeRemoved = -toBeRemoved - shulkerRemoveMap[0]!!.amount
                } else break
            }
        }
        DiamondBankOG.blockInventoryFor.remove(player.uniqueId)

        return false
    }

    fun handleError(
        uuid: UUID,
        function: PostgresFunction,
        amount: Long,
        type: PostgreSQL.BalanceType,
        playerBalance: PostgreSQL.PlayerBalance?,
        inFunction: String
    ) {
        DiamondBankOG.economyDisabled = true
        if (DiamondBankOG.sentryEnabled) {
            val sentryUser = User()
            sentryUser.id = uuid.toString()

            val sentryEvent = SentryEvent()
            sentryEvent.user = sentryUser
            sentryEvent.setExtra("Function", "${function.string}(amount = $amount, type = $type)")
            if (playerBalance != null) {
                if (playerBalance.bankBalance != null) sentryEvent.setExtra("Bank Balance", playerBalance.bankBalance)
                if (playerBalance.inventoryBalance != null) sentryEvent.setExtra(
                    "Inventory Balance",
                    playerBalance.inventoryBalance
                )
                if (playerBalance.enderChestBalance != null) sentryEvent.setExtra(
                    "Ender chest Balance",
                    playerBalance.enderChestBalance
                )
            }

            val message = Message()
            message.message = "${function.string} failed in $inFunction"
            sentryEvent.message = message

            Sentry.captureEvent(sentryEvent)
        }
    }

    fun Inventory.countDiamonds(): Long {
        val inventoryDiamonds = this.all(Material.DIAMOND).values.sumOf { it.amount }
        val shulkerBoxDiamonds = this.all(Material.SHULKER_BOX).values.sumOf { itemStack ->
            ((itemStack.itemMeta as BlockStateMeta).blockState as ShulkerBox).inventory.all(Material.DIAMOND).values.sumOf { it.amount }
        }
        return inventoryDiamonds.toLong() + shulkerBoxDiamonds.toLong()
    }
}