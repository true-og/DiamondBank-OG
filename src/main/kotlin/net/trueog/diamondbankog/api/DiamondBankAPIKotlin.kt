package net.trueog.diamondbankog.api

import java.util.*
import kotlinx.coroutines.DelicateCoroutinesApi
import net.trueog.diamondbankog.CommonOperations
import net.trueog.diamondbankog.DiamondBankException.*
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.DiamondBankOG.Companion.eventManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.transactionLock
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.InventoryExtensions.lock
import net.trueog.diamondbankog.InventoryExtensions.unlock
import net.trueog.diamondbankog.InventorySnapshot
import net.trueog.diamondbankog.MainThreadBlock.runOnMainThread
import net.trueog.diamondbankog.PlayerBalanceChangedListener
import net.trueog.diamondbankog.PostgreSQL.PlayerShards
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.Bukkit

@OptIn(DelicateCoroutinesApi::class)
class DiamondBankAPIKotlin {
    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     */
    @Suppress("unused")
    suspend fun addToPlayerBankShards(
        uuid: UUID,
        shards: Long,
        transactionReason: String,
        notes: String?,
    ): Result<Unit> {
        if (economyDisabled) return Result.failure(EconomyDisabledException())

        return transactionLock.withLockSuspend(uuid) {
            balanceManager.addToPlayerShards(uuid, shards, ShardType.BANK).getOrElse {
                return@withLockSuspend Result.failure(it)
            }

            balanceManager.insertTransactionLog(uuid, shards, null, transactionReason, notes).getOrElse {
                handleError(it)
            }

            Result.success(Unit)
        }
    }

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     */
    @Suppress("unused")
    suspend fun subtractFromPlayerBankShards(
        uuid: UUID,
        shards: Long,
        transactionReason: String,
        notes: String?,
    ): Result<Unit> {
        if (economyDisabled) return Result.failure(EconomyDisabledException())

        return transactionLock.withLockSuspend(uuid) {
            balanceManager.subtractFromBankShards(uuid, shards).getOrElse {
                return@withLockSuspend Result.failure(it)
            }

            balanceManager.insertTransactionLog(uuid, shards, null, transactionReason, notes).getOrElse {
                handleError(it)
            }

            Result.success(Unit)
        }
    }

    /** WARNING: if the player has a transaction lock applied this function will wait until its released */
    @Suppress("unused") suspend fun getBankShards(uuid: UUID): Result<Long> = getShardTypeShards(uuid, ShardType.BANK)

    /** WARNING: if the player has a transaction lock applied this function will wait until its released */
    @Suppress("unused")
    suspend fun getInventoryShards(uuid: UUID): Result<Long> = getShardTypeShards(uuid, ShardType.INVENTORY)

    /** WARNING: if the player has a transaction lock applied this function will wait until its released */
    @Suppress("unused")
    suspend fun getEnderChestShards(uuid: UUID): Result<Long> = getShardTypeShards(uuid, ShardType.ENDER_CHEST)

    /** WARNING: if the player has a transaction lock applied this function will wait until its released */
    @Suppress("unused") suspend fun getTotalShards(uuid: UUID): Result<Long> = getShardTypeShards(uuid, ShardType.TOTAL)

    /** WARNING: if the player has a transaction lock applied this function will wait until its released */
    @Suppress("unused")
    suspend fun getAllShards(uuid: UUID): Result<PlayerShards> {
        if (economyDisabled) return Result.failure(EconomyDisabledException())

        return transactionLock.withLockSuspend(uuid) { balanceManager.getAllShards(uuid) }
    }

    private suspend fun getShardTypeShards(uuid: UUID, type: ShardType): Result<Long> {
        if (economyDisabled) return Result.failure(EconomyDisabledException())

        return transactionLock.withLockSuspend(uuid) {
            val result =
                when (type) {
                    ShardType.BANK -> balanceManager.getBankShards(uuid)
                    ShardType.INVENTORY -> balanceManager.getInventoryShards(uuid)
                    ShardType.ENDER_CHEST -> balanceManager.getEnderChestShards(uuid)
                    ShardType.TOTAL -> balanceManager.getTotalShards(uuid)
                }.getOrElse {
                    return@withLockSuspend Result.failure(it)
                }
            Result.success(result)
        }
    }

    @Suppress("unused")
    suspend fun getBaltop(offset: Int): Result<Map<UUID?, Long>> {
        if (economyDisabled) return Result.failure(EconomyDisabledException())

        val baltop =
            balanceManager.getBaltop(offset).getOrElse {
                return Result.failure(it)
            }
        return Result.success(baltop)
    }

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     */
    @Suppress("unused")
    suspend fun consumeFromPlayer(uuid: UUID, shards: Long, transactionReason: String, notes: String?): Result<Unit> {
        if (economyDisabled) return Result.failure(EconomyDisabledException())

        return transactionLock.withLockSuspend(uuid) {
            val player = Bukkit.getPlayer(uuid) ?: return@withLockSuspend Result.failure(PlayerNotOnlineException())
            if (!player.hasPlayedBefore()) return@withLockSuspend Result.failure(InvalidPlayerException())

            val inventorySnapshot = runOnMainThread {
                player.inventory.lock()
                InventorySnapshot.from(player.inventory)
            }

            CommonOperations.consume(player.uniqueId, shards, inventorySnapshot).getOrElse {
                return@withLockSuspend Result.failure(it)
            }

            runOnMainThread {
                inventorySnapshot.restoreTo(player.inventory)
                player.inventory.unlock()
            }

            balanceManager.insertTransactionLog(uuid, shards, null, transactionReason, notes).getOrElse {
                handleError(it)
            }

            Result.success(Unit)
        }
    }

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * WARNING: This function can return a CouldNotRemoveEnoughException, make sure you handle it properly. It has a
     * field called notRemoved that has the amount of shards not removed, you should continue with the originally
     * requested amount of shards minus notRemoved
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     */
    @Suppress("unused")
    suspend fun playerPayPlayer(
        payerUuid: UUID,
        receiverUuid: UUID,
        shards: Long,
        transactionReason: String,
        notes: String?,
    ): Result<Unit> {
        if (economyDisabled) return Result.failure(EconomyDisabledException())

        return transactionLock.withLockSuspend(payerUuid) {
            val payer = Bukkit.getPlayer(payerUuid) ?: return@withLockSuspend Result.failure(PlayerNotOnlineException())
            if (!payer.hasPlayedBefore()) return@withLockSuspend Result.failure(InvalidPlayerException())

            val receiver = Bukkit.getPlayer(receiverUuid) ?: Bukkit.getOfflinePlayer(receiverUuid)
            if (!receiver.hasPlayedBefore()) return@withLockSuspend Result.failure(InvalidPlayerException())

            val inventorySnapshot = runOnMainThread {
                payer.inventory.lock()
                InventorySnapshot.from(payer.inventory)
            }

            CommonOperations.consume(payer.uniqueId, shards, inventorySnapshot).getOrElse {
                return@withLockSuspend Result.failure(it)
            }

            balanceManager.addToPlayerShards(receiverUuid, shards, ShardType.BANK).getOrElse {
                handleError(it)
                return@withLockSuspend Result.failure(it)
            }

            runOnMainThread {
                inventorySnapshot.restoreTo(payer.inventory)
                payer.inventory.unlock()
            }

            balanceManager.insertTransactionLog(payerUuid, shards, receiverUuid, transactionReason, notes).getOrElse {
                handleError(it)
            }

            Result.success(Unit)
        }
    }

    @Suppress("unused")
    fun registerEventListener(eventListener: PlayerBalanceChangedListener) {
        eventManager.register(eventListener)
    }

    /** Converts a Diamond float to Shards */
    @Suppress("unused")
    fun diamondsToShards(diamonds: Float) = CommonOperations.diamondsToShards(diamonds)

    /** Converts Shards into a formatted Diamonds string */
    @Suppress("unused") fun shardsToDiamonds(shards: Long) = CommonOperations.shardsToDiamonds(shards)
}
