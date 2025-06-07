package net.trueog.diamondbankog

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.future.future
import net.trueog.diamondbankog.PostgreSQL.PlayerShards
import net.trueog.diamondbankog.PostgreSQL.ShardType
import net.trueog.diamondbankog.TransactionLock.LockResult
import org.bukkit.Bukkit
import java.util.*
import java.util.concurrent.CompletableFuture

@OptIn(DelicateCoroutinesApi::class)
class DiamondBankAPI(private var postgreSQL: PostgreSQL) {
    sealed class DiamondBankException(message: String) : Exception(message) {
        object TransactionsLockedException : DiamondBankException("Transactions for player are locked") {
            @Suppress("unused")
            private fun readResolve(): Any = TransactionsLockedException
        }

        object EconomyDisabledException : DiamondBankException("Economy is disabled") {
            @Suppress("unused")
            private fun readResolve(): Any = EconomyDisabledException
        }

        object InvalidPlayerException : DiamondBankException("Invalid player") {
            @Suppress("unused")
            private fun readResolve(): Any = InvalidPlayerException
        }

        object PayerNotOnlineException : DiamondBankException("Payer is not online") {
            @Suppress("unused")
            private fun readResolve(): Any = PayerNotOnlineException
        }

        object PlayerNotOnlineException : DiamondBankException("Player is not online") {
            @Suppress("unused")
            private fun readResolve(): Any = PlayerNotOnlineException
        }

        object OtherException : DiamondBankException("Other exception") {
            @Suppress("unused")
            private fun readResolve(): Any = OtherException
        }
    }

    /**
     * WARNING: blocking, if the player has a transaction lock applied this function will wait until its released
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.OtherException
     */
    @Suppress("unused")
    fun blockingAddToPlayerBankShards(uuid: UUID, shards: Int): CompletableFuture<Unit> {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException
        return DiamondBankOG.scope.future {
            DiamondBankOG.transactionLock.withLockSuspend(uuid) {
                val error = postgreSQL.addToPlayerShards(uuid, shards, ShardType.BANK)
                if (error) {
                    throw DiamondBankException.OtherException
                }
                null
            }
        }
    }

    /**
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.TransactionsLockedException
     * @throws DiamondBankException.OtherException
     */
    @Suppress("unused")
    fun addToPlayerBankShards(uuid: UUID, shards: Int): CompletableFuture<Unit> {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return DiamondBankOG.scope.future {
            when (val result = DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) {
                val error = postgreSQL.addToPlayerShards(uuid, shards, ShardType.BANK)
                if (error) {
                    throw DiamondBankException.OtherException
                }
                null
            }) {
                is LockResult.Acquired -> {
                    result.result
                }

                LockResult.Failed -> {
                    throw DiamondBankException.TransactionsLockedException
                }
            }
        }
    }

    /**
     * WARNING: blocking, if the player has a transaction lock applied this function will wait until its released
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.OtherException
     */
    @Suppress("unused")
    fun blockingSubtractFromPlayerBankShards(uuid: UUID, shards: Int): CompletableFuture<Unit> {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return DiamondBankOG.scope.future {
            DiamondBankOG.transactionLock.withLockSuspend(uuid) {
                val error = postgreSQL.subtractFromPlayerShards(
                    uuid,
                    shards,
                    ShardType.BANK
                )
                if (error) {
                    throw DiamondBankException.OtherException
                }
                null
            }
        }
    }

    /**
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.TransactionsLockedException
     * @throws DiamondBankException.OtherException
     */
    @Suppress("unused")
    fun subtractFromPlayerBankShards(uuid: UUID, shards: Int): CompletableFuture<Unit> {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return DiamondBankOG.scope.future {
            when (val result = DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) {
                val error = postgreSQL.subtractFromPlayerShards(
                    uuid,
                    shards,
                    ShardType.BANK
                )
                if (error) {
                    throw DiamondBankException.OtherException
                }
                null
            }) {
                is LockResult.Acquired -> {
                    result.result
                }

                LockResult.Failed -> {
                    throw DiamondBankException.TransactionsLockedException
                }
            }
        }
    }

    /**
     * WARNING: blocking, if the player has a transaction lock applied this function will wait until its released
     * PlayerShards is always null if DiamondBankError is not null
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.OtherException
     */
    @Suppress("unused")
    fun blockingGetPlayerShards(uuid: UUID, type: ShardType): CompletableFuture<PlayerShards?> {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return DiamondBankOG.scope.future {
            DiamondBankOG.transactionLock.withLockSuspend(uuid) {
                val playerShards = postgreSQL.getPlayerShards(uuid, type)
                if (playerShards.isNeededShardTypeNull(type)) {
                    throw DiamondBankException.OtherException
                }
                playerShards
            }
        }
    }

    /**
     * PlayerShards is always null if DiamondBankError is not null
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.TransactionsLockedException
     * @throws DiamondBankException.OtherException
     */
    @Suppress("unused")
    fun getPlayerShards(uuid: UUID, type: ShardType): CompletableFuture<PlayerShards?> {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return DiamondBankOG.scope.future {
            when (val result = DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) {
                val playerShards = postgreSQL.getPlayerShards(uuid, type)
                if (playerShards.isNeededShardTypeNull(type)) {
                    throw DiamondBankException.OtherException
                }
                playerShards
            }) {
                is LockResult.Acquired -> {
                    result.result
                }

                LockResult.Failed -> {
                    throw DiamondBankException.TransactionsLockedException
                }
            }
        }
    }

    /**
     * WARNING: blocking, if the player has a transaction lock applied this function will wait until its released
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.InvalidPlayerException
     * @throws DiamondBankException.PlayerNotOnlineException
     * @throws DiamondBankException.OtherException
     */
    @Suppress("unused")
    fun blockingWithdrawFromPlayer(uuid: UUID, shards: Int): CompletableFuture<Unit> {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return DiamondBankOG.scope.future {
            DiamondBankOG.transactionLock.withLockSuspend(uuid) {
                val player = Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
                if (!player.hasPlayedBefore()) throw DiamondBankException.InvalidPlayerException
                if (!player.isOnline) throw DiamondBankException.PlayerNotOnlineException
                val playerPlayer = player.player ?: throw DiamondBankException.InvalidPlayerException

                val notRemoved = Helper.withdrawFromPlayer(playerPlayer, shards)
                if (notRemoved != 0) {
                    Helper.handleError(
                        uuid,
                        shards,
                        null
                    )
                    throw DiamondBankException.OtherException
                }
                null
            }
        }
    }

    /**
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.InvalidPlayerException
     * @throws DiamondBankException.PlayerNotOnlineException
     * @throws DiamondBankException.TransactionsLockedException
     * @throws DiamondBankException.OtherException
     */
    @Suppress("unused")
    fun withdrawFromPlayer(uuid: UUID, shards: Int): CompletableFuture<Unit> {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return DiamondBankOG.scope.future {
            when (val result = DiamondBankOG.transactionLock.tryWithLockSuspend(uuid) {
                val player = Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
                if (!player.hasPlayedBefore()) throw DiamondBankException.InvalidPlayerException
                if (!player.isOnline) throw DiamondBankException.PlayerNotOnlineException
                val playerPlayer = player.player ?: throw DiamondBankException.InvalidPlayerException

                val notRemoved = Helper.withdrawFromPlayer(playerPlayer, shards)
                if (notRemoved != 0) {
                    Helper.handleError(
                        uuid,
                        shards,
                        null
                    )
                    throw DiamondBankException.OtherException
                }
                null
            }) {
                is LockResult.Acquired -> {
                    result.result
                }

                LockResult.Failed -> {
                    throw DiamondBankException.TransactionsLockedException
                }
            }
        }
    }

    /**
     * WARNING: blocking, if the player has a transaction lock applied this function will wait until its released
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.InvalidPlayerException
     * @throws DiamondBankException.PlayerNotOnlineException
     * @throws DiamondBankException.OtherException
     */
    @Suppress("unused")
    fun blockingPlayerPayPlayer(payerUuid: UUID, receiverUuid: UUID, shards: Int): CompletableFuture<Unit> {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return DiamondBankOG.scope.future {
            DiamondBankOG.transactionLock.withLockSuspend(payerUuid) {
                val sender = Bukkit.getPlayer(payerUuid) ?: Bukkit.getOfflinePlayer(payerUuid)
                if (!sender.hasPlayedBefore()) throw DiamondBankException.InvalidPlayerException
                if (!sender.isOnline) throw DiamondBankException.PayerNotOnlineException
                val senderPlayer = sender.player ?: throw DiamondBankException.InvalidPlayerException

                val receiver = Bukkit.getPlayer(receiverUuid) ?: Bukkit.getOfflinePlayer(receiverUuid)
                if (!receiver.hasPlayedBefore()) throw DiamondBankException.InvalidPlayerException

                val notRemoved = Helper.withdrawFromPlayer(senderPlayer, shards)
                if (notRemoved != 0) {
                    Helper.handleError(
                        payerUuid,
                        shards,
                        null
                    )
                    throw DiamondBankException.OtherException
                }

                val error = postgreSQL.addToPlayerShards(
                    receiver.uniqueId,
                    shards,
                    ShardType.BANK
                )
                if (error) {
                    Helper.handleError(
                        sender.uniqueId,
                        shards,
                        null
                    )
                    throw DiamondBankException.OtherException
                }
                null
            }
        }
    }

    /**
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.InvalidPlayerException
     * @throws DiamondBankException.PlayerNotOnlineException
     * @throws DiamondBankException.TransactionsLockedException
     * @throws DiamondBankException.OtherException
     */
    @Suppress("unused")
    fun playerPayPlayer(payerUuid: UUID, receiverUuid: UUID, shards: Int): CompletableFuture<Unit> {
        if (DiamondBankOG.economyDisabled) throw DiamondBankException.EconomyDisabledException

        return DiamondBankOG.scope.future {
            when (val result = DiamondBankOG.transactionLock.tryWithLockSuspend(payerUuid) {
                val sender = Bukkit.getPlayer(payerUuid) ?: Bukkit.getOfflinePlayer(payerUuid)
                if (!sender.hasPlayedBefore()) throw DiamondBankException.InvalidPlayerException
                if (!sender.isOnline) throw DiamondBankException.PayerNotOnlineException
                val senderPlayer = sender.player ?: throw DiamondBankException.InvalidPlayerException

                val receiver = Bukkit.getPlayer(receiverUuid) ?: Bukkit.getOfflinePlayer(receiverUuid)
                if (!receiver.hasPlayedBefore()) throw DiamondBankException.InvalidPlayerException

                val notRemoved = Helper.withdrawFromPlayer(senderPlayer, shards)
                if (notRemoved != 0) {
                    Helper.handleError(
                        payerUuid,
                        shards,
                        null
                    )
                    throw DiamondBankException.OtherException
                }

                val error = postgreSQL.addToPlayerShards(
                    receiver.uniqueId,
                    shards,
                    ShardType.BANK
                )
                if (error) {
                    Helper.handleError(
                        sender.uniqueId,
                        shards,
                        null
                    )
                    throw DiamondBankException.OtherException
                }
                null
            }) {
                is LockResult.Acquired -> {
                    result.result
                }

                LockResult.Failed -> {
                    throw DiamondBankException.TransactionsLockedException
                }
            }
        }
    }
}