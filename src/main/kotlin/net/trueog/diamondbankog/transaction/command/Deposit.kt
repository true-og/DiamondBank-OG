package net.trueog.diamondbankog.transaction.command

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage
import net.trueog.diamondbankog.*
import net.trueog.diamondbankog.balance.BalanceManager
import net.trueog.diamondbankog.config.Config
import net.trueog.diamondbankog.transaction.CommonOperations
import net.trueog.diamondbankog.transaction.InventoryLockExtensions.lock
import net.trueog.diamondbankog.transaction.InventoryLockExtensions.unlock
import net.trueog.diamondbankog.transaction.InventorySnapshot
import net.trueog.diamondbankog.transaction.InventorySnapshotUtils
import net.trueog.diamondbankog.transaction.TransactionLock
import net.trueog.diamondbankog.util.CommonCommandInterlude
import net.trueog.diamondbankog.util.ErrorHandler.handleError
import net.trueog.diamondbankog.util.MainThreadBlock.runOnMainThread
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

internal class Deposit(
    val config: Config = DiamondBankOG.config,
    val balanceManager: BalanceManager = DiamondBankOG.balanceManager,
    val mm: MiniMessage = DiamondBankOG.mm,
    val scope: CoroutineScope = DiamondBankOG.scope,
    val transactionLock: TransactionLock = DiamondBankOG.transactionLock,
) : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (CommonCommandInterlude.run(sender, "deposit", config, mm)) {
            return true
        }

        if (args.isNullOrEmpty()) {
            sender.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <red>You did not provide the amount of <aqua>Diamonds <red>that you want to deposit."
                )
            )
            return true
        }
        if (args.size != 1) {
            sender.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <red>Please (only) provide the amount of <aqua>Diamonds <red>you want to deposit. Either a number or \"all\"."
                )
            )
            return true
        }

        var shards: Long = -1L
        if (args[0] != "all") {
            val amount: Float
            try {
                amount = args[0].toFloat()
                if (amount <= 0) {
                    sender.sendMessage(
                        mm.deserialize("${config.prefix}<reset>: <red>You cannot deposit a negative or zero amount.")
                    )
                    return true
                }
            } catch (_: Exception) {
                sender.sendMessage(mm.deserialize("${config.prefix}<reset>: <red>Invalid argument."))
                return true
            }
            shards =
                CommonOperations.diamondsToShards(amount).getOrElse {
                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <aqua>Diamonds<red> can only have one decimal digit. Issue /diamondbankhelp for more information."
                        )
                    )
                    return true
                }
        }

        scope.launch {
            when (
                transactionLock.tryWithLockSuspend(sender.uniqueId) {
                    val inventorySnapshot = runOnMainThread {
                        sender.inventory.lock()
                        InventorySnapshot.from(sender.inventory, balanceManager)
                    }

                    val removedInShards: Long =
                        if (shards == -1L) {
                            InventorySnapshotUtils.removeAll(inventorySnapshot).toLong()
                        } else {
                            InventorySnapshotUtils.removeShards(inventorySnapshot, shards, config, balanceManager, mm)
                                .getOrElse {
                                    sender.sendMessage(
                                        mm.deserialize("${config.prefix}<reset>: <red>Something went wrong.")
                                    )
                                    sender.inventory.unlock()
                                    handleError(it)
                                    return@tryWithLockSuspend
                                }
                                .toLong()
                        }
                    if (shards != -1L && removedInShards != shards) {
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>You do not have ${
                                    CommonOperations.shardsToDiamondsFull(
                                        shards
                                    )
                                } <red>to deposit."
                            )
                        )
                        sender.inventory.unlock()
                        return@tryWithLockSuspend
                    }

                    balanceManager.addToBankShards(sender.uniqueId, removedInShards).getOrElse {
                        handleError(it)
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>Something went wrong while trying to add to your balance."
                            )
                        )
                        return@tryWithLockSuspend
                    }

                    runOnMainThread {
                        inventorySnapshot.restoreTo(sender.inventory)
                        sender.inventory.unlock()
                    }

                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <green>Successfully deposited ${
                                CommonOperations.shardsToDiamondsFull(
                                    removedInShards
                                )
                            } <green>into your bank account."
                        )
                    )

                    balanceManager.insertTransactionLog(sender.uniqueId, shards, null, "Deposit", null).getOrElse {
                        handleError(it)
                    }
                }
            ) {
                is TransactionLock.LockResult.Failed -> {
                    sender.sendMessage(
                        mm.deserialize("${config.prefix}<reset>: <red>You are currently blocked from using /deposit.")
                    )
                    return@launch
                }

                else -> {}
            }
        }
        return true
    }
}
