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
                    handleError(it)
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
                    handleError(it)
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

    @Suppress("unused")
    fun registerEventListener(eventListener: PlayerBalanceChangedListener) {
        eventManager.register(eventListener)
    }
}
