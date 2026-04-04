package net.trueog.diamondbankog

import java.util.*
import net.trueog.diamondbankog.PostgreSQL.PlayerShards
import net.trueog.diamondbankog.PostgreSQL.ShardType

interface BalanceManager {
    suspend fun setPlayerShards(uuid: UUID, shards: Long, type: ShardType): Result<Unit>

    suspend fun addToBankShards(uuid: UUID, shards: Long): Result<Unit>

    suspend fun subtractFromBankShards(uuid: UUID, shards: Long): Result<Unit>

    suspend fun getBankShards(uuid: UUID): Result<Long>

    suspend fun getInventoryShards(uuid: UUID): Result<Long>

    suspend fun getEnderChestShards(uuid: UUID): Result<Long>

    suspend fun getShardTypeShards(uuid: UUID, type: ShardType): Result<Long>

    suspend fun getTotalShards(uuid: UUID): Result<Long>

    suspend fun getAllShards(uuid: UUID): Result<PlayerShards>

    suspend fun getBaltop(offset: Int): Result<Map<UUID?, Long>>

    suspend fun getBaltopWithUuid(uuid: UUID): Result<Pair<Map<UUID?, Long>, Long>>

    suspend fun getNumberOfRows(): Result<Long>

    suspend fun insertTransactionLog(
        playerUuid: UUID,
        transferredShards: Long,
        playerToUuid: UUID?,
        transactionReason: String,
        notes: String?,
    ): Result<Unit>

    suspend fun hasEntry(uuid: UUID): Result<Boolean>

    suspend fun cacheForPlayer(uuid: UUID): Result<Unit>

    fun removeCacheForPlayer(uuid: UUID)

    fun shutdown()
}
