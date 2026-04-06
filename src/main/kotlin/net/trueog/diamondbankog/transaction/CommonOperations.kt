package net.trueog.diamondbankog.transaction

import java.util.*
import kotlin.math.floor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.trueog.diamondbankog.DiamondBankException.InsufficientFundsException
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.DiamondBankRuntimeException.MoreThanOneDecimalDigitRuntimeException
import net.trueog.diamondbankog.balance.BalanceManager
import net.trueog.diamondbankog.config.Config
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

object CommonOperations {
    suspend fun consume(
        uuid: UUID,
        shards: Long,
        inventorySnapshot: InventorySnapshot,
        config: Config = DiamondBankOG.config,
        balanceManager: BalanceManager = DiamondBankOG.balanceManager,
        mm: MiniMessage = DiamondBankOG.mm,
    ): Result<Unit> {
        val bankShards =
            balanceManager.getBankShards(uuid).getOrElse {
                return Result.failure(it)
            }
        val shardsToSubtract =
            if (bankShards < shards) {
                val toRemoveShards = shards - bankShards
                val removedInShards =
                    InventorySnapshotUtils.removeShards(inventorySnapshot, toRemoveShards, config, balanceManager, mm)
                        .getOrElse {
                            return Result.failure(it)
                        }
                        .toLong()
                if (removedInShards != toRemoveShards) {
                    val short = toRemoveShards - removedInShards
                    throw InsufficientFundsException(short)
                }
                bankShards
            } else {
                shards
            }
        balanceManager.subtractFromBankShards(uuid, shardsToSubtract).getOrElse {
            return Result.failure(it)
        }
        return Result.success(Unit)
    }

    fun diamondsToShards(diamonds: Float): Result<Long> {
        val split = diamonds.toString().split('.')
        if (split[1].length > 1) {
            return Result.failure(MoreThanOneDecimalDigitRuntimeException())
        }
        return Result.success((split[0].toLong() * 9) + split[1].toLong())
    }

    fun shardsToDiamonds(shards: Long) = String.format("%.1f", floor((shards / 9.0) * 10) / 10.0)

    fun shardsToDiamondsFull(shards: Long): String {
        val diamonds = shardsToDiamonds(shards)
        return "<yellow>$diamonds <aqua>Diamond${if (diamonds != "1.0") "s" else ""}"
    }

    fun getPlayerUsingUuidOrName(value: String): OfflinePlayer {
        return try {
            val uuid = UUID.fromString(value)
            Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
        } catch (_: Exception) {
            Bukkit.getPlayer(value) ?: Bukkit.getOfflinePlayer(value)
        }
    }
}
