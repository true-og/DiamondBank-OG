package net.trueog.diamondbankog

import java.util.*
import kotlinx.coroutines.DelicateCoroutinesApi
import net.trueog.diamondbankog.DiamondBankException.*
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.DiamondBankOG.Companion.eventManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.transactionLock
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.PostgreSQL.PlayerShards
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.Bukkit

@OptIn(DelicateCoroutinesApi::class)
class DiamondBankAPIKotlin() {
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
                handleError(uuid, shards, null, null, true)
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
                handleError(uuid, shards, null, null, true)
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
    suspend fun withdrawFromPlayer(uuid: UUID, shards: Long, transactionReason: String, notes: String?): Result<Unit> {
        if (economyDisabled) return Result.failure(EconomyDisabledException())

        return transactionLock.withLockSuspend(uuid) {
            val player = Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
            if (!player.hasPlayedBefore()) return@withLockSuspend Result.failure(InvalidPlayerException())
            if (!player.isOnline) return@withLockSuspend Result.failure(PlayerNotOnlineException())
            val playerPlayer = player.player ?: return@withLockSuspend Result.failure(InvalidPlayerException())

            val balance =
                balanceManager.getTotalShards(uuid).getOrElse {
                    return@withLockSuspend Result.failure(it)
                }
            if (balance - shards < 0) {
                return@withLockSuspend Result.failure(InsufficientBalanceException(balance))
            }

            WithdrawHelper.withdrawFromPlayer(playerPlayer, shards).getOrElse {
                handleError(uuid, shards, null)
                return@withLockSuspend Result.failure(it)
            }

            balanceManager.insertTransactionLog(uuid, shards, null, transactionReason, notes).getOrElse {
                handleError(uuid, shards, null, null, true)
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
            val payer = Bukkit.getPlayer(payerUuid) ?: Bukkit.getOfflinePlayer(payerUuid)
            if (!payer.hasPlayedBefore()) return@withLockSuspend Result.failure(InvalidPlayerException())
            if (!payer.isOnline) return@withLockSuspend Result.failure(PayerNotOnlineException())
            val payerPlayer = payer.player ?: return@withLockSuspend Result.failure(InvalidPlayerException())

            val receiver = Bukkit.getPlayer(receiverUuid) ?: Bukkit.getOfflinePlayer(receiverUuid)
            if (!receiver.hasPlayedBefore()) return@withLockSuspend Result.failure(InvalidPlayerException())

            val balance =
                balanceManager.getTotalShards(payerUuid).getOrElse {
                    return@withLockSuspend Result.failure(it)
                }
            if (balance - shards < 0) {
                return@withLockSuspend Result.failure(InsufficientBalanceException(balance))
            }

            WithdrawHelper.withdrawFromPlayer(payerPlayer, shards).getOrElse {
                handleError(payerUuid, shards, null)
                return@withLockSuspend Result.failure(it)
            }

            balanceManager.addToPlayerShards(receiverUuid, shards, ShardType.BANK).getOrElse {
                handleError(payerUuid, shards, null)
                return@withLockSuspend Result.failure(it)
            }

            balanceManager.insertTransactionLog(payerUuid, shards, receiverUuid, transactionReason, notes).getOrElse {
                handleError(payerUuid, shards, null, receiverUuid, true)
            }

            Result.success(Unit)
        }
    }

    @Suppress("unused")
    fun registerEventListener(eventListener: PlayerBalanceChangedListener) {
        eventManager.register(eventListener)
    }
}
