package net.trueog.diamondbankog.api

import java.util.*
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
        shards: ULong,
        transactionReason: String,
        notes: String?,
    ): Result<Unit> {
        require(shards <= Long.MAX_VALUE.toULong()) { "shards must not be above the max value of a Long" }
        if (economyDisabled) return Result.failure(EconomyDisabledException())

        return transactionLock.withLockSuspend(uuid) {
            balanceManager.addToBankShards(uuid, shards.toLong()).getOrElse {
                handleError(it)
                return@withLockSuspend Result.failure(EconomyDisabledException())
            }

            balanceManager.insertTransactionLog(uuid, shards.toLong(), null, transactionReason, notes).getOrElse {
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
        shards: ULong,
        transactionReason: String,
        notes: String?,
    ): Result<Unit> {
        require(shards <= Long.MAX_VALUE.toULong()) { "shards must not be above the max value of a Long" }
        if (economyDisabled) return Result.failure(EconomyDisabledException())

        return transactionLock.withLockSuspend(uuid) {
            balanceManager.subtractFromBankShards(uuid, shards.toLong()).getOrElse {
                if (it is InsufficientBalanceException) return@withLockSuspend Result.failure(it)
                handleError(it)
                return@withLockSuspend Result.failure(EconomyDisabledException())
            }

            balanceManager.insertTransactionLog(uuid, shards.toLong(), null, transactionReason, notes).getOrElse {
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

        return transactionLock.withLockSuspend(uuid) {
            Result.success(
                balanceManager.getAllShards(uuid).getOrElse {
                    handleError(it)
                    return@withLockSuspend Result.failure(EconomyDisabledException())
                }
            )
        }
    }

    private suspend fun getShardTypeShards(uuid: UUID, type: ShardType): Result<Long> {
        if (economyDisabled) return Result.failure(EconomyDisabledException())

        return transactionLock.withLockSuspend(uuid) {
            Result.success(
                when (type) {
                    ShardType.BANK -> balanceManager.getBankShards(uuid)
                    ShardType.INVENTORY -> balanceManager.getInventoryShards(uuid)
                    ShardType.ENDER_CHEST -> balanceManager.getEnderChestShards(uuid)
                    ShardType.TOTAL -> balanceManager.getTotalShards(uuid)
                }.getOrElse {
                    handleError(it)
                    return@withLockSuspend Result.failure(EconomyDisabledException())
                }
            )
        }
    }

    @Suppress("unused")
    suspend fun getBaltop(offset: Int): Result<Map<UUID?, Long>> {
        if (economyDisabled) return Result.failure(EconomyDisabledException())

        return Result.success(
            balanceManager.getBaltop(offset).getOrElse {
                handleError(it)
                return Result.failure(EconomyDisabledException())
            }
        )
    }

    /**
     * WARNING: if the player has a transaction lock applied this function will wait until its released
     *
     * @param transactionReason the reason for this transaction for in the transaction log
     * @param notes any specifics for this transaction that may be nice to know for in the transaction log
     */
    @Suppress("unused")
    suspend fun consumeFromPlayer(uuid: UUID, shards: ULong, transactionReason: String, notes: String?): Result<Unit> {
        require(shards <= Long.MAX_VALUE.toULong()) { "shards must not be above the max value of a Long" }
        if (economyDisabled) return Result.failure(EconomyDisabledException())

        return transactionLock.withLockSuspend(uuid) {
            val player = Bukkit.getPlayer(uuid) ?: return@withLockSuspend Result.failure(PlayerNotOnlineException())
            if (!player.hasPlayedBefore()) return@withLockSuspend Result.failure(InvalidPlayerException())

            player.inventory
                .withLockSuspend {
                    val inventorySnapshot = runOnMainThread { InventorySnapshot.from(player.inventory, balanceManager) }

                    val toSubtract =
                        CommonOperations.consume(player.uniqueId, shards.toLong(), inventorySnapshot).getOrElse {
                            if (it is DatabaseException) {
                                handleError(it)
                                return@withLockSuspend Result.failure(EconomyDisabledException())
                            }
                            return@withLockSuspend Result.failure(it)
                        }

                    balanceManager.subtractFromBankShards(player.uniqueId, toSubtract).getOrElse {
                        if (it is InsufficientBalanceException) return@withLockSuspend Result.failure(it)
                        handleError(it)
                        return@withLockSuspend Result.failure(EconomyDisabledException())
                    }

                    runOnMainThread { inventorySnapshot.restoreTo(player.inventory) }
                    Result.success(Unit)
                }
                .getOrElse {
                    return@withLockSuspend Result.failure(it)
                }

            balanceManager.insertTransactionLog(uuid, shards.toLong(), null, transactionReason, notes).getOrElse {
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
        senderUuid: UUID,
        receiverUuid: UUID,
        shards: ULong,
        transactionReason: String,
        notes: String?,
    ): Result<Unit> {
        require(shards <= Long.MAX_VALUE.toULong()) { "shards must not be above the max value of a Long" }
        if (economyDisabled) return Result.failure(EconomyDisabledException())

        return transactionLock.withLockSuspend(senderUuid) {
            val sender =
                Bukkit.getPlayer(senderUuid) ?: return@withLockSuspend Result.failure(SenderNotOnlineException())
            if (!sender.hasPlayedBefore()) return@withLockSuspend Result.failure(InvalidPlayerException())

            val receiver = Bukkit.getPlayer(receiverUuid) ?: Bukkit.getOfflinePlayer(receiverUuid)
            if (!receiver.hasPlayedBefore()) return@withLockSuspend Result.failure(InvalidPlayerException())

            sender.inventory
                .withLockSuspend {
                    val inventorySnapshot = runOnMainThread { InventorySnapshot.from(sender.inventory, balanceManager) }

                    val shardsToSubtractFromSender =
                        CommonOperations.consume(sender.uniqueId, shards.toLong(), inventorySnapshot).getOrElse {
                            if (it is DatabaseException) {
                                handleError(it)
                                return@withLockSuspend Result.failure(EconomyDisabledException())
                            }
                            return@withLockSuspend Result.failure(it)
                        }

                    balanceManager
                        .transferBankShards(senderUuid, receiverUuid, shardsToSubtractFromSender, shards.toLong())
                        .getOrElse {
                            if (it is InsufficientBalanceException) return@withLockSuspend Result.failure(it)
                            handleError(it)
                            return@withLockSuspend Result.failure(EconomyDisabledException())
                        }

                    runOnMainThread { inventorySnapshot.restoreTo(sender.inventory) }
                    Result.success(Unit)
                }
                .getOrElse {
                    return@withLockSuspend Result.failure(it)
                }

            balanceManager
                .insertTransactionLog(senderUuid, shards.toLong(), receiverUuid, transactionReason, notes)
                .getOrElse { handleError(it) }

            Result.success(Unit)
        }
    }

    @Suppress("unused")
    fun registerEventListener(eventListener: PlayerBalanceChangedListener) {
        eventManager.register(eventListener)
    }

    /** Converts a Diamond double to Shards */
    @Suppress("unused") fun diamondsToShards(diamonds: Double) = diamondsToShards(diamonds.toFloat())

    /** Converts a Diamond float to Shards */
    @Suppress("unused") fun diamondsToShards(diamonds: Float) = CommonOperations.diamondsToShards(diamonds)

    /** Converts Shards into a formatted Diamonds string */
    @Suppress("unused") fun shardsToDiamonds(shards: Long) = CommonOperations.shardsToDiamonds(shards)
}
