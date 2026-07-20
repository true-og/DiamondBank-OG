package net.trueog.diamondbankog.balance

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import net.trueog.diamondbankog.DiamondBankException.InvalidArgumentException
import net.trueog.diamondbankog.balance.shard.ShardType

internal class Cache {
    val bankBalanceCache = Object2LongOpenHashMap<UUID>().apply { defaultReturnValue(-1L) }
    val bankBalanceCacheLock = ReentrantReadWriteLock()
    val inventoryBalanceCache = Object2LongOpenHashMap<UUID>().apply { defaultReturnValue(-1L) }
    val inventoryBalanceCacheLock = ReentrantReadWriteLock()
    val enderChestBalanceCache = Object2LongOpenHashMap<UUID>().apply { defaultReturnValue(-1L) }
    val enderChestBalanceCacheLock = ReentrantReadWriteLock()

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

    fun addBalance(uuid: UUID, value: Long, type: ShardType): Result<Long> {
        val lock =
            when (type) {
                ShardType.BANK -> bankBalanceCacheLock
                ShardType.INVENTORY -> inventoryBalanceCacheLock
                ShardType.ENDER_CHEST -> enderChestBalanceCacheLock
                else -> return Result.failure(InvalidArgumentException())
            }
        lock.write {
            val old = getBalance(uuid, type).coerceAtLeast(0)
            val newBalance = old + value
            setBalance(uuid, newBalance, type)
            return Result.success(newBalance)
        }
    }

    fun getBalance(uuid: UUID, type: ShardType): Long {
        return when (type) {
            ShardType.BANK -> {
                bankBalanceCacheLock.read { bankBalanceCache.getLong(uuid) }
            }

            ShardType.INVENTORY -> {
                inventoryBalanceCacheLock.read { inventoryBalanceCache.getLong(uuid) }
            }

            ShardType.ENDER_CHEST -> {
                enderChestBalanceCacheLock.read { enderChestBalanceCache.getLong(uuid) }
            }

            ShardType.TOTAL -> {
                bankBalanceCacheLock.read {
                    inventoryBalanceCacheLock.read {
                        enderChestBalanceCacheLock.read {
                            val bankBalance = bankBalanceCache.getLong(uuid)
                            val inventoryBalance = inventoryBalanceCache.getLong(uuid)
                            val enderChestBalance = enderChestBalanceCache.getLong(uuid)
                            if (bankBalance == -1L && inventoryBalance == -1L && enderChestBalance == -1L) {
                                return -1
                            }

                            bankBalance.coerceAtLeast(0) +
                                inventoryBalance.coerceAtLeast(0) +
                                enderChestBalance.coerceAtLeast(0)
                        }
                    }
                }
            }
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
