package net.trueog.diamondbankog.commands

import java.util.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.CommonOperations
import net.trueog.diamondbankog.DiamondBankException
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.mm
import net.trueog.diamondbankog.DiamondBankOG.Companion.scope
import net.trueog.diamondbankog.DiamondBankOG.Companion.transactionLock
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.InventoryExtensions.lock
import net.trueog.diamondbankog.InventoryExtensions.unlock
import net.trueog.diamondbankog.InventorySnapshot
import net.trueog.diamondbankog.MainThreadBlock.runOnMainThread
import net.trueog.diamondbankog.PlayerPrefix.getPrefix
import net.trueog.diamondbankog.PostgreSQL.ShardType
import net.trueog.diamondbankog.TransactionLock
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

internal class Pay : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (CommonCommandInterlude.run(sender, "pay")) {
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

        val receiver =
            try {
                Bukkit.getPlayer(UUID.fromString(args[0])) ?: Bukkit.getOfflinePlayer(UUID.fromString(args[0]))
            } catch (_: Exception) {
                Bukkit.getPlayer(args[0]) ?: Bukkit.getOfflinePlayer(args[0])
            }

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
                        "${config.prefix}<reset>: <red><aqua>Diamonds<red> can only have one decimal digit. Issue /diamondbankhelp for more information."
                    )
                )
                return true
            }

        scope.launch {
            when (
                transactionLock.tryWithLockSuspend(sender.uniqueId) {
                    val inventorySnapshot = runOnMainThread {
                        sender.inventory.lock()
                        InventorySnapshot.from(sender.inventory)
                    }

                    CommonOperations.consume(sender.uniqueId, shards, inventorySnapshot).getOrElse {
                        when (it) {
                            is DiamondBankException.InsufficientFundsException -> {
                                val shortInDiamonds = CommonOperations.shardsToDiamonds(it.short)
                                sender.sendMessage(
                                    mm.deserialize(
                                        "${config.prefix}<reset>: <red>You are <yellow>$shortInDiamonds <aqua>Diamond${if (shortInDiamonds != "1.0") "s" else ""} <red>short for that payment."
                                    )
                                )
                                sender.inventory.unlock()
                                return@tryWithLockSuspend
                            }

                            is DiamondBankException.InsufficientInventorySpaceException -> {
                                sender.sendMessage(
                                    mm.deserialize(
                                        "${config.prefix}<reset>: <red>You do not have enough inventory space for the change."
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

                    balanceManager.addToPlayerShards(receiver.uniqueId, shards, ShardType.BANK).getOrElse {
                        handleError(it)
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                            )
                        )
                        sender.inventory.unlock()
                        return@tryWithLockSuspend
                    }

                    val diamondsPaid = CommonOperations.shardsToDiamonds(shards)

                    runOnMainThread {
                        inventorySnapshot.restoreTo(sender.inventory)
                        sender.inventory.unlock()
                    }

                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <green>Successfully paid <yellow>$diamondsPaid <aqua>Diamond${if (diamondsPaid != "1.0") "s" else ""} <green>to ${
                                getPrefix(
                                    receiver.uniqueId
                                )
                            } ${receiver.name}<reset><green>."
                        )
                    )

                    if (receiver.isOnline) {
                        val receiverPlayer = receiver.player ?: return@tryWithLockSuspend
                        receiverPlayer.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <green>${
                                    getPrefix(
                                        sender.uniqueId
                                    )
                                } ${sender.name}<reset> <green>has paid you <yellow>$diamondsPaid <aqua>Diamond${if (diamondsPaid != "1.0") "s" else ""}<green>."
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
