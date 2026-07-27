package net.trueog.diamondbankog.balance

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import net.trueog.diamondbankog.DiamondBankException.InvalidArgumentException
import net.trueog.diamondbankog.balance.shard.ShardType

internal class Cache {
    private val bankBalanceCache = Object2LongOpenHashMap<UUID>().apply { defaultReturnValue(-1L) }
    private val bankBalanceCacheLock = ReentrantReadWriteLock()
    private val inventoryBalanceCache = Object2LongOpenHashMap<UUID>().apply { defaultReturnValue(-1L) }
    private val inventoryBalanceCacheLock = ReentrantReadWriteLock()
    private val enderChestBalanceCache = Object2LongOpenHashMap<UUID>().apply { defaultReturnValue(-1L) }
    private val enderChestBalanceCacheLock = ReentrantReadWriteLock()

    fun setBalance(uuid: UUID, value: Long, type: ShardType): Result<Unit> {
        if (type == ShardType.TOTAL) return Result.failure(InvalidArgumentException())
        when (type) {
            ShardType.BANK -> {
                bankBalanceCacheLock.write { bankBalanceCache.put(uuid, value) }
            }

            ShardType.INVENTORY -> {
                inventoryBalanceCacheLock.write { inventoryBalanceCache.put(uuid, value) }
            }

            ShardType.ENDER_CHEST -> {
                enderChestBalanceCacheLock.write { enderChestBalanceCache.put(uuid, value) }
            }

            else -> {}
        }
        return Result.success(Unit)
    }

    fun getBalance(uuid: UUID, type: ShardType): Result<Long> {
        return when (type) {
            ShardType.BANK -> {
                bankBalanceCacheLock.read { Result.success(bankBalanceCache.getLong(uuid)) }
            }

            ShardType.INVENTORY -> {
                inventoryBalanceCacheLock.read { Result.success(inventoryBalanceCache.getLong(uuid)) }
            }

            ShardType.ENDER_CHEST -> {
                enderChestBalanceCacheLock.read { Result.success(enderChestBalanceCache.getLong(uuid)) }
            }

            else -> Result.failure(InvalidArgumentException())
        }
    }

    fun removeBalance(uuid: UUID, type: ShardType): Result<Unit> {
        if (type == ShardType.TOTAL) return Result.failure(InvalidArgumentException())
        when (type) {
            ShardType.BANK -> {
                bankBalanceCacheLock.write { bankBalanceCache.removeLong(uuid) }
            }

            ShardType.INVENTORY -> {
                inventoryBalanceCacheLock.write { inventoryBalanceCache.removeLong(uuid) }
            }

            ShardType.ENDER_CHEST -> {
                enderChestBalanceCacheLock.write { enderChestBalanceCache.removeLong(uuid) }
            }

            else -> {}
        }
        return Result.success(Unit)
    }

    fun removeAll(uuid: UUID) {
        removeBalance(uuid, ShardType.BANK)
        removeBalance(uuid, ShardType.INVENTORY)
        removeBalance(uuid, ShardType.ENDER_CHEST)
    }
}
