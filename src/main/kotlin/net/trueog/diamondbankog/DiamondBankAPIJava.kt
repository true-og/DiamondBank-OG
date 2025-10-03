package net.trueog.diamondbankog

import java.util.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.runBlocking
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
class DiamondBankAPIJava() {
    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released.
     *
     * This function also blocks for the database call, this is so you don't have to manually run .get() on a
     * CompletableFuture
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(EconomyDisabledException::class, DatabaseException::class)
    @Suppress("unused")
    fun addToPlayerBankShards(uuid: UUID, shards: Long, transactionReason: String, notes: String?) {
        if (economyDisabled) throw EconomyDisabledException()

        return runBlocking {
            transactionLock.withLockSuspend(uuid) {
                balanceManager.addToPlayerShards(uuid, shards, ShardType.BANK).getOrElse { throw it }

                balanceManager.insertTransactionLog(uuid, shards, null, transactionReason, notes).getOrElse {
                    handleError(uuid, shards, null, null, true)
                }
            }
        }
    }

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * This function also blocks for the database call, this is so you don't have to manually run .get() on a
     * CompletableFuture
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.DatabaseException
     * @throws DiamondBankException.InsufficientBalanceException
     */
    @Throws(EconomyDisabledException::class, DatabaseException::class, InsufficientBalanceException::class)
    @Suppress("unused")
    fun subtractFromPlayerBankShards(uuid: UUID, shards: Long, transactionReason: String, notes: String?) {
        if (economyDisabled) throw EconomyDisabledException()

        return runBlocking {
            transactionLock.withLockSuspend(uuid) {
                balanceManager.subtractFromBankShards(uuid, shards).getOrElse { throw it }

                balanceManager.insertTransactionLog(uuid, shards, null, transactionReason, notes).getOrElse {
                    handleError(uuid, shards, null, null, true)
                }
            }
        }
    }

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * This function also blocks for the database call, this is so you don't have to manually run .get() on a
     * CompletableFuture
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(EconomyDisabledException::class, DatabaseException::class)
    @Suppress("unused")
    fun getBankShards(uuid: UUID): Long = getShardTypeShards(uuid, ShardType.BANK)

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * This function also blocks for the database call, this is so you don't have to manually run .get() on a
     * CompletableFuture
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(EconomyDisabledException::class, DatabaseException::class)
    @Suppress("unused")
    fun getInventoryShards(uuid: UUID): Long = getShardTypeShards(uuid, ShardType.INVENTORY)

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * This function also blocks for the database call, this is so you don't have to manually run .get() on a
     * CompletableFuture
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(EconomyDisabledException::class, DatabaseException::class)
    @Suppress("unused")
    fun getEnderChestShards(uuid: UUID): Long = getShardTypeShards(uuid, ShardType.ENDER_CHEST)

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * This function also blocks for the database call, this is so you don't have to manually run .get() on a
     * CompletableFuture
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(EconomyDisabledException::class, DatabaseException::class)
    @Suppress("unused")
    fun getTotalShards(uuid: UUID): Long = getShardTypeShards(uuid, ShardType.TOTAL)

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * This function also blocks for the database call, this is so you don't have to manually run .get() on a
     * CompletableFuture
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(EconomyDisabledException::class, DatabaseException::class)
    @Suppress("unused")
    fun getAllShards(uuid: UUID): PlayerShards {
        if (economyDisabled) throw EconomyDisabledException()

        return runBlocking {
            transactionLock.withLockSuspend(uuid) {
                val result = balanceManager.getAllShards(uuid)
                result.getOrThrow()
            }
        }
    }

    @Throws(EconomyDisabledException::class, DatabaseException::class)
    private fun getShardTypeShards(uuid: UUID, type: ShardType): Long {
        if (economyDisabled) throw EconomyDisabledException()

        return runBlocking {
            val result =
                transactionLock.withLockSuspend(uuid) {
                    when (type) {
                        ShardType.BANK -> balanceManager.getBankShards(uuid)
                        ShardType.INVENTORY -> balanceManager.getInventoryShards(uuid)
                        ShardType.ENDER_CHEST -> balanceManager.getEnderChestShards(uuid)
                        ShardType.TOTAL -> balanceManager.getTotalShards(uuid)
                    }
                }
            result.getOrThrow()
        }
    }

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * This function also blocks for the database call, this is so you don't have to manually run .get() on a
     * CompletableFuture
     *
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(EconomyDisabledException::class, DatabaseException::class)
    @Suppress("unused")
    fun getBaltop(offset: Int): Map<UUID?, Long> {
        if (economyDisabled) throw EconomyDisabledException()

        return runBlocking { balanceManager.getBaltop(offset).getOrElse { throw it } }
    }

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * This function also blocks for the database call, this is so you don't have to manually run .get() on a
     * CompletableFuture
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.InvalidPlayerException
     * @throws DiamondBankException.PlayerNotOnlineException
     * @throws DiamondBankException.InsufficientBalanceException
     * @throws DiamondBankException.CouldNotRemoveEnoughException
     * @throws DiamondBankException.DatabaseException
     */
    @Throws(
        EconomyDisabledException::class,
        InvalidPlayerException::class,
        PlayerNotOnlineException::class,
        InsufficientBalanceException::class,
        CouldNotRemoveEnoughException::class,
        DatabaseException::class,
    )
    @Suppress("unused")
    fun withdrawFromPlayer(uuid: UUID, shards: Long, transactionReason: String, notes: String?) {
        if (economyDisabled) throw EconomyDisabledException()

        return runBlocking {
            transactionLock.withLockSuspend(uuid) {
                val player = Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
                if (!player.hasPlayedBefore()) throw InvalidPlayerException()
                if (!player.isOnline) throw PlayerNotOnlineException()
                val playerPlayer = player.player ?: throw InvalidPlayerException()

                val balance = balanceManager.getTotalShards(uuid).getOrElse { throw it }
                if (balance - shards < 0) {
                    throw InsufficientBalanceException(balance)
                }

                WithdrawHelper.withdrawFromPlayer(playerPlayer, shards).getOrElse {
                    handleError(uuid, shards, null)
                    throw it
                }

                balanceManager.insertTransactionLog(uuid, shards, null, transactionReason, notes).getOrElse {
                    handleError(uuid, shards, null, null, true)
                }
            }
        }
    }

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * This function also blocks for the database call, this is so you don't have to manually run .get() on a
     * CompletableFuture
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.InvalidPlayerException
     * @throws DiamondBankException.PayerNotOnlineException
     * @throws DiamondBankException.DatabaseException
     * @throws DiamondBankException.InsufficientBalanceException
     * @throws DiamondBankException.CouldNotRemoveEnoughException
     * @throws DiamondBankException.OtherException
     */
    @Throws(
        EconomyDisabledException::class,
        InvalidPlayerException::class,
        PayerNotOnlineException::class,
        DatabaseException::class,
        InsufficientBalanceException::class,
        CouldNotRemoveEnoughException::class,
        OtherException::class,
    )
    @Suppress("unused")
    fun playerPayPlayer(payerUuid: UUID, receiverUuid: UUID, shards: Long, transactionReason: String, notes: String?) {
        if (economyDisabled) throw EconomyDisabledException()

        return runBlocking {
            transactionLock.withLockSuspend(payerUuid) {
                val payer = Bukkit.getPlayer(payerUuid) ?: Bukkit.getOfflinePlayer(payerUuid)
                if (!payer.hasPlayedBefore()) throw InvalidPlayerException()
                if (!payer.isOnline) throw PayerNotOnlineException()
                val payerPlayer = payer.player ?: throw InvalidPlayerException()

                val receiver = Bukkit.getPlayer(receiverUuid) ?: Bukkit.getOfflinePlayer(receiverUuid)
                if (!receiver.hasPlayedBefore()) throw InvalidPlayerException()

                val balance = balanceManager.getTotalShards(payerUuid).getOrElse { throw it }
                if (balance - shards < 0) {
                    throw InsufficientBalanceException(balance)
                }

                WithdrawHelper.withdrawFromPlayer(payerPlayer, shards).getOrElse {
                    handleError(payerUuid, shards, null)
                    throw it
                }

                balanceManager.addToPlayerShards(receiverUuid, shards, ShardType.BANK).getOrElse {
                    handleError(payerUuid, shards, null)
                    throw it
                }

                balanceManager
                    .insertTransactionLog(payerUuid, shards, receiverUuid, transactionReason, notes)
                    .getOrElse { handleError(payerUuid, shards, null, receiverUuid, true) }
            }
        }
    }

    @Suppress("unused")
    fun registerEventListener(eventListener: PlayerBalanceChangedListener) {
        eventManager.register(eventListener)
    }
}
