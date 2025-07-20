package net.trueog.diamondbankog

import java.util.*
import kotlinx.coroutines.DelicateCoroutinesApi
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.PostgreSQL.PlayerShards
import net.trueog.diamondbankog.PostgreSQL.ShardType
import net.trueog.diamondbankog.TransactionLock.LockResult
import org.bukkit.Bukkit

@OptIn(DelicateCoroutinesApi::class)
class DiamondBankAPIKotlin(private var postgreSQL: PostgreSQL) {
    /**
     * WARNING: blocking, if the player has a transaction lock applied this function will wait until its released
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     */
    @Suppress("unused")
    suspend fun blockingAddToPlayerBankShards(
        uuid: UUID,
        shards: Int,
        transactionReason: String,
        notes: String?,
    ): Result<Unit> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)
        return DiamondBankOG.transactionLock.withLockSuspend(uuid) {
            postgreSQL.addToPlayerShards(uuid, shards, ShardType.BANK).getOrElse {
                return@withLockSuspend Result.failure(DiamondBankException.DatabaseException(it.message ?: "Database exception"))
            }

            DiamondBankOG.postgreSQL.insertTransactionLog(uuid, shards, null, transactionReason, notes).getOrElse {
                handleError(uuid, shards, null, null, true)
            }

            Result.success(Unit)
        }
    }

    /**
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     */
    @Suppress("unused")
    suspend fun addToPlayerBankShards(
        uuid: UUID,
        shards: Int,
        transactionReason: String,
        notes: String?,
    ): Result<Unit> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return when (
            val result =
                DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) {
                    postgreSQL.addToPlayerShards(uuid, shards, ShardType.BANK).getOrElse {
                        return@tryWithLockSuspend Result.failure(DiamondBankException.DatabaseException(it.message ?: "Database exception"))
                    }

                    DiamondBankOG.postgreSQL
                        .insertTransactionLog(uuid, shards, null, transactionReason, notes)
                        .getOrElse { handleError(uuid, shards, null, null, true) }

                    Result.success(Unit)
                }
        ) {
            is LockResult.Acquired -> result.result

            is LockResult.Failed -> throw DiamondBankException.TransactionsLockedException
        }
    }

    /**
     * WARNING: blocking, if the player has a transaction lock applied this function will wait until its released
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     */
    @Suppress("unused")
    suspend fun blockingSubtractFromPlayerBankShards(
        uuid: UUID,
        shards: Int,
        transactionReason: String,
        notes: String?,
    ): Result<Unit> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return DiamondBankOG.transactionLock.withLockSuspend(uuid) {
            postgreSQL.subtractFromBankShards(uuid, shards).getOrElse {
                return@withLockSuspend Result.failure(DiamondBankException.DatabaseException(it.message ?: "Database exception"))
            }

            DiamondBankOG.postgreSQL.insertTransactionLog(uuid, shards, null, transactionReason, notes).getOrElse {
                handleError(uuid, shards, null, null, true)
            }

            Result.success(Unit)
        }
    }

    /**
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     */
    @Suppress("unused")
    suspend fun subtractFromPlayerBankShards(
        uuid: UUID,
        shards: Int,
        transactionReason: String,
        notes: String?,
    ): Result<Unit> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return when (
            val result =
                DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) {
                    postgreSQL.subtractFromBankShards(uuid, shards).getOrElse {
                        return@tryWithLockSuspend Result.failure(DiamondBankException.DatabaseException(it.message ?: "Database exception"))
                    }

                    DiamondBankOG.postgreSQL
                        .insertTransactionLog(uuid, shards, null, transactionReason, notes)
                        .getOrElse { handleError(uuid, shards, null, null, true) }

                    Result.success(Unit)
                }
        ) {
            is LockResult.Acquired -> result.result

            is LockResult.Failed -> throw DiamondBankException.TransactionsLockedException
        }
    }

    /** WARNING: blocking, if the player has a transaction lock applied this function will wait until its released */
    @Suppress("unused")
    suspend fun blockingGetBankShards(uuid: UUID): Result<Int> = blockingGetShardTypeShards(uuid, ShardType.BANK)

    /** WARNING: blocking, if the player has a transaction lock applied this function will wait until its released */
    @Suppress("unused")
    suspend fun blockingGetInventoryShards(uuid: UUID): Result<Int> =
        blockingGetShardTypeShards(uuid, ShardType.INVENTORY)

    /** WARNING: blocking, if the player has a transaction lock applied this function will wait until its released */
    @Suppress("unused")
    suspend fun blockingGetEnderChestShards(uuid: UUID): Result<Int> =
        blockingGetShardTypeShards(uuid, ShardType.ENDER_CHEST)

    /** WARNING: blocking, if the player has a transaction lock applied this function will wait until its released */
    @Suppress("unused")
    suspend fun blockingGetTotalShards(uuid: UUID): Result<Int> = blockingGetShardTypeShards(uuid, ShardType.TOTAL)

    /** WARNING: blocking, if the player has a transaction lock applied this function will wait until its released */
    @Suppress("unused")
    suspend fun blockingGetAllShards(uuid: UUID): Result<PlayerShards> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)
        return postgreSQL.getAllShards(uuid)
    }

    private suspend fun blockingGetShardTypeShards(uuid: UUID, type: ShardType): Result<Int> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return DiamondBankOG.transactionLock.withLockSuspend(uuid) {
            val result = when (type) {
                ShardType.BANK -> postgreSQL.getBankShards(uuid)
                ShardType.INVENTORY -> postgreSQL.getInventoryShards(uuid)
                ShardType.ENDER_CHEST -> postgreSQL.getEnderChestShards(uuid)
                ShardType.TOTAL -> postgreSQL.getTotalShards(uuid)
            }
            result.exceptionOrNull()?.let {
                Result.failure<Int>(DiamondBankException.DatabaseException(it.message ?: "Database exception"))
            }
            Result.success(result.getOrThrow())
        }
    }

    @Suppress("unused") suspend fun getBankShards(uuid: UUID) = getShardTypeShards(uuid, ShardType.BANK)

    @Suppress("unused") suspend fun getInventoryShards(uuid: UUID) = getShardTypeShards(uuid, ShardType.INVENTORY)

    @Suppress("unused") suspend fun getEnderChestShards(uuid: UUID) = getShardTypeShards(uuid, ShardType.ENDER_CHEST)

    @Suppress("unused") suspend fun getTotalShards(uuid: UUID) = getShardTypeShards(uuid, ShardType.TOTAL)

    @Suppress("unused")
    suspend fun getAllShards(uuid: UUID): Result<PlayerShards> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return when (
            val result = DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) {
                val result = postgreSQL.getAllShards(uuid)
                result.exceptionOrNull()?.let {
                    Result.failure<Int>(DiamondBankException.DatabaseException(it.message ?: "Database exception"))
                }
                Result.success(result.getOrThrow())
            }
        ) {
            is LockResult.Acquired -> result.result

            is LockResult.Failed -> throw DiamondBankException.TransactionsLockedException
        }
    }

    private suspend fun getShardTypeShards(uuid: UUID, type: ShardType): Result<Int> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return when (
            val result =
                DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) {
                    val result = when (type) {
                        ShardType.BANK -> postgreSQL.getBankShards(uuid)
                        ShardType.INVENTORY -> postgreSQL.getInventoryShards(uuid)
                        ShardType.ENDER_CHEST -> postgreSQL.getEnderChestShards(uuid)
                        ShardType.TOTAL -> postgreSQL.getTotalShards(uuid)
                    }
                    result.exceptionOrNull()?.let {
                        Result.failure<Int>(DiamondBankException.DatabaseException(it.message ?: "Database exception"))
                    }
                    Result.success(result.getOrThrow())
                }
        ) {
            is LockResult.Acquired -> result.result

            is LockResult.Failed -> throw DiamondBankException.TransactionsLockedException
        }
    }

    @Suppress("unused")
    suspend fun getBaltop(offset: Int): Result<Map<UUID?, Int>> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        val baltop = postgreSQL.getBaltop(offset)
        if (baltop == null) {
            return Result.failure(DiamondBankException.DatabaseException("Database exception"))
        }
        return Result.success(baltop)
    }

    /**
     * WARNING: blocking, if the player has a transaction lock applied this function will wait until its released
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     */
    @Suppress("unused")
    suspend fun blockingWithdrawFromPlayer(
        uuid: UUID,
        shards: Int,
        transactionReason: String,
        notes: String?,
    ): Result<Unit> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return DiamondBankOG.transactionLock.withLockSuspend(uuid) {
            val player = Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
            if (!player.hasPlayedBefore())
                return@withLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)
            if (!player.isOnline) return@withLockSuspend Result.failure(DiamondBankException.PlayerNotOnlineException)
            val playerPlayer =
                player.player ?: return@withLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)

            val notRemoved = WithdrawHelper.withdrawFromPlayer(playerPlayer, shards)
            if (notRemoved != 0) {
                handleError(uuid, shards, null)
                return@withLockSuspend Result.failure(DiamondBankException.OtherException)
            }

            DiamondBankOG.postgreSQL.insertTransactionLog(uuid, shards, null, transactionReason, notes).getOrElse {
                handleError(uuid, shards, null, null, true)
            }

            Result.success(Unit)
        }
    }

    /**
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     */
    @Suppress("unused")
    suspend fun withdrawFromPlayer(uuid: UUID, shards: Int, transactionReason: String, notes: String?): Result<Unit> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return when (
            val result =
                DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) {
                    val player = Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
                    if (!player.hasPlayedBefore())
                        return@tryWithLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)
                    if (!player.isOnline)
                        return@tryWithLockSuspend Result.failure(DiamondBankException.PlayerNotOnlineException)
                    val playerPlayer =
                        player.player
                            ?: return@tryWithLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)

                    val notRemoved = WithdrawHelper.withdrawFromPlayer(playerPlayer, shards)
                    if (notRemoved != 0) {
                        handleError(uuid, shards, null)
                        return@tryWithLockSuspend Result.failure(DiamondBankException.OtherException)
                    }

                    DiamondBankOG.postgreSQL
                        .insertTransactionLog(uuid, shards, null, transactionReason, notes)
                        .getOrElse { handleError(uuid, shards, null, null, true) }

                    Result.success(Unit)
                }
        ) {
            is LockResult.Acquired -> result.result

            is LockResult.Failed -> throw DiamondBankException.TransactionsLockedException
        }
    }

    /**
     * WARNING: blocking, if the player has a transaction lock applied this function will wait until its released
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     */
    @Suppress("unused")
    suspend fun blockingPlayerPayPlayer(
        payerUuid: UUID,
        receiverUuid: UUID,
        shards: Int,
        transactionReason: String,
        notes: String?,
    ): Result<Unit> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return DiamondBankOG.transactionLock.withLockSuspend(payerUuid) {
            val sender = Bukkit.getPlayer(payerUuid) ?: Bukkit.getOfflinePlayer(payerUuid)
            if (!sender.hasPlayedBefore())
                return@withLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)
            if (!sender.isOnline) return@withLockSuspend Result.failure(DiamondBankException.PayerNotOnlineException)
            val senderPlayer =
                sender.player ?: return@withLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)

            val receiver = Bukkit.getPlayer(receiverUuid) ?: Bukkit.getOfflinePlayer(receiverUuid)
            if (!receiver.hasPlayedBefore())
                return@withLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)

            val notRemoved = WithdrawHelper.withdrawFromPlayer(senderPlayer, shards)
            if (notRemoved != 0) {
                handleError(payerUuid, shards, null)
                return@withLockSuspend Result.failure(DiamondBankException.OtherException)
            }

            postgreSQL.addToPlayerShards(receiver.uniqueId, shards, ShardType.BANK).getOrElse {
                handleError(sender.uniqueId, shards, null)
                return@withLockSuspend Result.failure(DiamondBankException.DatabaseException(it.message ?: "Database exception"))
            }

            DiamondBankOG.postgreSQL
                .insertTransactionLog(payerUuid, shards, receiverUuid, transactionReason, notes)
                .getOrElse { handleError(payerUuid, shards, null, receiverUuid, true) }

            Result.success(Unit)
        }
    }

    /**
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     */
    @Suppress("unused")
    suspend fun playerPayPlayer(
        payerUuid: UUID,
        receiverUuid: UUID,
        shards: Int,
        transactionReason: String,
        notes: String?,
    ): Result<Unit> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return when (
            val result =
                DiamondBankOG.transactionLock.tryWithLockSuspend(payerUuid) {
                    val sender = Bukkit.getPlayer(payerUuid) ?: Bukkit.getOfflinePlayer(payerUuid)
                    if (!sender.hasPlayedBefore())
                        return@tryWithLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)
                    if (!sender.isOnline)
                        return@tryWithLockSuspend Result.failure(DiamondBankException.PayerNotOnlineException)
                    val senderPlayer =
                        sender.player
                            ?: return@tryWithLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)

                    val receiver = Bukkit.getPlayer(receiverUuid) ?: Bukkit.getOfflinePlayer(receiverUuid)
                    if (!receiver.hasPlayedBefore())
                        return@tryWithLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)

                    val notRemoved = WithdrawHelper.withdrawFromPlayer(senderPlayer, shards)
                    if (notRemoved != 0) {
                        handleError(payerUuid, shards, null)
                        return@tryWithLockSuspend Result.failure(DiamondBankException.OtherException)
                    }

                    postgreSQL.addToPlayerShards(receiver.uniqueId, shards, ShardType.BANK).getOrElse {
                        handleError(sender.uniqueId, shards, null)
                        return@tryWithLockSuspend Result.failure(DiamondBankException.DatabaseException(it.message ?: "Database exception"))
                    }

                    DiamondBankOG.postgreSQL
                        .insertTransactionLog(payerUuid, shards, receiverUuid, transactionReason, notes)
                        .getOrElse { handleError(payerUuid, shards, null, receiverUuid, true) }

                    Result.success(Unit)
                }
        ) {
            is LockResult.Acquired -> result.result

            is LockResult.Failed -> throw DiamondBankException.TransactionsLockedException
        }
    }
}
