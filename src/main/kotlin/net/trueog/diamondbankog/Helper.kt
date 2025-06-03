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
import kotlin.math.floor

object Helper {
    enum class PostgresFunction(val string: String) {
        SET_PLAYER_SHARDS("setPlayerShards"),
        ADD_TO_PLAYER_SHARDS("addToPlayerShards"),
        SUBTRACT_FROM_PLAYER_SHARDS("subtractFromShards"),
        OTHER("other")
    }

    suspend fun withdrawFromPlayer(player: Player, shards: Int): Int? {
        val playerShards = DiamondBankOG.postgreSQL.getPlayerShards(player.uniqueId, ShardType.ALL)
        if (playerShards.shardsInBank == null || playerShards.shardsInInventory == null || playerShards.shardsInEnderChest == null) {
            player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong."))
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
                DiamondBankOG.economyDisabled = true
                player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."))
                return null
            }

            error = player.inventory.withdraw(
                playerShards.shardsInInventory
            )
            if (error) {
                DiamondBankOG.economyDisabled = true
                player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."))
                return null
            }

            error = player.enderChest.withdraw(
                playerShards.shardsInEnderChest
            )
            if (error) {
                DiamondBankOG.economyDisabled = true
                player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."))
                return null
            }
            return playerShards.shardsInBank + playerShards.shardsInInventory + playerShards.shardsInEnderChest
        }

        if (shards > playerShards.shardsInBank + playerShards.shardsInInventory + playerShards.shardsInEnderChest) {
            val diamonds = String.format("%.1f", floor((shards / 9.0) * 10) / 10.0)
            val totalDiamonds = String.format(
                "%.1f",
                floor(((playerShards.shardsInBank + playerShards.shardsInInventory + playerShards.shardsInEnderChest) / 9.0) * 10) / 10.0
            )
            player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Cannot use <yellow>$diamonds <aqua>${if (diamonds == "1.0") "Diamond" else "Diamonds"} <red>in a transaction because you only have <yellow>$totalDiamonds <aqua>${if (totalDiamonds == "1.0") "Diamond" else "Diamonds"}<red>."))
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
                DiamondBankOG.economyDisabled = true
                player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."))
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
                DiamondBankOG.economyDisabled = true
                player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."))
                return null
            }

            error = player.inventory.withdraw(
                shards - playerShards.shardsInBank
            )
            if (error) {
                DiamondBankOG.economyDisabled = true
                player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."))
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
            DiamondBankOG.economyDisabled = true
            player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."))
            return null
        }

        error = player.inventory.withdraw(playerShards.shardsInInventory)
        if (error) {
            DiamondBankOG.economyDisabled = true
            player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."))
            return null
        }

        error = player.enderChest.withdraw(
            shards - (playerShards.shardsInBank + playerShards.shardsInInventory)
        )
        if (error) {
            DiamondBankOG.economyDisabled = true
            player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."))
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