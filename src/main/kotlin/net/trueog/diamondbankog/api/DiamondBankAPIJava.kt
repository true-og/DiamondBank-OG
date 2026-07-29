package net.trueog.diamondbankog.api

import java.util.*
import kotlinx.coroutines.runBlocking
import net.trueog.diamondbankog.DiamondBankException
import net.trueog.diamondbankog.DiamondBankException.*
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.DiamondBankOG.Companion.eventManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.transactionLock
import net.trueog.diamondbankog.balance.shard.PlayerShards
import net.trueog.diamondbankog.balance.shard.ShardType
import net.trueog.diamondbankog.transaction.CommonOperations
import net.trueog.diamondbankog.transaction.InventoryLockExtensions.withLockSuspend
import net.trueog.diamondbankog.transaction.InventorySnapshot
import net.trueog.diamondbankog.util.ErrorHandler.handleError
import net.trueog.diamondbankog.util.MainThreadBlock.runOnMainThread
import org.bukkit.Bukkit

class DiamondBankAPIJava {
    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released.
     *
     * This function also blocks for the database call, this is so you don't have to manually run .get() on a
     * CompletableFuture
     *
     * @param shards must not be negative
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     * @throws DiamondBankException.EconomyDisabledException
     */
    @Throws(EconomyDisabledException::class)
    @Suppress("unused")
    fun addToPlayerBankShards(uuid: UUID, shards: Long, transactionReason: String, notes: String?) {
        require(shards >= 0) { "shards must not be negative" }
        if (economyDisabled) throw EconomyDisabledException()

        return runBlocking {
            transactionLock.withLockSuspend(uuid) {
                balanceManager.addToBankShards(uuid, shards).getOrElse {
                    handleError(it)
                    throw EconomyDisabledException()
                }

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
     * @param shards must not be negative
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.InsufficientBalanceException
     */
    @Throws(EconomyDisabledException::class, InsufficientBalanceException::class)
    @Suppress("unused")
    fun subtractFromPlayerBankShards(uuid: UUID, shards: Long, transactionReason: String, notes: String?) {
        require(shards >= 0) { "shards must not be negative" }
        if (economyDisabled) throw EconomyDisabledException()

        return runBlocking {
            transactionLock.withLockSuspend(uuid) {
                balanceManager.subtractFromBankShards(uuid, shards).getOrElse {
                    if (it is InsufficientBalanceException) throw it
                    handleError(it)
                    throw EconomyDisabledException()
                }

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
     */
    @Throws(EconomyDisabledException::class)
    @Suppress("unused")
    fun getBankShards(uuid: UUID): Long = getShardTypeShards(uuid, ShardType.BANK)

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * This function also blocks for the database call, this is so you don't have to manually run .get() on a
     * CompletableFuture
     *
     * @throws DiamondBankException.EconomyDisabledException
     */
    @Throws(EconomyDisabledException::class)
    @Suppress("unused")
    fun getInventoryShards(uuid: UUID): Long = getShardTypeShards(uuid, ShardType.INVENTORY)

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * This function also blocks for the database call, this is so you don't have to manually run .get() on a
     * CompletableFuture
     *
     * @throws DiamondBankException.EconomyDisabledException
     */
    @Throws(EconomyDisabledException::class)
    @Suppress("unused")
    fun getEnderChestShards(uuid: UUID): Long = getShardTypeShards(uuid, ShardType.ENDER_CHEST)

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * This function also blocks for the database call, this is so you don't have to manually run .get() on a
     * CompletableFuture
     *
     * @throws DiamondBankException.EconomyDisabledException
     */
    @Throws(EconomyDisabledException::class)
    @Suppress("unused")
    fun getTotalShards(uuid: UUID): Long = getShardTypeShards(uuid, ShardType.TOTAL)

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * This function also blocks for the database call, this is so you don't have to manually run .get() on a
     * CompletableFuture
     *
     * @throws DiamondBankException.EconomyDisabledException
     */
    @Throws(EconomyDisabledException::class)
    @Suppress("unused")
    fun getAllShards(uuid: UUID): PlayerShards {
        if (economyDisabled) throw EconomyDisabledException()

        return runBlocking {
            transactionLock.withLockSuspend(uuid) {
                balanceManager.getAllShards(uuid).getOrElse {
                    handleError(it)
                    throw EconomyDisabledException()
                }
            }
        }
    }

    @Throws(EconomyDisabledException::class)
    private fun getShardTypeShards(uuid: UUID, type: ShardType): Long {
        if (economyDisabled) throw EconomyDisabledException()

        return runBlocking {
            transactionLock
                .withLockSuspend(uuid) {
                    when (type) {
                        ShardType.BANK -> balanceManager.getBankShards(uuid)
                        ShardType.INVENTORY -> balanceManager.getInventoryShards(uuid)
                        ShardType.ENDER_CHEST -> balanceManager.getEnderChestShards(uuid)
                        ShardType.TOTAL -> balanceManager.getTotalShards(uuid)
                    }
                }
                .getOrElse {
                    handleError(it)
                    throw EconomyDisabledException()
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
     */
    @Throws(EconomyDisabledException::class)
    @Suppress("unused")
    fun getBaltop(offset: Int): Map<UUID?, Long> {
        if (economyDisabled) throw EconomyDisabledException()

        return runBlocking {
            balanceManager.getBaltop(offset).getOrElse {
                handleError(it)
                throw EconomyDisabledException()
            }
        }
    }

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * This function also blocks for the database call, this is so you don't have to manually run .get() on a
     * CompletableFuture
     *
     * @param shards must not be negative
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.InvalidPlayerException
     * @throws DiamondBankException.PlayerNotOnlineException
     * @throws DiamondBankException.InsufficientFundsException
     */
    @Throws(
        EconomyDisabledException::class,
        InvalidPlayerException::class,
        PlayerNotOnlineException::class,
        InsufficientFundsException::class,
    )
    @Suppress("unused")
    fun consumeFromPlayer(uuid: UUID, shards: Long, transactionReason: String, notes: String?) {
        require(shards >= 0) { "shards must not be negative" }
        if (economyDisabled) throw EconomyDisabledException()

        return runBlocking {
            transactionLock.withLockSuspend(uuid) {
                val player = Bukkit.getPlayer(uuid) ?: throw PlayerNotOnlineException()
                if (!player.hasPlayedBefore()) throw InvalidPlayerException()

                player.inventory.withLockSuspend {
                    val inventorySnapshot = runOnMainThread { InventorySnapshot.from(player.inventory, balanceManager) }

                    val toSubtract =
                        CommonOperations.consume(player.uniqueId, shards, inventorySnapshot).getOrElse {
                            if (it is DatabaseException) {
                                handleError(it)
                                throw EconomyDisabledException()
                            }
                            throw it
                        }

                    balanceManager.subtractFromBankShards(player.uniqueId, toSubtract).getOrElse {
                        if (it is InsufficientBalanceException) throw it
                        handleError(it)
                        throw EconomyDisabledException()
                    }

                    runOnMainThread { inventorySnapshot.restoreTo(player.inventory) }
                }

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
     * @param shards must not be negative
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     * @throws DiamondBankException.EconomyDisabledException
     * @throws DiamondBankException.InvalidPlayerException
     * @throws DiamondBankException.SenderNotOnlineException
     * @throws DiamondBankException.InsufficientFundsException
     */
    @Throws(
        EconomyDisabledException::class,
        InvalidPlayerException::class,
        SenderNotOnlineException::class,
        InsufficientFundsException::class,
    )
    @Suppress("unused")
    fun playerPayPlayer(senderUuid: UUID, receiverUuid: UUID, shards: Long, transactionReason: String, notes: String?) {
        require(shards >= 0) { "shards must not be negative" }
        if (economyDisabled) throw EconomyDisabledException()

        return runBlocking {
            transactionLock.withLockSuspend(senderUuid) {
                val sender = Bukkit.getPlayer(senderUuid) ?: throw SenderNotOnlineException()
                if (!sender.hasPlayedBefore()) throw InvalidPlayerException()

                val receiver = Bukkit.getPlayer(receiverUuid) ?: Bukkit.getOfflinePlayer(receiverUuid)
                if (!receiver.hasPlayedBefore()) throw InvalidPlayerException()

                sender.inventory.withLockSuspend {
                    val inventorySnapshot = runOnMainThread { InventorySnapshot.from(sender.inventory, balanceManager) }

                    val shardsToSubtractFromSender =
                        CommonOperations.consume(sender.uniqueId, shards, inventorySnapshot).getOrElse {
                            if (it is DatabaseException) {
                                handleError(it)
                                throw EconomyDisabledException()
                            }
                            throw it
                        }

                    balanceManager
                        .transferBankShards(senderUuid, receiverUuid, shardsToSubtractFromSender, shards)
                        .getOrElse {
                            if (it is InsufficientBalanceException) throw it
                            handleError(it)
                            throw EconomyDisabledException()
                        }

                    runOnMainThread { inventorySnapshot.restoreTo(sender.inventory) }
                }

                balanceManager
                    .insertTransactionLog(senderUuid, shards, receiverUuid, transactionReason, notes)
                    .getOrElse { handleError(it) }
            }
        }
    }

    /** Register a PlayerBalanceChangedListener to listen for player balance changes */
    @Suppress("unused")
    fun registerEventListener(eventListener: PlayerBalanceChangedListener) {
        eventManager.register(eventListener)
    }

    /**
     * WARNING: This function can throw a MoreThanOneDecimalDigitRuntimeException, make sure it's checked when handling
     * arbitrary input
     *
     * Converts a Diamond double to Shards
     */
    @Suppress("unused") fun diamondsToShards(diamonds: Double) = diamondsToShards(diamonds.toFloat())

    /**
     * WARNING: This function can throw a MoreThanOneDecimalDigitRuntimeException, make sure it's checked when handling
     * arbitrary input
     *
     * Converts a Diamond float to Shards
     */
    @Suppress("unused") fun diamondsToShards(diamonds: Float) = CommonOperations.diamondsToShards(diamonds).getOrThrow()

    /** Converts Shards into a formatted Diamonds string */
    @Suppress("unused") fun shardsToDiamonds(shards: Long) = CommonOperations.shardsToDiamonds(shards)
}
