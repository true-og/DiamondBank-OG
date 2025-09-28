package net.trueog.diamondbankog

import kotlin.math.floor
import net.trueog.diamondbankog.DiamondBankException.CouldNotRemoveEnoughException
import net.trueog.diamondbankog.DiamondBankException.OtherException
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.mm
import net.trueog.diamondbankog.DiamondBankOG.Companion.postgreSQL
import net.trueog.diamondbankog.InventoryExtensions.withdraw
import org.bukkit.entity.Player

internal object WithdrawHelper {
    suspend fun withdrawFromPlayer(player: Player, shards: Long): Result<Unit> {
        val playerShards =
            postgreSQL.getAllShards(player.uniqueId).getOrElse {
                return Result.failure(it)
            }

        // Withdraw everything
        if (shards == -1L) {
            postgreSQL.subtractFromBankShards(player.uniqueId, playerShards.bank).getOrElse {
                player.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                    )
                )
                return Result.failure(it)
            }

            val notRemovedInventory = player.inventory.withdraw(playerShards.inventory.toInt())
            val notRemovedEnderChest = player.enderChest.withdraw(playerShards.enderChest.toInt())
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
                mm.deserialize(
                    "${config.prefix}<reset>: <red>Cannot use <yellow>$diamonds <aqua>Diamond${if (diamonds != "1.0") "s" else ""} <red>in a transaction because you only have <yellow>$totalDiamonds <aqua>Diamond${if (totalDiamonds != "1.0") "s" else ""}<red>."
                )
            )
            return Result.failure(OtherException())
        }

        if (shards <= playerShards.bank) {
            postgreSQL.subtractFromBankShards(player.uniqueId, shards).getOrElse {
                player.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                    )
                )
                return Result.failure(it)
            }
            return Result.success(Unit)
        }

        if (shards <= playerShards.bank + playerShards.inventory) {
            postgreSQL.subtractFromBankShards(player.uniqueId, playerShards.bank).getOrElse {
                player.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                    )
                )
                return Result.failure(it)
            }

            val notRemoved = player.inventory.withdraw((shards - playerShards.bank).toInt())
            return if (notRemoved == 0) {
                Result.success(Unit)
            } else {
                Result.failure(CouldNotRemoveEnoughException(notRemoved))
            }
        }

        postgreSQL.subtractFromBankShards(player.uniqueId, playerShards.bank).getOrElse {
            player.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                )
            )
            return Result.failure(it)
        }

        val notRemovedInventory = player.inventory.withdraw(playerShards.inventory.toInt())
        val notRemovedEnderChest =
            player.enderChest.withdraw((shards - (playerShards.bank + playerShards.inventory)).toInt())

        return if (notRemovedInventory == 0 && notRemovedEnderChest == 0) {
            Result.success(Unit)
        } else {
            Result.failure(CouldNotRemoveEnoughException(notRemovedInventory + notRemovedEnderChest))
        }
    }
}
