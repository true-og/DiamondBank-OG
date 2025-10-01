package net.trueog.diamondbankog

import java.sql.SQLException
import java.util.UUID
import net.trueog.diamondbankog.DiamondBankException.InvalidArgumentException
import net.trueog.diamondbankog.PostgreSQL.PlayerShards
import net.trueog.diamondbankog.PostgreSQL.ShardType

internal class BalanceManager {
    val cache = Cache()
    val postgreSQL = PostgreSQL()

    @Throws(SQLException::class, ClassNotFoundException::class)
    fun init() {
        postgreSQL.initDB()
    }

    suspend fun setPlayerShards(uuid: UUID, shards: Long, type: ShardType): Result<Unit> {
        if (type == ShardType.TOTAL) return Result.failure(InvalidArgumentException())

        cache.removeBalance(uuid, type)
        postgreSQL.setPlayerShards(uuid, shards, type).getOrElse {
            return Result.failure(it)
        }
        cache.setBalance(uuid, shards, type)
        return Result.success(Unit)
    }

    suspend fun addToPlayerShards(uuid: UUID, shards: Long, type: ShardType): Result<Unit> {
        if (type == ShardType.TOTAL) return Result.failure(InvalidArgumentException())

        cache.removeBalance(uuid, type)
        postgreSQL.addToPlayerShards(uuid, shards, type).getOrElse {
            return Result.failure(it)
        }
        cache.addBalance(uuid, shards, type)
        return Result.success(Unit)
    }

    suspend fun subtractFromBankShards(uuid: UUID, shards: Long): Result<Unit> =
        addToPlayerShards(uuid, -shards, ShardType.BANK)

    suspend fun getBankShards(uuid: UUID) = getShardTypeShards(uuid, ShardType.BANK)

    suspend fun getInventoryShards(uuid: UUID) = getShardTypeShards(uuid, ShardType.INVENTORY)

    suspend fun getEnderChestShards(uuid: UUID) = getShardTypeShards(uuid, ShardType.ENDER_CHEST)

    suspend fun getShardTypeShards(uuid: UUID, type: ShardType): Result<Long> {
        val cacheBalance = cache.getBalance(uuid, type)
        if (cacheBalance == -1L) {
            val dbBalance =
                postgreSQL.getShardTypeShards(uuid, type).getOrElse {
                    return Result.failure(it)
                }
            cache.setBalance(uuid, dbBalance, type)
            return Result.success(dbBalance)
        }
        return Result.success(cacheBalance)
    }

    suspend fun getTotalShards(uuid: UUID): Result<Long> {
        val cacheBalance = cache.getBalance(uuid, ShardType.TOTAL)
        if (cacheBalance == -1L) {
            val dbBalance =
                postgreSQL.getTotalShards(uuid).getOrElse {
                    return Result.failure(it)
                }
            cache.setBalance(uuid, dbBalance, ShardType.TOTAL)
            return Result.success(dbBalance)
        }
        return Result.success(cacheBalance)
    }

    suspend fun getAllShards(uuid: UUID): Result<PlayerShards> {
        val cacheBankBalance = cache.getBalance(uuid, ShardType.BANK)
        val cacheInventoryBalance = cache.getBalance(uuid, ShardType.INVENTORY)
        val cacheEnderChestBalance = cache.getBalance(uuid, ShardType.ENDER_CHEST)
        if (cacheBankBalance == -1L || cacheInventoryBalance == -1L || cacheEnderChestBalance == -1L) {
            val dbPlayerShards =
                postgreSQL.getAllShards(uuid).getOrElse {
                    return Result.failure(it)
                }
            cache.setBalance(uuid, dbPlayerShards.bank, ShardType.BANK)
            cache.setBalance(uuid, dbPlayerShards.inventory, ShardType.INVENTORY)
            cache.setBalance(uuid, dbPlayerShards.enderChest, ShardType.ENDER_CHEST)
            return Result.success(dbPlayerShards)
        }
        return Result.success(PlayerShards(cacheBankBalance, cacheInventoryBalance, cacheEnderChestBalance))
    }

    suspend fun cacheForPlayer(uuid: UUID): Result<Unit> {
        val playerShards =
            postgreSQL.getAllShards(uuid).getOrElse {
                return Result.failure(it)
            }
        cache.setBalance(uuid, playerShards.bank, ShardType.BANK)
        cache.setBalance(uuid, playerShards.inventory, ShardType.INVENTORY)
        cache.setBalance(uuid, playerShards.enderChest, ShardType.ENDER_CHEST)
        return Result.success(Unit)
    }

    fun removeCacheForPlayer(uuid: UUID) {
        cache.removeAll(uuid)
    }

    fun shutdown() {
        postgreSQL.pool.disconnect().get()
    }

    suspend fun hasEntry(uuid: UUID) = postgreSQL.hasEntry(uuid)

    suspend fun insertTransactionLog(
        playerUuid: UUID,
        transferredShards: Long,
        playerToUuid: UUID?,
        transactionReason: String,
        notes: String?,
    ): Result<Unit> =
        postgreSQL.insertTransactionLog(playerUuid, transferredShards, playerToUuid, transactionReason, notes)

    suspend fun getBaltop(offset: Int): Result<Map<UUID?, Long>> = postgreSQL.getBaltop(offset)

    suspend fun getBaltopWithUuid(uuid: UUID): Result<Pair<Map<UUID?, Long>, Long>> = postgreSQL.getBaltopWithUuid(uuid)

    suspend fun getNumberOfRows(): Result<Long> = postgreSQL.getNumberOfRows()
}
