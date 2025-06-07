package net.trueog.diamondbankog

import net.trueog.diamondbankog.InventoryExtensions.withdraw
import net.trueog.diamondbankog.PostgreSQL.PlayerShards
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.entity.Player
import java.util.*
import kotlin.math.floor

object Helper {
    /**
     * @return The amount of not removed shards, -1 if error
     */
    suspend fun withdrawFromPlayer(player: Player, shards: Int): Int {
        val playerShards = DiamondBankOG.postgreSQL.getPlayerShards(player.uniqueId, ShardType.ALL)
        if (playerShards.shardsInBank == null || playerShards.shardsInInventory == null || playerShards.shardsInEnderChest == null) {
            return -1
        }

        // Withdraw everything
        if (shards == -1) {
            val error = DiamondBankOG.postgreSQL.subtractFromPlayerShards(
                player.uniqueId,
                playerShards.shardsInBank,
                ShardType.BANK
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    shards,
                    playerShards
                )
                player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."))
                return -1
            }

            val notRemovedInventory = player.inventory.withdraw(
                playerShards.shardsInInventory
            )

            val notRemovedEnderChest = player.enderChest.withdraw(
                playerShards.shardsInEnderChest
            )
            return notRemovedInventory + notRemovedEnderChest
        }

        if (shards > playerShards.shardsInBank + playerShards.shardsInInventory + playerShards.shardsInEnderChest) {
            val diamonds = String.format("%.1f", floor((shards / 9.0) * 10) / 10.0)
            val totalDiamonds = String.format(
                "%.1f",
                floor(((playerShards.shardsInBank + playerShards.shardsInInventory + playerShards.shardsInEnderChest) / 9.0) * 10) / 10.0
            )
            player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Cannot use <yellow>$diamonds <aqua>${if (diamonds == "1.0") "Diamond" else "Diamonds"} <red>in a transaction because you only have <yellow>$totalDiamonds <aqua>${if (totalDiamonds == "1.0") "Diamond" else "Diamonds"}<red>."))
            return -1
        }

        if (shards <= playerShards.shardsInBank) {
            val error = DiamondBankOG.postgreSQL.subtractFromPlayerShards(
                player.uniqueId,
                shards,
                ShardType.BANK
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    shards,
                    playerShards
                )
                player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."))
                return -1
            }
            return 0
        }

        if (shards <= playerShards.shardsInBank + playerShards.shardsInInventory) {
            val error = DiamondBankOG.postgreSQL.subtractFromPlayerShards(
                player.uniqueId,
                playerShards.shardsInBank,
                ShardType.BANK
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    shards,
                    playerShards
                )
                player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."))
                return -1
            }

            val notRemoved = player.inventory.withdraw(
                shards - playerShards.shardsInBank
            )
            return notRemoved
        }

        val error = DiamondBankOG.postgreSQL.subtractFromPlayerShards(
            player.uniqueId,
            playerShards.shardsInBank,
            ShardType.BANK
        )
        if (error) {
            handleError(
                player.uniqueId,
                shards,
                playerShards
            )
            player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."))
            return -1
        }

        val notRemovedInventory = player.inventory.withdraw(playerShards.shardsInInventory)

        val notRemovedEnderChest = player.enderChest.withdraw(
            shards - (playerShards.shardsInBank + playerShards.shardsInInventory)
        )

        return notRemovedInventory + notRemovedEnderChest
    }

    class EconomyException(message: String) : Exception(message)

    /**
     * Handles the error by throwing, implicitly disables the economy
     */
    fun handleError(
        uuid: UUID,
        expectedMutatedShards: Int,
        playerShards: PlayerShards?
    ) {
        DiamondBankOG.economyDisabled = true

        throw EconomyException(
            """

            Player UUID: $uuid
            Expected Mutated Shards = $expectedMutatedShards${
                if (playerShards != null) {
                    if (playerShards.shardsInBank != -1) "Player Bank Balance: ${playerShards.shardsInBank}" else ""
                } else ""
            }${
                if (playerShards != null) {
                    if (playerShards.shardsInInventory != -1) "Player Inventory Balance: ${playerShards.shardsInInventory}" else ""
                } else ""
            }${
                if (playerShards != null) {
                    if (playerShards.shardsInEnderChest != -1) "Player Ender Chest Balance: ${playerShards.shardsInEnderChest}" else ""
                } else ""
            }
        """.trimIndent()
        )
    }
}