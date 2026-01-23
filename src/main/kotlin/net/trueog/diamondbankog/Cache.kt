package net.trueog.diamondbankog

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import net.trueog.diamondbankog.DiamondBankException.InvalidArgumentException
import net.trueog.diamondbankog.PostgreSQL.ShardType
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

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

    fun addBalance(uuid: UUID, value: Long, type: ShardType): Result<Unit> {
        if (type == ShardType.TOTAL) return Result.failure(InvalidArgumentException())
        when (type) {
            ShardType.BANK -> {
                bankBalanceCacheLock.write { bankBalanceCache.addTo(uuid, value) }
            }

            ShardType.INVENTORY -> {
                inventoryBalanceCacheLock.write { inventoryBalanceCache.addTo(uuid, value) }
            }

            ShardType.ENDER_CHEST -> {
                enderChestBalanceCacheLock.write { enderChestBalanceCache.addTo(uuid, value) }
            }

            else -> {}
        }
        return Result.success(Unit)
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
                            bankBalanceCache.getLong(uuid) +
                                    inventoryBalanceCache.getLong(uuid) +
                                    enderChestBalanceCache.getLong(uuid)
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
        bankBalanceCacheLock.write {
            inventoryBalanceCacheLock.write {
                enderChestBalanceCacheLock.write {
                    bankBalanceCache.removeLong(uuid)
                    inventoryBalanceCache.removeLong(uuid)
                    enderChestBalanceCache.removeLong(uuid)
                }
            }
        }
    }
}
