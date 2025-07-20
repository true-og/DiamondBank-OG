package net.trueog.diamondbankog

import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.getOrThrow
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.PostgreSQL.PlayerShards
import net.trueog.diamondbankog.PostgreSQL.ShardType
import net.trueog.diamondbankog.TransactionLock.LockResult
import org.bukkit.Bukkit

@OptIn(DelicateCoroutinesApi::class)
class DiamondBankAPIJava(private var postgreSQL: PostgreSQL) {
    /**
     * WARNING: do not run on a thread where blocking is unacceptable WARNING: lock is blocking, if the player has a
     * transaction lock applied this function will wait until its released
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(DiamondBankException.EconomyDisabledException::class, DiamondBankException.DatabaseException::class)
    @Suppress("unused")
    fun blockingAddToPlayerBankShards(uuid: UUID, shards: Int, transactionReason: String, notes: String?) {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException
        return runBlocking {
            DiamondBankOG.transactionLock.withLockSuspend(uuid) {
                postgreSQL.addToPlayerShards(uuid, shards, ShardType.BANK).getOrElse {
                    throw DiamondBankException.DatabaseException(it.message ?: "Database exception")
                }

                DiamondBankOG.postgreSQL.insertTransactionLog(uuid, shards, null, transactionReason, notes).getOrElse {
                    handleError(uuid, shards, null, null, true)
                }
            }
        }
    }

    /**
     * WARNING: do not run on a thread where blocking is unacceptable
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.TransactionsLockedException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(
        DiamondBankException.EconomyDisabledException::class,
        DiamondBankException.TransactionsLockedException::class,
        DiamondBankException.OtherException::class,
    )
    @Suppress("unused")
    fun addToPlayerBankShards(uuid: UUID, shards: Int, transactionReason: String, notes: String?) {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return runBlocking {
            when (
                val result =
                    DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) {
                        postgreSQL.addToPlayerShards(uuid, shards, ShardType.BANK).getOrElse {
                            throw DiamondBankException.DatabaseException(it.message ?: "Database exception")
                        }

                        DiamondBankOG.postgreSQL
                            .insertTransactionLog(uuid, shards, null, transactionReason, notes)
                            .getOrElse { handleError(uuid, shards, null, null, true) }
                    }
            ) {
                is LockResult.Acquired -> result.result

                is LockResult.Failed -> throw DiamondBankException.TransactionsLockedException
            }
        }
    }

    /**
     * WARNING: do not run on a thread where blocking is unacceptable WARNING: blocking, if the player has a transaction
     * lock applied this function will wait until its released
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(DiamondBankException.EconomyDisabledException::class, DiamondBankException.DatabaseException::class)
    @Suppress("unused")
    fun blockingSubtractFromPlayerBankShards(uuid: UUID, shards: Int, transactionReason: String, notes: String?) {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return runBlocking {
            DiamondBankOG.transactionLock.withLockSuspend(uuid) {
                postgreSQL.subtractFromBankShards(uuid, shards).getOrElse {
                    throw DiamondBankException.DatabaseException(it.message ?: "Database exception")
                }

                DiamondBankOG.postgreSQL.insertTransactionLog(uuid, shards, null, transactionReason, notes).getOrElse {
                    handleError(uuid, shards, null, null, true)
                }
            }
        }
    }

    /**
     * WARNING: do not run on a thread where blocking is unacceptable
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.TransactionsLockedException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(
        DiamondBankException.EconomyDisabledException::class,
        DiamondBankException.TransactionsLockedException::class,
        DiamondBankException.DatabaseException::class,
    )
    @Suppress("unused")
    fun subtractFromPlayerBankShards(uuid: UUID, shards: Int, transactionReason: String, notes: String?) {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return runBlocking {
            when (
                val result =
                    DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) {
                        postgreSQL.subtractFromBankShards(uuid, shards).getOrElse {
                            throw DiamondBankException.DatabaseException(it.message ?: "Database exception")
                        }

                        DiamondBankOG.postgreSQL
                            .insertTransactionLog(uuid, shards, null, transactionReason, notes)
                            .getOrElse { handleError(uuid, shards, null, null, true) }
                    }
            ) {
                is LockResult.Acquired -> result.result

                is LockResult.Failed -> throw DiamondBankException.TransactionsLockedException
            }
        }
    }

    /**
     * WARNING: do not run on a thread where blocking is unacceptable WARNING: blocking, if the player has a transaction
     * lock applied this function will wait until its released
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(DiamondBankException.EconomyDisabledException::class, DiamondBankException.DatabaseException::class)
    @Suppress("unused")
    fun blockingGetBankShards(uuid: UUID): Int = blockingGetShardTypeShards(uuid, ShardType.BANK)

    /**
     * WARNING: do not run on a thread where blocking is unacceptable WARNING: blocking, if the player has a transaction
     * lock applied this function will wait until its released
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(DiamondBankException.EconomyDisabledException::class, DiamondBankException.DatabaseException::class)
    @Suppress("unused")
    fun blockingGetInventoryShards(uuid: UUID): Int = blockingGetShardTypeShards(uuid, ShardType.INVENTORY)

    /**
     * WARNING: do not run on a thread where blocking is unacceptable WARNING: blocking, if the player has a transaction
     * lock applied this function will wait until its released
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(DiamondBankException.EconomyDisabledException::class, DiamondBankException.DatabaseException::class)
    @Suppress("unused")
    fun blockingGetEnderChestShards(uuid: UUID): Int = blockingGetShardTypeShards(uuid, ShardType.ENDER_CHEST)

    /**
     * WARNING: do not run on a thread where blocking is unacceptable WARNING: blocking, if the player has a transaction
     * lock applied this function will wait until its released
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(DiamondBankException.EconomyDisabledException::class, DiamondBankException.DatabaseException::class)
    @Suppress("unused")
    fun blockingGetTotalShards(uuid: UUID): Int = blockingGetShardTypeShards(uuid, ShardType.TOTAL)

    /**
     * WARNING: do not run on a thread where blocking is unacceptable WARNING: blocking, if the player has a transaction
     * lock applied this function will wait until its released
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(DiamondBankException.EconomyDisabledException::class, DiamondBankException.DatabaseException::class)
    @Suppress("unused")
    fun blockingGetAllShards(uuid: UUID): PlayerShards {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return runBlocking {
            DiamondBankOG.transactionLock.withLockSuspend(uuid) {
                val result = postgreSQL.getAllShards(uuid)
                result.exceptionOrNull()?.let {
                    throw DiamondBankException.DatabaseException(it.message ?: "Database exception")
                }
                result.getOrThrow()
            }
        }
    }

    /**
     * WARNING: do not run on a thread where blocking is unacceptable WARNING: blocking, if the player has a transaction
     * lock applied this function will wait until its released
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(DiamondBankException.EconomyDisabledException::class, DiamondBankException.DatabaseException::class)
    private fun blockingGetShardTypeShards(uuid: UUID, type: ShardType): Int {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return runBlocking {
            val result =
                DiamondBankOG.transactionLock.withLockSuspend(uuid) {
                    when (type) {
                        ShardType.BANK -> postgreSQL.getBankShards(uuid)
                        ShardType.INVENTORY -> postgreSQL.getInventoryShards(uuid)
                        ShardType.ENDER_CHEST -> postgreSQL.getEnderChestShards(uuid)
                        ShardType.TOTAL -> postgreSQL.getTotalShards(uuid)
                    }
                }
            result.exceptionOrNull()?.let {
                throw DiamondBankException.DatabaseException(it.message ?: "Database exception")
            }
            result.getOrThrow()
        }
    }

    /**
     * WARNING: do not run on a thread where blocking is unacceptable
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.TransactionsLockedException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(
        DiamondBankException.EconomyDisabledException::class,
        DiamondBankException.TransactionsLockedException::class,
        DiamondBankException.DatabaseException::class,
    )
    @Suppress("unused")
    fun getBankShards(uuid: UUID): Int = getShardTypeShards(uuid, ShardType.BANK)

    /**
     * WARNING: do not run on a thread where blocking is unacceptable
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.TransactionsLockedException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(
        DiamondBankException.EconomyDisabledException::class,
        DiamondBankException.TransactionsLockedException::class,
        DiamondBankException.DatabaseException::class,
    )
    @Suppress("unused")
    fun getInventoryShards(uuid: UUID): Int = getShardTypeShards(uuid, ShardType.INVENTORY)

    /**
     * WARNING: do not run on a thread where blocking is unacceptable
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.TransactionsLockedException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(
        DiamondBankException.EconomyDisabledException::class,
        DiamondBankException.TransactionsLockedException::class,
        DiamondBankException.DatabaseException::class,
    )
    @Suppress("unused")
    fun getEnderChestShards(uuid: UUID): Int = getShardTypeShards(uuid, ShardType.ENDER_CHEST)

    /**
     * WARNING: do not run on a thread where blocking is unacceptable
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.TransactionsLockedException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(
        DiamondBankException.EconomyDisabledException::class,
        DiamondBankException.TransactionsLockedException::class,
        DiamondBankException.DatabaseException::class,
    )
    @Suppress("unused")
    fun getTotalShards(uuid: UUID): Int = getShardTypeShards(uuid, ShardType.TOTAL)

    /**
     * WARNING: do not run on a thread where blocking is unacceptable
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.TransactionsLockedException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(
        DiamondBankException.EconomyDisabledException::class,
        DiamondBankException.TransactionsLockedException::class,
        DiamondBankException.DatabaseException::class,
    )
    @Suppress("unused")
    fun getAllShards(uuid: UUID): PlayerShards {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return runBlocking {
            when (
                val result = DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) { postgreSQL.getAllShards(uuid) }
            ) {
                is LockResult.Acquired -> {
                    result.result.exceptionOrNull()?.let {
                        throw DiamondBankException.DatabaseException(it.message ?: "Database exception")
                    }
                    result.result.getOrThrow()
                }

                is LockResult.Failed -> throw DiamondBankException.TransactionsLockedException
            }
        }
    }

    private fun getShardTypeShards(uuid: UUID, type: ShardType): Int {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return runBlocking {
            when (
                val result =
                    DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) {
                        when (type) {
                            ShardType.BANK -> postgreSQL.getBankShards(uuid)
                            ShardType.INVENTORY -> postgreSQL.getInventoryShards(uuid)
                            ShardType.ENDER_CHEST -> postgreSQL.getEnderChestShards(uuid)
                            ShardType.TOTAL -> postgreSQL.getTotalShards(uuid)
                        }
                    }
            ) {
                is LockResult.Acquired -> {
                    result.result.exceptionOrNull()?.let {
                        throw DiamondBankException.DatabaseException(it.message ?: "Database exception")
                    }
                    result.result.getOrThrow()
                }

                is LockResult.Failed -> throw DiamondBankException.TransactionsLockedException
            }
        }
    }

    /**
     * WARNING: do not run on a thread where blocking is unacceptable
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(DiamondBankException.EconomyDisabledException::class, DiamondBankException.DatabaseException::class)
    @Suppress("unused")
    fun getBaltop(offset: Int): CompletableFuture<Map<UUID?, Int>> {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return DiamondBankOG.scope.future {
            val baltop =
                postgreSQL.getBaltop(offset).getOrElse {
                    throw DiamondBankException.DatabaseException(it.message ?: "Database exception")
                }
            baltop
        }
    }

    /**
     * WARNING: do not run on a thread where blocking is unacceptable WARNING: blocking, if the player has a transaction
     * lock applied this function will wait until its released
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.InvalidPlayerException
     * @throws DiamondBankException.PlayerNotOnlineException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(
        DiamondBankException.EconomyDisabledException::class,
        DiamondBankException.InvalidPlayerException::class,
        DiamondBankException.PlayerNotOnlineException::class,
        DiamondBankException.DatabaseException::class,
    )
    @Suppress("unused")
    fun blockingWithdrawFromPlayer(
        uuid: UUID,
        shards: Int,
        transactionReason: String,
        notes: String?,
    ): CompletableFuture<Unit> {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return DiamondBankOG.scope.future {
            DiamondBankOG.transactionLock.withLockSuspend(uuid) {
                val player = Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
                if (!player.hasPlayedBefore()) throw DiamondBankException.InvalidPlayerException
                if (!player.isOnline) throw DiamondBankException.PlayerNotOnlineException
                val playerPlayer = player.player ?: throw DiamondBankException.InvalidPlayerException

                val notRemoved = WithdrawHelper.withdrawFromPlayer(playerPlayer, shards)
                if (notRemoved != 0) {
                    handleError(uuid, shards, null)
                    throw DiamondBankException.DatabaseException("Database exception")
                }

                DiamondBankOG.postgreSQL.insertTransactionLog(uuid, shards, null, transactionReason, notes).getOrElse {
                    handleError(uuid, shards, null, null, true)
                }
            }
        }
    }

    /**
     * WARNING: do not run on a thread where blocking is unacceptable
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.InvalidPlayerException
     * @throws DiamondBankException.PlayerNotOnlineException
     * @throws DiamondBankException.TransactionsLockedException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(
        DiamondBankException.EconomyDisabledException::class,
        DiamondBankException.InvalidPlayerException::class,
        DiamondBankException.PlayerNotOnlineException::class,
        DiamondBankException.TransactionsLockedException::class,
        DiamondBankException.DatabaseException::class,
    )
    @Suppress("unused")
    fun withdrawFromPlayer(
        uuid: UUID,
        shards: Int,
        transactionReason: String,
        notes: String?,
    ): CompletableFuture<Unit> {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return DiamondBankOG.scope.future {
            when (
                val result =
                    DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) {
                        val player = Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
                        if (!player.hasPlayedBefore()) throw DiamondBankException.InvalidPlayerException
                        if (!player.isOnline) throw DiamondBankException.PlayerNotOnlineException
                        val playerPlayer = player.player ?: throw DiamondBankException.InvalidPlayerException

                        val notRemoved = WithdrawHelper.withdrawFromPlayer(playerPlayer, shards)
                        if (notRemoved != 0) {
                            handleError(uuid, shards, null)
                            throw DiamondBankException.DatabaseException("Database exception")
                        }

                        DiamondBankOG.postgreSQL
                            .insertTransactionLog(uuid, shards, null, transactionReason, notes)
                            .getOrElse { handleError(uuid, shards, null, null, true) }
                    }
            ) {
                is LockResult.Acquired -> result.result

                is LockResult.Failed -> throw DiamondBankException.TransactionsLockedException
            }
        }
    }

    /**
     * WARNING: do not run on a thread where blocking is unacceptable WARNING: blocking, if the player has a transaction
     * lock applied this function will wait until its released
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.InvalidPlayerException
     * @throws DiamondBankException.PlayerNotOnlineException
     * @throws DiamondBankException.DatabaseException
     * @throws DiamondBankException.OtherException
     */
    @Throws(
        DiamondBankException.EconomyDisabledException::class,
        DiamondBankException.InvalidPlayerException::class,
        DiamondBankException.PlayerNotOnlineException::class,
        DiamondBankException.DatabaseException::class,
        DiamondBankException.OtherException::class,
    )
    @Suppress("unused")
    fun blockingPlayerPayPlayer(
        payerUuid: UUID,
        receiverUuid: UUID,
        shards: Int,
        transactionReason: String,
        notes: String?,
    ): CompletableFuture<Unit> {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return DiamondBankOG.scope.future {
            DiamondBankOG.transactionLock.withLockSuspend(payerUuid) {
                val sender = Bukkit.getPlayer(payerUuid) ?: Bukkit.getOfflinePlayer(payerUuid)
                if (!sender.hasPlayedBefore()) throw DiamondBankException.InvalidPlayerException
                if (!sender.isOnline) throw DiamondBankException.PayerNotOnlineException
                val senderPlayer = sender.player ?: throw DiamondBankException.InvalidPlayerException

                val receiver = Bukkit.getPlayer(receiverUuid) ?: Bukkit.getOfflinePlayer(receiverUuid)
                if (!receiver.hasPlayedBefore()) throw DiamondBankException.InvalidPlayerException

                val notRemoved = WithdrawHelper.withdrawFromPlayer(senderPlayer, shards)
                if (notRemoved != 0) {
                    handleError(payerUuid, shards, null)
                    throw DiamondBankException.OtherException
                }

                postgreSQL.addToPlayerShards(receiver.uniqueId, shards, ShardType.BANK).getOrElse {
                    handleError(sender.uniqueId, shards, null)
                    throw DiamondBankException.DatabaseException(it.message ?: "Database exception")
                }

                DiamondBankOG.postgreSQL
                    .insertTransactionLog(payerUuid, shards, receiverUuid, transactionReason, notes)
                    .getOrElse { handleError(payerUuid, shards, null, receiverUuid, true) }
            }
        }
    }

    /**
     * WARNING: do not run on a thread where blocking is unacceptable
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.InvalidPlayerException
     * @throws DiamondBankException.PlayerNotOnlineException
     * @throws DiamondBankException.TransactionsLockedException
     * @throws DiamondBankException.DatabaseException
     * @throws DiamondBankException.OtherException
     */
    @Throws(
        DiamondBankException.EconomyDisabledException::class,
        DiamondBankException.InvalidPlayerException::class,
        DiamondBankException.PlayerNotOnlineException::class,
        DiamondBankException.TransactionsLockedException::class,
        DiamondBankException.DatabaseException::class,
        DiamondBankException.OtherException::class,
    )
    @Suppress("unused")
    fun playerPayPlayer(
        payerUuid: UUID,
        receiverUuid: UUID,
        shards: Int,
        transactionReason: String,
        notes: String?,
    ): CompletableFuture<Unit> {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return DiamondBankOG.scope.future {
            when (
                val result =
                    DiamondBankOG.transactionLock.tryWithLockSuspend(payerUuid) {
                        val sender = Bukkit.getPlayer(payerUuid) ?: Bukkit.getOfflinePlayer(payerUuid)
                        if (!sender.hasPlayedBefore()) throw DiamondBankException.InvalidPlayerException
                        if (!sender.isOnline) throw DiamondBankException.PayerNotOnlineException
                        val senderPlayer = sender.player ?: throw DiamondBankException.InvalidPlayerException

                        val receiver = Bukkit.getPlayer(receiverUuid) ?: Bukkit.getOfflinePlayer(receiverUuid)
                        if (!receiver.hasPlayedBefore()) throw DiamondBankException.InvalidPlayerException

                        val notRemoved = WithdrawHelper.withdrawFromPlayer(senderPlayer, shards)
                        if (notRemoved != 0) {
                            handleError(payerUuid, shards, null)
                            throw DiamondBankException.OtherException
                        }

                        postgreSQL.addToPlayerShards(receiver.uniqueId, shards, ShardType.BANK).getOrElse {
                            handleError(sender.uniqueId, shards, null)
                            throw DiamondBankException.DatabaseException(it.message ?: "Database exception")
                        }

                        DiamondBankOG.postgreSQL
                            .insertTransactionLog(payerUuid, shards, receiverUuid, transactionReason, notes)
                            .getOrElse { handleError(payerUuid, shards, null, receiverUuid, true) }
                    }
            ) {
                is LockResult.Acquired -> result.result

                is LockResult.Failed -> throw DiamondBankException.TransactionsLockedException
            }
        }
    }
}
