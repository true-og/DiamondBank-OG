package net.trueog.diamondbankog.commands

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage
import net.luckperms.api.LuckPerms
import net.trueog.diamondbankog.*
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.InventoryExtensions.lock
import net.trueog.diamondbankog.InventoryExtensions.unlock
import net.trueog.diamondbankog.MainThreadBlock.runOnMainThread
import net.trueog.diamondbankog.PlayerPrefix.getPrefix
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

internal class Pay(
    val config: Config = DiamondBankOG.config,
    val balanceManager: BalanceManager = DiamondBankOG.balanceManager,
    val mm: MiniMessage = DiamondBankOG.mm,
    val scope: CoroutineScope = DiamondBankOG.scope,
    val luckPerms: LuckPerms = DiamondBankOG.luckPerms,
    val transactionLock: TransactionLock = DiamondBankOG.transactionLock,
) : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (CommonCommandInterlude.run(sender, "pay", config, mm)) {
            return true
        }

        if (args.isNullOrEmpty()) {
            sender.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <red>You did not provide the name or the UUID of a player and the amount of <aqua>Diamonds<red>."
                )
            )
            return true
        }
        if (args.size != 2) {
            sender.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <red>Please (only) provide the name or the UUID of a player and the amount of <aqua>Diamonds<red>."
                )
            )
            return true
        }

        val receiver = CommonOperations.getPlayerUsingUuidOrName(args[0])
        if (sender.uniqueId == receiver.uniqueId) {
            sender.sendMessage(mm.deserialize("${config.prefix}<reset>: <red>You cannot pay yourself."))
            return true
        }

        if (!receiver.hasPlayedBefore()) {
            sender.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <red>That player doesn't exist or hasn't joined this server before."
                )
            )
            return true
        }

        val amount: Float
        try {
            amount = args[1].toFloat()
            if (amount <= 0) {
                sender.sendMessage(
                    mm.deserialize("${config.prefix}<reset>: <red>You cannot pay a negative or zero amount.")
                )
                return true
            }
        } catch (_: Exception) {
            sender.sendMessage(mm.deserialize("${config.prefix}<reset>: <red>Invalid argument."))
            return true
        }
        val shards =
            CommonOperations.diamondsToShards(amount).getOrElse {
                sender.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <aqua>Diamonds<red> can only have one decimal digit. Issue /diamondbankhelp for more information."
                    )
                )
                return true
            }

        scope.launch {
            when (
                transactionLock.tryWithLockSuspend(sender.uniqueId) {
                    val inventorySnapshot = runOnMainThread {
                        sender.inventory.lock()
                        InventorySnapshot.from(sender.inventory, balanceManager)
                    }

                    CommonOperations.consume(sender.uniqueId, shards, inventorySnapshot, config, balanceManager, mm)
                        .getOrElse {
                            when (it) {
                                is DiamondBankException.InsufficientFundsException -> {
                                    sender.sendMessage(
                                        mm.deserialize(
                                            "${config.prefix}<reset>: <red>You are ${
                                                CommonOperations.shardsToDiamondsFull(
                                                    it.short
                                                )
                                            } <red>short for that payment."
                                        )
                                    )
                                    sender.inventory.unlock()
                                    return@tryWithLockSuspend
                                }

                                else -> {
                                    sender.sendMessage(
                                        mm.deserialize(
                                            "${config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                                        )
                                    )
                                    sender.inventory.unlock()
                                    return@tryWithLockSuspend
                                }
                            }
                        }

                    balanceManager.addToBankShards(receiver.uniqueId, shards).getOrElse {
                        handleError(it)
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                            )
                        )
                        sender.inventory.unlock()
                        return@tryWithLockSuspend
                    }

                    runOnMainThread {
                        inventorySnapshot.restoreTo(sender.inventory)
                        sender.inventory.unlock()
                    }

                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <green>Successfully paid ${
                                CommonOperations.shardsToDiamondsFull(
                                    shards
                                )
                            } <green>to ${
                                getPrefix(
                                    receiver.uniqueId,
                                    luckPerms,
                                )
                            }${receiver.name}<reset><green>."
                        )
                    )

                    if (receiver.isOnline) {
                        val receiverPlayer = receiver.player ?: return@tryWithLockSuspend
                        receiverPlayer.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <green>${
                                    getPrefix(
                                        sender.uniqueId,
                                        luckPerms,
                                    )
                                }${sender.name}<reset> <green>has paid you ${
                                    CommonOperations.shardsToDiamondsFull(
                                        shards
                                    )
                                }<green>."
                            )
                        )
                    }

                    balanceManager
                        .insertTransactionLog(sender.uniqueId, shards, receiver.uniqueId, "Pay", null)
                        .getOrElse { handleError(it) }
                }
            ) {
                is TransactionLock.LockResult.Failed -> {
                    sender.sendMessage(
                        mm.deserialize("${config.prefix}<reset>: <red>You are currently blocked from using /pay.")
                    )
                    return@launch
                }

                else -> {}
            }
        }
        return true
    }
}
