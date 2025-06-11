package net.trueog.diamondbankog

import kotlinx.coroutines.DelicateCoroutinesApi
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.PostgreSQL.PlayerShards
import net.trueog.diamondbankog.PostgreSQL.ShardType
import net.trueog.diamondbankog.TransactionLock.LockResult
import org.bukkit.Bukkit
import java.util.*

@OptIn(DelicateCoroutinesApi::class)
class DiamondBankAPIKotlin(private var postgreSQL: PostgreSQL) {
    /**
     * WARNING: blocking, if the player has a transaction lock applied this function will wait until its released
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     */
    @Suppress("unused")
    suspend fun blockingAddToPlayerBankShards(
        uuid: UUID,
        shards: Int,
        transactionReason: String,
        notes: String?
    ): Result<Unit> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)
        return DiamondBankOG.transactionLock.withLockSuspend(uuid) {
            val error = postgreSQL.addToPlayerShards(uuid, shards, ShardType.BANK)
            if (error) {
                return@withLockSuspend Result.failure(DiamondBankException.OtherException)
            }

            val transactionLogError = DiamondBankOG.postgreSQL.insertTransactionLog(
                uuid,
                shards,
                null,
                transactionReason,
                notes
            )
            if (transactionLogError) {
                handleError(
                    uuid,
                    shards,
                    null,
                    null,
                    true
                )
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
        notes: String?
    ): Result<Unit> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return when (val result = DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) {
            val error = postgreSQL.addToPlayerShards(uuid, shards, ShardType.BANK)
            if (error) {
                return@tryWithLockSuspend Result.failure(DiamondBankException.OtherException)
            }

            val transactionLogError = DiamondBankOG.postgreSQL.insertTransactionLog(
                uuid,
                shards,
                null,
                transactionReason,
                notes
            )
            if (transactionLogError) {
                handleError(
                    uuid,
                    shards,
                    null,
                    null,
                    true
                )
            }

            Result.success(Unit)
        }) {
            is LockResult.Acquired -> {
                result.result
            }

            LockResult.Failed -> {
                return Result.failure(DiamondBankException.TransactionsLockedException)
            }
        }
    }

    /**
     * WARNING: blocking, if the player has a transaction lock applied this function will wait until its released
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     */
    @Suppress("unused")
    suspend fun blockingSubtractFromPlayerBankShards(
        uuid: UUID,
        shards: Int,
        transactionReason: String,
        notes: String?
    ): Result<Unit> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return DiamondBankOG.transactionLock.withLockSuspend(uuid) {
            val error = postgreSQL.subtractFromPlayerShards(
                uuid,
                shards,
                ShardType.BANK
            )
            if (error) {
                return@withLockSuspend Result.failure(DiamondBankException.OtherException)
            }

            val transactionLogError = DiamondBankOG.postgreSQL.insertTransactionLog(
                uuid,
                shards,
                null,
                transactionReason,
                notes
            )
            if (transactionLogError) {
                handleError(
                    uuid,
                    shards,
                    null,
                    null,
                    true
                )
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
        notes: String?
    ): Result<Unit> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return when (val result = DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) {
            val error = postgreSQL.subtractFromPlayerShards(
                uuid,
                shards,
                ShardType.BANK
            )
            if (error) {
                return@tryWithLockSuspend Result.failure(DiamondBankException.OtherException)
            }

            val transactionLogError = DiamondBankOG.postgreSQL.insertTransactionLog(
                uuid,
                shards,
                null,
                transactionReason,
                notes
            )
            if (transactionLogError) {
                handleError(
                    uuid,
                    shards,
                    null,
                    null,
                    true
                )
            }

            Result.success(Unit)
        }) {
            is LockResult.Acquired -> {
                result.result
            }

            LockResult.Failed -> {
                Result.failure(DiamondBankException.TransactionsLockedException)
            }
        }
    }

    /**
     * WARNING: blocking, if the player has a transaction lock applied this function will wait until its released
     */
    @Suppress("unused")
    suspend fun blockingGetPlayerShards(uuid: UUID, type: ShardType): Result<PlayerShards> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return DiamondBankOG.transactionLock.withLockSuspend(uuid) {
            val playerShards = postgreSQL.getPlayerShards(uuid, type)
            if (playerShards.isNeededShardTypeNull(type)) {
                return@withLockSuspend Result.failure(DiamondBankException.OtherException)
            }

            Result.success(playerShards)
        }
    }

    @Suppress("unused")
    suspend fun getPlayerShards(uuid: UUID, type: ShardType): Result<PlayerShards> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return when (val result = DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) {
            val playerShards = postgreSQL.getPlayerShards(uuid, type)
            if (playerShards.isNeededShardTypeNull(type)) {
                return@tryWithLockSuspend Result.failure(DiamondBankException.OtherException)
            }
            Result.success(playerShards)
        }) {
            is LockResult.Acquired -> {
                result.result
            }

            LockResult.Failed -> {
                return Result.failure(DiamondBankException.TransactionsLockedException)
            }
        }
    }

    @Suppress("unused")
    suspend fun getBaltop(offset: Int): Result<Map<UUID?, Int>> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        val baltop = postgreSQL.getBaltop(offset)
        if (baltop == null) {
            return Result.failure(DiamondBankException.OtherException)
        }
        return Result.success(baltop)
    }

    /**
     * WARNING: blocking, if the player has a transaction lock applied this function will wait until its released
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     */
    @Suppress("unused")
    suspend fun blockingWithdrawFromPlayer(
        uuid: UUID,
        shards: Int,
        transactionReason: String,
        notes: String?
    ): Result<Unit> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return DiamondBankOG.transactionLock.withLockSuspend(uuid) {
            val player = Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
            if (!player.hasPlayedBefore()) return@withLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)
            if (!player.isOnline) return@withLockSuspend Result.failure(DiamondBankException.PlayerNotOnlineException)
            val playerPlayer =
                player.player ?: return@withLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)

            val notRemoved = WithdrawHelper.withdrawFromPlayer(playerPlayer, shards)
            if (notRemoved != 0) {
                handleError(
                    uuid,
                    shards,
                    null
                )
                return@withLockSuspend Result.failure(DiamondBankException.OtherException)
            }

            val transactionLogError = DiamondBankOG.postgreSQL.insertTransactionLog(
                uuid,
                shards,
                null,
                transactionReason,
                notes
            )
            if (transactionLogError) {
                handleError(
                    uuid,
                    shards,
                    null,
                    null,
                    true
                )
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

        return when (val result = DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) {
            val player = Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
            if (!player.hasPlayedBefore()) return@tryWithLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)
            if (!player.isOnline) return@tryWithLockSuspend Result.failure(DiamondBankException.PlayerNotOnlineException)
            val playerPlayer =
                player.player ?: return@tryWithLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)

            val notRemoved = WithdrawHelper.withdrawFromPlayer(playerPlayer, shards)
            if (notRemoved != 0) {
                handleError(
                    uuid,
                    shards,
                    null
                )
                return@tryWithLockSuspend Result.failure(DiamondBankException.OtherException)
            }

            val transactionLogError = DiamondBankOG.postgreSQL.insertTransactionLog(
                uuid,
                shards,
                null,
                transactionReason,
                notes
            )
            if (transactionLogError) {
                handleError(
                    uuid,
                    shards,
                    null,
                    null,
                    true
                )
            }

            Result.success(Unit)
        }) {
            is LockResult.Acquired -> {
                result.result
            }

            LockResult.Failed -> {
                Result.failure(DiamondBankException.TransactionsLockedException)
            }
        }
    }

    /**
     * WARNING: blocking, if the player has a transaction lock applied this function will wait until its released
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     */
    @Suppress("unused")
    suspend fun blockingPlayerPayPlayer(
        payerUuid: UUID,
        receiverUuid: UUID,
        shards: Int,
        transactionReason: String,
        notes: String?
    ): Result<Unit> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return DiamondBankOG.transactionLock.withLockSuspend(payerUuid) {
            val sender = Bukkit.getPlayer(payerUuid) ?: Bukkit.getOfflinePlayer(payerUuid)
            if (!sender.hasPlayedBefore()) return@withLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)
            if (!sender.isOnline) return@withLockSuspend Result.failure(DiamondBankException.PayerNotOnlineException)
            val senderPlayer =
                sender.player ?: return@withLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)

            val receiver = Bukkit.getPlayer(receiverUuid) ?: Bukkit.getOfflinePlayer(receiverUuid)
            if (!receiver.hasPlayedBefore()) return@withLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)

            val notRemoved = WithdrawHelper.withdrawFromPlayer(senderPlayer, shards)
            if (notRemoved != 0) {
                handleError(
                    payerUuid,
                    shards,
                    null
                )
                return@withLockSuspend Result.failure(DiamondBankException.OtherException)
            }

            val error = postgreSQL.addToPlayerShards(
                receiver.uniqueId,
                shards,
                ShardType.BANK
            )
            if (error) {
                handleError(
                    sender.uniqueId,
                    shards,
                    null
                )
                return@withLockSuspend Result.failure(DiamondBankException.OtherException)
            }

            val transactionLogError = DiamondBankOG.postgreSQL.insertTransactionLog(
                payerUuid,
                shards,
                receiverUuid,
                transactionReason,
                notes
            )
            if (transactionLogError) {
                handleError(
                    payerUuid,
                    shards,
                    null,
                    receiverUuid,
                    true
                )
            }

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
        notes: String?
    ): Result<Unit> {
        if (DiamondBankOG.economyDisabled) return Result.failure(DiamondBankException.EconomyDisabledException)

        return when (val result = DiamondBankOG.transactionLock.tryWithLockSuspend(payerUuid) {
            val sender = Bukkit.getPlayer(payerUuid) ?: Bukkit.getOfflinePlayer(payerUuid)
            if (!sender.hasPlayedBefore()) return@tryWithLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)
            if (!sender.isOnline) return@tryWithLockSuspend Result.failure(DiamondBankException.PayerNotOnlineException)
            val senderPlayer =
                sender.player ?: return@tryWithLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)

            val receiver = Bukkit.getPlayer(receiverUuid) ?: Bukkit.getOfflinePlayer(receiverUuid)
            if (!receiver.hasPlayedBefore()) return@tryWithLockSuspend Result.failure(DiamondBankException.InvalidPlayerException)

            val notRemoved = WithdrawHelper.withdrawFromPlayer(senderPlayer, shards)
            if (notRemoved != 0) {
                handleError(
                    payerUuid,
                    shards,
                    null
                )
                return@tryWithLockSuspend Result.failure(DiamondBankException.OtherException)
            }

            val error = postgreSQL.addToPlayerShards(
                receiver.uniqueId,
                shards,
                ShardType.BANK
            )
            if (error) {
                handleError(
                    sender.uniqueId,
                    shards,
                    null
                )
                return@tryWithLockSuspend Result.failure(DiamondBankException.OtherException)
            }

            val transactionLogError = DiamondBankOG.postgreSQL.insertTransactionLog(
                payerUuid,
                shards,
                receiverUuid,
                transactionReason,
                notes
            )
            if (transactionLogError) {
                handleError(
                    payerUuid,
                    shards,
                    null,
                    receiverUuid,
                    true
                )
            }

            Result.success(Unit)
        }) {
            is LockResult.Acquired -> {
                result.result
            }

            LockResult.Failed -> {
                Result.failure(DiamondBankException.TransactionsLockedException)
            }
        }
    }
}