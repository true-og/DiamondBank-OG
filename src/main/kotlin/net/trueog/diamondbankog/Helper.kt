package net.trueog.diamondbankog

import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.User
import net.trueog.diamondbankog.PostgreSQL.DiamondType
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
        SUBTRACT_FROM_PLAYER_BALANCE("subtractFromPlayerBalance")
    }

    suspend fun withdrawFromPlayer(player: Player, amount: Int): Int? {
        val somethingWentWrongMessage =
            DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong.")

        val playerBalance = DiamondBankOG.postgreSQL.getPlayerDiamonds(player.uniqueId, DiamondType.ALL)
        if (playerBalance.bankDiamonds == null || playerBalance.inventoryDiamonds == null || playerBalance.enderChestDiamonds == null) {
            player.sendMessage(somethingWentWrongMessage)
            return null
        }

        // Withdraw everything
        if (amount == -1) {
            var error = DiamondBankOG.postgreSQL.subtractFromPlayerDiamonds(
                player.uniqueId,
                playerBalance.bankDiamonds,
                DiamondType.BANK
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    PostgresFunction.SUBTRACT_FROM_PLAYER_BALANCE, playerBalance.bankDiamonds, DiamondType.BANK,
                    playerBalance, "withdrawFromPlayer"
                )
                player.sendMessage(somethingWentWrongMessage)
                return null
            }

            error = player.inventory.withdraw(
                playerBalance.inventoryDiamonds,
                playerBalance
            )
            if (error) {
                player.sendMessage(somethingWentWrongMessage)
                return null
            }

            error = player.enderChest.withdraw(
                playerBalance.enderChestDiamonds,
                playerBalance
            )
            if (error) {
                player.sendMessage(somethingWentWrongMessage)
                return null
            }
            return playerBalance.bankDiamonds + playerBalance.inventoryDiamonds + playerBalance.enderChestDiamonds
        }

        if (amount > playerBalance.bankDiamonds + playerBalance.inventoryDiamonds + playerBalance.enderChestDiamonds) {
            player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Cannot withdraw <yellow>$amount <aqua>${if (amount == 1) "Diamond" else "Diamonds"} <red>because you only have <yellow>${playerBalance.bankDiamonds + playerBalance.inventoryDiamonds} <aqua>${if (playerBalance.bankDiamonds + playerBalance.inventoryDiamonds == 1) "Diamond" else "Diamonds"}<red>."))
            return null
        }

        if (amount <= playerBalance.bankDiamonds) {
            val error = DiamondBankOG.postgreSQL.subtractFromPlayerDiamonds(
                player.uniqueId,
                amount,
                DiamondType.BANK
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    PostgresFunction.SUBTRACT_FROM_PLAYER_BALANCE, amount, DiamondType.BANK,
                    playerBalance, "withdrawFromPlayer"
                )
                player.sendMessage(somethingWentWrongMessage)
                return null
            }
            return amount
        }

        if (amount <= playerBalance.bankDiamonds + playerBalance.inventoryDiamonds) {
            var error = DiamondBankOG.postgreSQL.subtractFromPlayerDiamonds(
                player.uniqueId,
                playerBalance.bankDiamonds,
                DiamondType.BANK
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    PostgresFunction.SUBTRACT_FROM_PLAYER_BALANCE, playerBalance.bankDiamonds, DiamondType.BANK,
                    playerBalance, "withdrawFromPlayer"
                )
                player.sendMessage(somethingWentWrongMessage)
                return null
            }

            error = player.inventory.withdraw(
                amount - playerBalance.bankDiamonds,
                playerBalance
            )
            if (error) {
                player.sendMessage(somethingWentWrongMessage)
                return null
            }
            return amount
        }

        var error = DiamondBankOG.postgreSQL.subtractFromPlayerDiamonds(
            player.uniqueId,
            playerBalance.bankDiamonds,
            DiamondType.BANK
        )
        if (error) {
            handleError(
                player.uniqueId,
                PostgresFunction.SUBTRACT_FROM_PLAYER_BALANCE, playerBalance.bankDiamonds, DiamondType.BANK,
                playerBalance, "withdrawFromPlayer"
            )
            player.sendMessage(somethingWentWrongMessage)
            return null
        }

        error = player.inventory.withdraw(playerBalance.inventoryDiamonds, playerBalance)
        if (error) {
            player.sendMessage(somethingWentWrongMessage)
            return null
        }

        error = player.enderChest.withdraw(
            amount - (playerBalance.bankDiamonds + playerBalance.inventoryDiamonds),
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
        diamondType: DiamondType,
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
                    val error = DiamondBankOG.postgreSQL.addToPlayerDiamonds(
                        player.uniqueId,
                        change,
                        DiamondType.BANK
                    )
                    if (error) {
                        handleError(
                            player.uniqueId,
                            PostgresFunction.ADD_TO_PLAYER_BALANCE,
                            change,
                            diamondType,
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
                val error = DiamondBankOG.postgreSQL.addToPlayerDiamonds(
                    player.uniqueId,
                    change,
                    DiamondType.BANK
                )
                if (error) {
                    handleError(
                        player.uniqueId,
                        PostgresFunction.ADD_TO_PLAYER_BALANCE,
                        change,
                        diamondType,
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
        playerDiamonds: PostgreSQL.PlayerDiamonds
    ): Boolean {
        if (this.holder !is Player) return true

        val player = this.holder as Player
        DiamondBankOG.blockInventoryFor.add(player.uniqueId)

        val balance = if (this.type == InventoryType.PLAYER) {
            playerDiamonds.inventoryDiamonds!!
        } else {
            playerDiamonds.enderChestDiamonds!!
        }

        val diamondType = if (this.type == InventoryType.PLAYER) {
            DiamondType.INVENTORY
        } else {
            DiamondType.ENDER_CHEST
        }

        val inventoryDiamonds = this.countDiamonds()
        if (playerDiamonds.inventoryDiamonds != inventoryDiamonds) {
            val error = DiamondBankOG.postgreSQL.setPlayerDiamonds(
                player.uniqueId,
                balance - (amount - inventoryDiamonds),
                diamondType
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    PostgresFunction.SET_PLAYER_BALANCE,
                    amount,
                    diamondType,
                    playerDiamonds,
                    "Inventory.withdraw"
                )
                player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong."))
                DiamondBankOG.blockInventoryFor.remove(player.uniqueId)
                return true
            }
        }

        val error = DiamondBankOG.postgreSQL.subtractFromPlayerDiamonds(
            player.uniqueId,
            amount,
            diamondType
        )
        if (error) {
            handleError(
                player.uniqueId,
                PostgresFunction.SUBTRACT_FROM_PLAYER_BALANCE,
                amount,
                diamondType,
                playerDiamonds,
                "Inventory.withdraw"
            )
            player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong."))
            DiamondBankOG.blockInventoryFor.remove(player.uniqueId)
            return true
        }

        val removeMap = this.removeItem(ItemStack(Material.DIAMOND, amount))
        if (removeMap.isNotEmpty()) {
            var toBeRemoved = removeMap[0]!!.amount

            val removed = this.withdrawDiamondBlocks(player, toBeRemoved, diamondType, false) ?: return true
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
                            shulkerBox.inventory.withdrawDiamondBlocks(player, toBeRemoved, diamondType, true)
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
        diamondType: DiamondType,
        playerDiamonds: PostgreSQL.PlayerDiamonds?,
        inFunction: String
    ) {
        DiamondBankOG.economyDisabled = true
        if (DiamondBankOG.sentryEnabled) {
            val sentryUser = User()
            sentryUser.id = uuid.toString()

            val sentryEvent = SentryEvent()
            sentryEvent.user = sentryUser
            sentryEvent.setExtra("Function", "${function.string}(amount = $amount, type = $diamondType)")
            if (playerDiamonds != null) {
                if (playerDiamonds.bankDiamonds != null) sentryEvent.setExtra("Bank Balance", playerDiamonds.bankDiamonds)
                if (playerDiamonds.inventoryDiamonds != null) sentryEvent.setExtra(
                    "Inventory Balance",
                    playerDiamonds.inventoryDiamonds
                )
                if (playerDiamonds.enderChestDiamonds != null) sentryEvent.setExtra(
                    "Ender Chest Balance",
                    playerDiamonds.enderChestDiamonds
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
            Function: ${function.string}(amount = $amount, type = $diamondType)
            ${
                if (playerDiamonds != null) {
                    if (playerDiamonds.bankDiamonds != null) "Bank Balance: ${playerDiamonds.bankDiamonds}" else ""
                } else ""
            }
            ${
                if (playerDiamonds != null) {
                    if (playerDiamonds.inventoryDiamonds != null) "Inventory Balance: ${playerDiamonds.inventoryDiamonds}" else ""
                } else ""
            }
            ${
                if (playerDiamonds != null) {
                    if (playerDiamonds.enderChestDiamonds != null) "Ender Chest Balance: ${playerDiamonds.enderChestDiamonds}" else ""
                } else ""
            }
        """.trimIndent()
        )
    }
}