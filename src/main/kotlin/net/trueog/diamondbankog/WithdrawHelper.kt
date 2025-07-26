package net.trueog.diamondbankog

import kotlin.math.floor
import net.trueog.diamondbankog.DiamondBankException.CouldNotRemoveEnoughException
import net.trueog.diamondbankog.DiamondBankException.OtherException
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.InventoryExtensions.withdraw
import org.bukkit.entity.Player

internal object WithdrawHelper {
    /** @return The amount of not removed shards, -1 if error */
    suspend fun withdrawFromPlayer(player: Player, shards: Int): Result<Unit> {
        val playerShards =
            DiamondBankOG.postgreSQL.getAllShards(player.uniqueId).getOrElse {
                return Result.failure(it)
            }

        // Withdraw everything
        if (shards == -1) {
            DiamondBankOG.postgreSQL.subtractFromBankShards(player.uniqueId, playerShards.bank).getOrElse {
                player.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                    )
                )
                return Result.failure(it)
            }

            val notRemovedInventory = player.inventory.withdraw(playerShards.inventory)
            val notRemovedEnderChest = player.enderChest.withdraw(playerShards.enderChest)
            return if (notRemovedInventory == 0 && notRemovedEnderChest == 0) {
                Result.success(Unit)
            } else {
                Result.failure(CouldNotRemoveEnoughException(notRemovedInventory + notRemovedEnderChest))
            }
        }

        if (shards > playerShards.bank + playerShards.inventory + playerShards.enderChest) {
            val diamonds = String.format("%.1f", floor((shards / 9.0) * 10) / 10.0)
            val totalDiamonds =
                String.format(
                    "%.1f",
                    floor(((playerShards.bank + playerShards.inventory + playerShards.enderChest) / 9.0) * 10) / 10.0,
                )
            player.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${config.prefix}<reset>: <red>Cannot use <yellow>$diamonds <aqua>Diamond${if (diamonds != "1.0") "s" else ""} <red>in a transaction because you only have <yellow>$totalDiamonds <aqua>Diamond${if (totalDiamonds != "1.0") "s" else ""}<red>."
                )
            )
            return Result.failure(OtherException)
        }

        if (shards <= playerShards.bank) {
            DiamondBankOG.postgreSQL.subtractFromBankShards(player.uniqueId, shards).getOrElse {
                player.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                    )
                )
                return Result.failure(it)
            }
            return Result.success(Unit)
        }

        if (shards <= playerShards.bank + playerShards.inventory) {
            DiamondBankOG.postgreSQL.subtractFromBankShards(player.uniqueId, playerShards.bank).getOrElse {
                player.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                    )
                )
                return Result.failure(it)
            }

            val notRemoved = player.inventory.withdraw(shards - playerShards.bank)
            return if (notRemoved == 0) {
                Result.success(Unit)
            } else {
                Result.failure(CouldNotRemoveEnoughException(notRemoved))
            }
        }

        DiamondBankOG.postgreSQL.subtractFromBankShards(player.uniqueId, playerShards.bank).getOrElse {
            player.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                )
            )
            return Result.failure(it)
        }

        val notRemovedInventory = player.inventory.withdraw(playerShards.inventory)
        val notRemovedEnderChest = player.enderChest.withdraw(shards - (playerShards.bank + playerShards.inventory))

        return if (notRemovedInventory == 0 && notRemovedEnderChest == 0) {
            Result.success(Unit)
        } else {
            Result.failure(CouldNotRemoveEnoughException(notRemovedInventory + notRemovedEnderChest))
        }
    }
}
