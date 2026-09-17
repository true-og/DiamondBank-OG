package net.trueog.diamondbankog.balance

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.trueog.diamondbankog.DiamondBankException.EconomyDisabledException
import net.trueog.diamondbankog.DiamondBankException.InsufficientBalanceException
import net.trueog.diamondbankog.DiamondBankException.InvalidArgumentException
import net.trueog.diamondbankog.DiamondBankOG.Companion.debug
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.DiamondBankOG.Companion.plugin
import net.trueog.diamondbankog.balance.shard.PlayerShards
import net.trueog.diamondbankog.balance.shard.ShardType
import net.trueog.diamondbankog.persistence.PostgreSQL

internal class CachingBalanceManager private constructor() : BalanceManager {
    private val cache = Cache()
    private lateinit var postgreSQL: PostgreSQL

    companion object : BalanceManagerFactory {
        override fun create(): BalanceManager? {
            val cachingBalanceManager = CachingBalanceManager()
            cachingBalanceManager.postgreSQL = PostgreSQL.create() ?: return null
            return cachingBalanceManager
        }
    }

    private val mutexMap = ConcurrentHashMap<UUID, ConcurrentHashMap<ShardType, Mutex>>()

    private suspend fun <T> withLock(uuid: UUID, vararg types: ShardType, block: suspend () -> T): T {
        val shardTypeToMutex = mutexMap.computeIfAbsent(uuid) { ConcurrentHashMap() }
        val mutexes = types.sorted().map { shardTypeToMutex.computeIfAbsent(it) { Mutex() } }
        return mutexes.foldRight(block) { mutex, acc -> { mutex.withLock { acc() } } }()
    }

    private suspend fun <T> withLock(type: ShardType, vararg uuids: UUID, block: suspend () -> T): T {
        val mutexes =
            uuids.sorted().map { uuid ->
                mutexMap.computeIfAbsent(uuid) { ConcurrentHashMap() }.computeIfAbsent(type) { Mutex() }
            }
        return mutexes.foldRight(block) { mutex, acc -> { mutex.withLock { acc() } } }()
    }

    override suspend fun setPlayerShards(uuid: UUID, shards: Long, type: ShardType): Result<Unit> {
        if (type == ShardType.TOTAL) return Result.failure(InvalidArgumentException())
        if (economyDisabled) return Result.failure(EconomyDisabledException())

        return withLock(uuid, type) {
            postgreSQL.setPlayerShards(uuid, shards, type).getOrElse {
                return@withLock Result.failure(it)
            }
            cache.setBalance(uuid, shards, type)
            return@withLock Result.success(Unit)
        }
    }

    private suspend fun addToPlayerShards(uuid: UUID, shards: Long, type: ShardType): Result<Unit> {
        if (type == ShardType.TOTAL) return Result.failure(InvalidArgumentException())
        if (economyDisabled) return Result.failure(EconomyDisabledException())

        return withLock(uuid, type) {
            val currentBalance =
                postgreSQL.getShardTypeShards(uuid, type).getOrElse {
                    return@withLock Result.failure(it)
                }
            if (currentBalance + shards < 0) {
                return@withLock Result.failure(InsufficientBalanceException(currentBalance))
            }

            val newBalance =
                postgreSQL.addToPlayerShards(uuid, shards, type).getOrElse {
                    return@withLock Result.failure(it)
                }
            cache.setBalance(uuid, newBalance, type).getOrElse {
                return@withLock Result.failure(it)
            }
            return@withLock Result.success(Unit)
        }
    }

    override suspend fun addToBankShards(uuid: UUID, shards: Long): Result<Unit> =
        addToPlayerShards(uuid, shards, ShardType.BANK)

    override suspend fun subtractFromBankShards(uuid: UUID, shards: Long): Result<Unit> =
        addToPlayerShards(uuid, -shards, ShardType.BANK)

    override suspend fun transferBankShards(
        sender: UUID,
        receiver: UUID,
        shardsToSubtractFromSender: Long,
        shardsToAddToReceiver: Long,
    ): Result<Unit> {
        require(shardsToSubtractFromSender >= 0) { "shardsToSubtractFromSender must not be negative" }
        require(shardsToAddToReceiver >= 0) { "shardsToAddToReceiver must not be negative" }
        require(sender != receiver) { "sender and receiver must not be equal" }
        if (economyDisabled) return Result.failure(EconomyDisabledException())

        return withLock(ShardType.BANK, sender, receiver) {
            val currentBalanceFrom =
                postgreSQL.getShardTypeShards(sender, ShardType.BANK).getOrElse {
                    return@withLock Result.failure(it)
                }
            if (currentBalanceFrom < shardsToSubtractFromSender) {
                return@withLock Result.failure(InsufficientBalanceException(currentBalanceFrom))
            }

            postgreSQL
                .transferBankShards(sender, receiver, shardsToSubtractFromSender, shardsToAddToReceiver)
                .getOrElse {
                    return@withLock Result.failure(it)
                }
                .forEach { (uuid, shards) -> cache.setBalance(uuid, shards, ShardType.BANK) }
            return@withLock Result.success(Unit)
        }
    }

    override suspend fun getBankShards(uuid: UUID): Result<Long> = getShardTypeShards(uuid, ShardType.BANK)

    override suspend fun getInventoryShards(uuid: UUID): Result<Long> = getShardTypeShards(uuid, ShardType.INVENTORY)

    override suspend fun getEnderChestShards(uuid: UUID): Result<Long> = getShardTypeShards(uuid, ShardType.ENDER_CHEST)

    override suspend fun getShardTypeShards(uuid: UUID, type: ShardType): Result<Long> {
        val cacheBalance =
            cache.getBalance(uuid, type).getOrElse {
                return Result.failure(it)
            }
        if (cacheBalance != -1L) return Result.success(cacheBalance)

        return withLock(uuid, type) {
            if (debug) {
                plugin.logger.info("Cache miss for $uuid!")
            }
            val dbBalance =
                postgreSQL.getShardTypeShards(uuid, type).getOrElse {
                    return@withLock Result.failure(it)
                }
            cache.setBalance(uuid, dbBalance, type)
            return@withLock Result.success(dbBalance)
        }
    }

    override suspend fun getTotalShards(uuid: UUID): Result<Long> = getAllShards(uuid).map { it.total }

    override suspend fun getAllShards(uuid: UUID): Result<PlayerShards> {
        val cacheBankBalance =
            cache.getBalance(uuid, ShardType.BANK).getOrElse {
                return Result.failure(it)
            }
        val cacheInventoryBalance =
            cache.getBalance(uuid, ShardType.INVENTORY).getOrElse {
                return Result.failure(it)
            }
        val cacheEnderChestBalance =
            cache.getBalance(uuid, ShardType.ENDER_CHEST).getOrElse {
                return Result.failure(it)
            }

        if (cacheBankBalance != -1L && cacheInventoryBalance != -1L && cacheEnderChestBalance != -1L) {
            return Result.success(PlayerShards(cacheBankBalance, cacheInventoryBalance, cacheEnderChestBalance))
        }

        return withLock(uuid, ShardType.BANK, ShardType.INVENTORY, ShardType.ENDER_CHEST) {
            if (debug) {
                plugin.logger.info("Cache miss for $uuid!")
            }
            val dbPlayerShards =
                postgreSQL.getAllShards(uuid).getOrElse {
                    return@withLock Result.failure(it)
                }
            cache.setBalance(uuid, dbPlayerShards.bank, ShardType.BANK)
            cache.setBalance(uuid, dbPlayerShards.inventory, ShardType.INVENTORY)
            cache.setBalance(uuid, dbPlayerShards.enderChest, ShardType.ENDER_CHEST)
            return@withLock Result.success(dbPlayerShards)
        }
    }

    override suspend fun getBaltop(offset: Int): Result<Map<UUID?, Long>> {
        require(offset >= 0) { "offset must not be negative" }
        return postgreSQL.getBaltop(offset)
    }

    override suspend fun getBaltopWithUuid(uuid: UUID): Result<Pair<Map<UUID?, Long>, Long>> =
        postgreSQL.getBaltopWithUuid(uuid)

    override suspend fun getNumberOfRows(): Result<Long> = postgreSQL.getNumberOfRows()

    override suspend fun insertTransactionLog(
        playerUuid: UUID,
        transferredShards: Long,
        playerToUuid: UUID?,
        transactionReason: String,
        notes: String?,
    ): Result<Unit> =
        postgreSQL.insertTransactionLog(playerUuid, transferredShards, playerToUuid, transactionReason, notes)

    override suspend fun hasEntry(uuid: UUID): Result<Boolean> = postgreSQL.hasEntry(uuid)

    override suspend fun cacheForPlayer(uuid: UUID) =
        withLock(uuid, ShardType.BANK, ShardType.INVENTORY, ShardType.ENDER_CHEST) {
            val playerShards =
                postgreSQL.getAllShards(uuid).getOrElse {
                    return@withLock Result.failure(it)
                }
            cache.setBalance(uuid, playerShards.bank, ShardType.BANK)
            cache.setBalance(uuid, playerShards.inventory, ShardType.INVENTORY)
            cache.setBalance(uuid, playerShards.enderChest, ShardType.ENDER_CHEST)
            return@withLock Result.success(Unit)
        }

    override suspend fun removeCacheForPlayer(uuid: UUID) =
        withLock(uuid, ShardType.BANK, ShardType.INVENTORY, ShardType.ENDER_CHEST) { cache.removeAll(uuid) }

    override fun shutdown() {
        postgreSQL.disconnect().get()
    }
}
