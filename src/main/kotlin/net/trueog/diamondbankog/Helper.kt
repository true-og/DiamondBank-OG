package net.trueog.diamondbankog

import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.User
import net.trueog.diamondbankog.InventoryExtensions.withdraw
import net.trueog.diamondbankog.PostgreSQL.PlayerShards
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.entity.Player
import java.util.*

object Helper {
    enum class PostgresFunction(val string: String) {
        SET_PLAYER_SHARDS("setPlayerShards"),
        ADD_TO_PLAYER_SHARDS("addToPlayerShards"),
        SUBTRACT_FROM_PLAYER_SHARDS("subtractFromShards"),
        OTHER("other")
    }

    suspend fun withdrawFromPlayer(player: Player, shards: Int): Int? {
        val somethingWentWrongMessage =
            DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong.")

        val playerShards = DiamondBankOG.postgreSQL.getPlayerShards(player.uniqueId, ShardType.ALL)
        if (playerShards.shardsInBank == null || playerShards.shardsInInventory == null || playerShards.shardsInEnderChest == null) {
            player.sendMessage(somethingWentWrongMessage)
            return null
        }

        // Withdraw everything
        if (shards == -1) {
            var error = DiamondBankOG.postgreSQL.subtractFromPlayerShards(
                player.uniqueId,
                playerShards.shardsInBank,
                ShardType.BANK
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    PostgresFunction.SUBTRACT_FROM_PLAYER_SHARDS, playerShards.shardsInBank, ShardType.BANK,
                    playerShards, "withdrawFromPlayer"
                )
                player.sendMessage(somethingWentWrongMessage)
                return null
            }

            error = player.inventory.withdraw(
                playerShards.shardsInInventory
            )
            if (error) {
                player.sendMessage(somethingWentWrongMessage)
                return null
            }

            error = player.enderChest.withdraw(
                playerShards.shardsInEnderChest
            )
            if (error) {
                player.sendMessage(somethingWentWrongMessage)
                return null
            }
            return playerShards.shardsInBank + playerShards.shardsInInventory + playerShards.shardsInEnderChest
        }

        if (shards > playerShards.shardsInBank + playerShards.shardsInInventory + playerShards.shardsInEnderChest) {
            player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Cannot withdraw <yellow>$shards <aqua>${if (shards == 1) "Diamond" else "Diamonds"} <red>because you only have <yellow>${playerShards.shardsInBank + playerShards.shardsInInventory} <aqua>${if (playerShards.shardsInBank + playerShards.shardsInInventory == 1) "Diamond" else "Diamonds"}<red>."))
            return null
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
                    PostgresFunction.SUBTRACT_FROM_PLAYER_SHARDS, shards, ShardType.BANK,
                    playerShards, "withdrawFromPlayer"
                )
                player.sendMessage(somethingWentWrongMessage)
                return null
            }
            return shards
        }

        if (shards <= playerShards.shardsInBank + playerShards.shardsInInventory) {
            var error = DiamondBankOG.postgreSQL.subtractFromPlayerShards(
                player.uniqueId,
                playerShards.shardsInBank,
                ShardType.BANK
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    PostgresFunction.SUBTRACT_FROM_PLAYER_SHARDS, playerShards.shardsInBank, ShardType.BANK,
                    playerShards, "withdrawFromPlayer"
                )
                player.sendMessage(somethingWentWrongMessage)
                return null
            }

            error = player.inventory.withdraw(
                shards - playerShards.shardsInBank
            )
            if (error) {
                player.sendMessage(somethingWentWrongMessage)
                return null
            }
            return shards
        }

        var error = DiamondBankOG.postgreSQL.subtractFromPlayerShards(
            player.uniqueId,
            playerShards.shardsInBank,
            ShardType.BANK
        )
        if (error) {
            handleError(
                player.uniqueId,
                PostgresFunction.SUBTRACT_FROM_PLAYER_SHARDS, playerShards.shardsInBank, ShardType.BANK,
                playerShards, "withdrawFromPlayer"
            )
            player.sendMessage(somethingWentWrongMessage)
            return null
        }

        error = player.inventory.withdraw(playerShards.shardsInInventory)
        if (error) {
            player.sendMessage(somethingWentWrongMessage)
            return null
        }

        error = player.enderChest.withdraw(
            shards - (playerShards.shardsInBank + playerShards.shardsInInventory)
        )
        if (error) {
            player.sendMessage(somethingWentWrongMessage)
            return null
        }

        return shards
    }

    fun handleError(
        uuid: UUID,
        function: PostgresFunction,
        shards: Int,
        diamondType: ShardType,
        playerShards: PlayerShards?,
        inFunction: String
    ) {
        DiamondBankOG.economyDisabled = true
        if (DiamondBankOG.sentryEnabled) {
            val sentryUser = User()
            sentryUser.id = uuid.toString()

            val sentryEvent = SentryEvent()
            sentryEvent.user = sentryUser
            sentryEvent.setExtra("Function", "${function.string}(amount = $shards, type = $diamondType)")
            if (playerShards != null) {
                if (playerShards.shardsInBank != null) sentryEvent.setExtra(
                    "Bank Balance",
                    playerShards.shardsInBank
                )
                if (playerShards.shardsInInventory != null) sentryEvent.setExtra(
                    "Inventory Balance",
                    playerShards.shardsInInventory
                )
                if (playerShards.shardsInEnderChest != null) sentryEvent.setExtra(
                    "Ender Chest Balance",
                    playerShards.shardsInEnderChest
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
            Function: ${function.string}(amount = $shards, type = $diamondType)
            ${
                if (playerShards != null) {
                    if (playerShards.shardsInBank != null) "Bank Balance: ${playerShards.shardsInBank}" else ""
                } else ""
            }
            ${
                if (playerShards != null) {
                    if (playerShards.shardsInInventory != null) "Inventory Balance: ${playerShards.shardsInInventory}" else ""
                } else ""
            }
            ${
                if (playerShards != null) {
                    if (playerShards.shardsInEnderChest != null) "Ender Chest Balance: ${playerShards.shardsInEnderChest}" else ""
                } else ""
            }
        """.trimIndent()
        )
    }
}