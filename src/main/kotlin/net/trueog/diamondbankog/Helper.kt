package net.trueog.diamondbankog

import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.User
import net.trueog.diamondbankog.PostgreSQL.BalanceType
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import java.util.*
import kotlin.math.ceil

object Helper {
    enum class PostgresFunction(val string: String) {
        SET_PLAYER_BALANCE("setPlayerBalance"),
        ADD_TO_PLAYER_BALANCE("addToPlayerBalance"),
        SUBTRACT_FROM_PLAYER_BALANCE("subtractFromPlayerBalance"),
        MIGRATE_FROM_OLD_DIAMOND_BANK("migrate")
    }

    suspend fun withdrawFromPlayer(player: Player, amount: Int): Int? {
        val somethingWentWrongMessage =
            DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong.")

        val playerBalance = DiamondBankOG.postgreSQL.getPlayerBalance(player.uniqueId, BalanceType.ALL)
        if (playerBalance.bankBalance == null || playerBalance.inventoryBalance == null || playerBalance.enderChestBalance == null) {
            player.sendMessage(somethingWentWrongMessage)
            return null
        }

        // Withdraw everything
        if (amount == -1) {
            var error = DiamondBankOG.postgreSQL.subtractFromPlayerBalance(
                player.uniqueId,
                playerBalance.bankBalance,
                BalanceType.BANK_BALANCE
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    PostgresFunction.SUBTRACT_FROM_PLAYER_BALANCE, playerBalance.bankBalance, BalanceType.BANK_BALANCE,
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
            player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Cannot withdraw <yellow>$amount <aqua>${if (amount == 1) "Diamond" else "Diamonds"} <red>because you only have <yellow>${playerBalance.bankBalance + playerBalance.inventoryBalance} <aqua>${if (playerBalance.bankBalance + playerBalance.inventoryBalance == 1) "Diamond" else "Diamonds"}<red>."))
            return null
        }

        if (amount <= playerBalance.bankBalance) {
            val error = DiamondBankOG.postgreSQL.subtractFromPlayerBalance(
                player.uniqueId,
                amount,
                BalanceType.BANK_BALANCE
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    PostgresFunction.SUBTRACT_FROM_PLAYER_BALANCE, amount, BalanceType.BANK_BALANCE,
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
                BalanceType.BANK_BALANCE
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    PostgresFunction.SUBTRACT_FROM_PLAYER_BALANCE, playerBalance.bankBalance, BalanceType.BANK_BALANCE,
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
            BalanceType.BANK_BALANCE
        )
        if (error) {
            handleError(
                player.uniqueId,
                PostgresFunction.SUBTRACT_FROM_PLAYER_BALANCE, playerBalance.bankBalance, BalanceType.BANK_BALANCE,
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

    private suspend fun Inventory.withdrawDiamondBlocks(
        player: Player,
        toBeRemoved: Int,
        balanceType: BalanceType,
        isShulkerBox: Boolean
    ): Int? {
        val diamondBlocks =
            this.contents.filterNotNull().filter { it.type == Material.DIAMOND_BLOCK }.sumOf { it.amount }
        if (diamondBlocks == 0) return 0

        val blocksNeeded = ceil(toBeRemoved.toDouble() / 9).toInt()
        val wholeBlocks = if (blocksNeeded > diamondBlocks) diamondBlocks else blocksNeeded
        val remainder = (toBeRemoved) % 9
        val change = if (remainder == 0) 0 else 9 - remainder

        this.removeItem(ItemStack(Material.DIAMOND_BLOCK, wholeBlocks))

        val emptySlots = this.storageContents.filter { it == null }.size * 64
        val leftOverSpace = this.storageContents.filterNotNull().filter { it.type == Material.DIAMOND }
            .sumOf { 64 - it.amount }

        if (change > (emptySlots + leftOverSpace)) {
            if (isShulkerBox) {
                val emptySlotsInInventory = player.inventory.storageContents.filter { it == null }.size * 64
                val leftOverSpaceInInventory =
                    player.inventory.storageContents.filterNotNull().filter { it.type == Material.DIAMOND }
                        .sumOf { 64 - it.amount }

                if (change > (emptySlotsInInventory + leftOverSpaceInInventory)) {
                    player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: You don't have enough inventory space in your shulker box to store <yellow>$change <aqua>${if (change == 1) "Diamond" else "Diamonds"} <reset>of change from converting diamond blocks into <aqua>Diamonds<reset>, so the <aqua>${if (change == 1) "Diamond" else "Diamonds"} <reset>has been deposited into your bank."))
                    val error = DiamondBankOG.postgreSQL.addToPlayerBalance(
                        player.uniqueId,
                        change,
                        BalanceType.BANK_BALANCE
                    )
                    if (error) {
                        handleError(
                            player.uniqueId,
                            PostgresFunction.ADD_TO_PLAYER_BALANCE,
                            change,
                            balanceType,
                            null,
                            "Inventory.withdrawDiamondBlocks"
                        )
                        player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong."))
                        DiamondBankOG.blockInventoryFor.remove(player.uniqueId)
                        return null
                    }
                } else {
                    player.inventory.addItem(ItemStack(Material.DIAMOND, change))
                    player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: You don't have enough inventory space in your shulker box to store <yellow>$change <aqua>${if (change == 1) "Diamond" else "Diamonds"} <reset>of change from converting diamond blocks into <aqua>Diamonds<reset>, so the <aqua>${if (change == 1) "Diamond" else "Diamonds"} <reset>have been added to your inventory."))
                }
            } else {
                player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: You don't have enough inventory space to store <yellow>$change <aqua>${if (change == 1) "Diamond" else "Diamonds"} <reset>of change from converting diamond blocks into <aqua>Diamonds<reset>, so the <aqua>${if (change == 1) "Diamond" else "Diamonds"} <reset>has been deposited into your bank."))
                val error = DiamondBankOG.postgreSQL.addToPlayerBalance(
                    player.uniqueId,
                    change,
                    BalanceType.BANK_BALANCE
                )
                if (error) {
                    handleError(
                        player.uniqueId,
                        PostgresFunction.ADD_TO_PLAYER_BALANCE,
                        change,
                        balanceType,
                        null,
                        "Inventory.withdrawDiamondBlocks"
                    )
                    player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong."))
                    DiamondBankOG.blockInventoryFor.remove(player.uniqueId)
                    return null
                }
            }
        } else {
            this.addItem(ItemStack(Material.DIAMOND, change))
        }

        return if (blocksNeeded > diamondBlocks) diamondBlocks else (wholeBlocks * 9) - change
    }

    suspend fun Inventory.withdraw(
        amount: Int,
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
            BalanceType.INVENTORY_BALANCE
        } else {
            BalanceType.ENDER_CHEST_BALANCE
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
                    PostgresFunction.SET_PLAYER_BALANCE,
                    amount,
                    balanceType,
                    playerBalance,
                    "Inventory.withdraw"
                )
                player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong."))
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
                PostgresFunction.SUBTRACT_FROM_PLAYER_BALANCE,
                amount,
                balanceType,
                playerBalance,
                "Inventory.withdraw"
            )
            player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong."))
            DiamondBankOG.blockInventoryFor.remove(player.uniqueId)
            return true
        }

        val removeMap = this.removeItem(ItemStack(Material.DIAMOND, amount))
        if (removeMap.isNotEmpty()) {
            var toBeRemoved = removeMap[0]!!.amount

            val removed = this.withdrawDiamondBlocks(player, toBeRemoved, balanceType, false) ?: return true
            toBeRemoved -= removed

            if (toBeRemoved != 0) {
                val itemStacks = this.contents.filterNotNull().filter { it.type == Material.SHULKER_BOX }
                for (itemStack in itemStacks) {
                    val blockStateMeta = (itemStack.itemMeta as BlockStateMeta)
                    itemStack.itemMeta
                    val shulkerBox = blockStateMeta.blockState as ShulkerBox
                    val shulkerRemoveMap = shulkerBox.inventory.removeItem(ItemStack(Material.DIAMOND, toBeRemoved))

                    blockStateMeta.blockState = shulkerBox
                    itemStack.itemMeta = blockStateMeta

                    toBeRemoved -= if (shulkerRemoveMap.isEmpty()) toBeRemoved else toBeRemoved - shulkerRemoveMap[0]!!.amount
                    if (toBeRemoved == 0) break
                }

                if (toBeRemoved != 0) {
                    for (itemStack in itemStacks) {
                        val blockStateMeta = (itemStack.itemMeta as BlockStateMeta)
                        itemStack.itemMeta
                        val shulkerBox = blockStateMeta.blockState as ShulkerBox

                        val shulkerRemoved =
                            shulkerBox.inventory.withdrawDiamondBlocks(player, toBeRemoved, balanceType, true)
                                ?: return true

                        blockStateMeta.blockState = shulkerBox
                        itemStack.itemMeta = blockStateMeta

                        toBeRemoved -= shulkerRemoved
                        if (toBeRemoved == 0) break
                    }
                }
            }
        }
        DiamondBankOG.blockInventoryFor.remove(player.uniqueId)

        return false
    }

    fun Inventory.countDiamonds(): Int {
        val inventoryDiamonds = this.all(Material.DIAMOND).values.sumOf { it.amount }
        val shulkerBoxDiamonds = this.all(Material.SHULKER_BOX).values.sumOf { itemStack ->
            ((itemStack.itemMeta as BlockStateMeta).blockState as ShulkerBox).inventory.all(Material.DIAMOND).values.sumOf { it.amount }
        }
        val inventoryDiamondBlocks = this.all(Material.DIAMOND_BLOCK).values.sumOf { it.amount * 9 }
        val shulkerBoxDiamondBlocks = this.all(Material.SHULKER_BOX).values.sumOf { itemStack ->
            ((itemStack.itemMeta as BlockStateMeta).blockState as ShulkerBox).inventory.all(Material.DIAMOND_BLOCK).values.sumOf { it.amount * 9 }
        }
        return inventoryDiamonds + shulkerBoxDiamonds + inventoryDiamondBlocks + shulkerBoxDiamondBlocks
    }

    fun handleError(
        uuid: UUID,
        function: PostgresFunction,
        amount: Int,
        balanceType: BalanceType,
        playerBalance: PostgreSQL.PlayerBalance?,
        inFunction: String
    ) {
        DiamondBankOG.economyDisabled = true
        if (DiamondBankOG.sentryEnabled) {
            val sentryUser = User()
            sentryUser.id = uuid.toString()

            val sentryEvent = SentryEvent()
            sentryEvent.user = sentryUser
            sentryEvent.setExtra("Function", "${function.string}(amount = $amount, type = $balanceType)")
            if (playerBalance != null) {
                if (playerBalance.bankBalance != null) sentryEvent.setExtra("Bank Balance", playerBalance.bankBalance)
                if (playerBalance.inventoryBalance != null) sentryEvent.setExtra(
                    "Inventory Balance",
                    playerBalance.inventoryBalance
                )
                if (playerBalance.enderChestBalance != null) sentryEvent.setExtra(
                    "Ender Chest Balance",
                    playerBalance.enderChestBalance
                )
            }

            val message = Message()
            message.message = "${function.string} failed in $inFunction"
            sentryEvent.message = message

            Sentry.captureEvent(sentryEvent)
        }
        DiamondBankOG.plugin.logger.severe(
            """
            ${function.string} failed in $inFunction
            Player UUID: $uuid
            Function: ${function.string}(amount = $amount, type = $balanceType)
            ${
                if (playerBalance != null) {
                    if (playerBalance.bankBalance != null) "Bank Balance: ${playerBalance.bankBalance}" else ""
                } else ""
            }
            ${
                if (playerBalance != null) {
                    if (playerBalance.inventoryBalance != null) "Inventory Balance: ${playerBalance.inventoryBalance}" else ""
                } else ""
            }
            ${
                if (playerBalance != null) {
                    if (playerBalance.enderChestBalance != null) "Ender Chest Balance: ${playerBalance.enderChestBalance}" else ""
                } else ""
            }
        """.trimIndent()
        )
    }
}
