package net.trueog.diamondbankog

import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.User
import net.trueog.diamondbankog.InventoryExtensions.withdraw
import net.trueog.diamondbankog.PostgreSQL.*
import org.bukkit.entity.Player
import java.util.*

object Helper {
    enum class PostgresFunction(val string: String) {
        SET_PLAYER_DIAMONDS("setPlayerDiamonds"),
        ADD_TO_PLAYER_DIAMONDS("addToPlayerDiamonds"),
        SUBTRACT_FROM_PLAYER_DIAMONDS("subtractFromPlayerDiamonds")
    }

    suspend fun withdrawFromPlayer(player: Player, amount: Int): Int? {
        val somethingWentWrongMessage =
            DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong.")

        val playerShards = DiamondBankOG.postgreSQL.getPlayerShards(player.uniqueId, ShardType.ALL)
        if (playerShards.amountInBank == null || playerShards.amountInInventory == null || playerShards.amountInEnderChest == null) {
            player.sendMessage(somethingWentWrongMessage)
            return null
        }

        // Withdraw everything
        if (amount == -1) {
            var error = DiamondBankOG.postgreSQL.subtractFromPlayerShards(
                player.uniqueId,
                playerShards.amountInBank,
                ShardType.BANK
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    PostgresFunction.SUBTRACT_FROM_PLAYER_DIAMONDS, playerShards.amountInBank, ShardType.BANK,
                    playerShards, "withdrawFromPlayer"
                )
                player.sendMessage(somethingWentWrongMessage)
                return null
            }

            error = player.inventory.withdraw(
                playerShards.amountInInventory
            )
            if (error) {
                player.sendMessage(somethingWentWrongMessage)
                return null
            }

            error = player.enderChest.withdraw(
                playerShards.amountInEnderChest
            )
            if (error) {
                player.sendMessage(somethingWentWrongMessage)
                return null
            }
            return playerShards.amountInBank + playerShards.amountInInventory + playerShards.amountInEnderChest
        }

        if (amount > playerShards.amountInBank + playerShards.amountInInventory + playerShards.amountInEnderChest) {
            player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Cannot withdraw <yellow>$amount <aqua>${if (amount == 1) "Diamond" else "Diamonds"} <red>because you only have <yellow>${playerShards.amountInBank + playerShards.amountInInventory} <aqua>${if (playerShards.amountInBank + playerShards.amountInInventory == 1) "Diamond" else "Diamonds"}<red>."))
            return null
        }

        if (amount <= playerShards.amountInBank) {
            val error = DiamondBankOG.postgreSQL.subtractFromPlayerShards(
                player.uniqueId,
                amount,
                ShardType.BANK
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    PostgresFunction.SUBTRACT_FROM_PLAYER_DIAMONDS, amount, ShardType.BANK,
                    playerShards, "withdrawFromPlayer"
                )
                player.sendMessage(somethingWentWrongMessage)
                return null
            }
            return amount
        }

        if (amount <= playerShards.amountInBank + playerShards.amountInInventory) {
            var error = DiamondBankOG.postgreSQL.subtractFromPlayerShards(
                player.uniqueId,
                playerShards.amountInBank,
                ShardType.BANK
            )
            if (error) {
                handleError(
                    player.uniqueId,
                    PostgresFunction.SUBTRACT_FROM_PLAYER_DIAMONDS, playerShards.amountInBank, ShardType.BANK,
                    playerShards, "withdrawFromPlayer"
                )
                player.sendMessage(somethingWentWrongMessage)
                return null
            }

            error = player.inventory.withdraw(
                amount - playerShards.amountInBank
            )
            if (error) {
                player.sendMessage(somethingWentWrongMessage)
                return null
            }
            return amount
        }

        var error = DiamondBankOG.postgreSQL.subtractFromPlayerShards(
            player.uniqueId,
            playerShards.amountInBank,
            ShardType.BANK
        )
        if (error) {
            handleError(
                player.uniqueId,
                PostgresFunction.SUBTRACT_FROM_PLAYER_DIAMONDS, playerShards.amountInBank, ShardType.BANK,
                playerShards, "withdrawFromPlayer"
            )
            player.sendMessage(somethingWentWrongMessage)
            return null
        }

        error = player.inventory.withdraw(playerShards.amountInInventory)
        if (error) {
            player.sendMessage(somethingWentWrongMessage)
            return null
        }

        error = player.enderChest.withdraw(
            amount - (playerShards.amountInBank + playerShards.amountInInventory)
        )
        if (error) {
            player.sendMessage(somethingWentWrongMessage)
            return null
        }

        return amount
    }

    fun handleError(
        uuid: UUID,
        function: PostgresFunction,
        amount: Int,
        diamondType: ShardType,
        playerDiamonds: PlayerShards?,
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
                if (playerDiamonds.amountInBank != null) sentryEvent.setExtra(
                    "Bank Balance",
                    playerDiamonds.amountInBank
                )
                if (playerDiamonds.amountInInventory != null) sentryEvent.setExtra(
                    "Inventory Balance",
                    playerDiamonds.amountInInventory
                )
                if (playerDiamonds.amountInEnderChest != null) sentryEvent.setExtra(
                    "Ender Chest Balance",
                    playerDiamonds.amountInEnderChest
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
                    if (playerDiamonds.amountInBank != null) "Bank Balance: ${playerDiamonds.amountInBank}" else ""
                } else ""
            }
            ${
                if (playerDiamonds != null) {
                    if (playerDiamonds.amountInInventory != null) "Inventory Balance: ${playerDiamonds.amountInInventory}" else ""
                } else ""
            }
            ${
                if (playerDiamonds != null) {
                    if (playerDiamonds.amountInEnderChest != null) "Ender Chest Balance: ${playerDiamonds.amountInEnderChest}" else ""
                } else ""
            }
        """.trimIndent()
        )
    }
}