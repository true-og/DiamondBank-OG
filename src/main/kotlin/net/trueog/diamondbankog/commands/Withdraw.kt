package net.trueog.diamondbankog.commands

import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.*
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.DiamondBankOG.Companion.mm
import net.trueog.diamondbankog.DiamondBankOG.Companion.scope
import net.trueog.diamondbankog.DiamondBankOG.Companion.transactionLock
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.InventoryExtensions.countTotal
import net.trueog.diamondbankog.MainThreadBlock.runOnMainThread
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

internal class Withdraw : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        scope.launch {
            if (economyDisabled) {
                sender.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <red>The economy is disabled. Please notify a staff member."
                    )
                )
                return@launch
            }

            if (sender !is Player) {
                sender.sendMessage("You can only execute this command as a player.")
                return@launch
            }

            val worldName = sender.world.name
            if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") {
                sender.sendMessage(
                    mm.deserialize("${config.prefix}<reset>: <red>You cannot use /withdraw when in a minigame.")
                )
                return@launch
            }

            if (!sender.hasPermission("diamondbank-og.withdraw")) {
                sender.sendMessage(
                    mm.deserialize("${config.prefix}<reset>: <red>You do not have permission to use this command.")
                )
                return@launch
            }

            if (args == null || args.isEmpty()) {
                sender.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <red>You did not provide the amount of <aqua>Diamonds <red>that you want to withdraw."
                    )
                )
                return@launch
            }
            if (args.size != 1 && args.size != 2) {
                sender.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <red>Please provide the amount of <aqua>Diamonds <red>you want to withdraw. Either a number or \"all\"."
                    )
                )
                return@launch
            }

            var shards = -1L
            if (args[0] != "all") {
                val amount: Float
                try {
                    amount = args[0].toFloat()
                    if (amount <= 0) {
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>You cannot withdraw a negative or zero amount."
                            )
                        )
                        return@launch
                    }
                } catch (_: Exception) {
                    sender.sendMessage(mm.deserialize("${config.prefix}<reset>: <red>Invalid argument."))
                    return@launch
                }
                val split = amount.toString().split(".")
                if (split[1].length > 1) {
                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <red><aqua>Diamonds<red> can only have one decimal digit. Issue /diamondbankhelp for more information."
                        )
                    )
                    return@launch
                }
                shards = (split[0].toLong() * 9) + split[1].toLong()
            }

            val lockResult =
                transactionLock.tryWithLockSuspend(sender.uniqueId) {
                    val playerShards =
                        balanceManager.getAllShards(sender.uniqueId).getOrElse {
                            sender.sendMessage(
                                mm.deserialize(
                                    "${config.prefix}<reset>: <red>Something went wrong while trying to get your balance."
                                )
                            )
                            return@tryWithLockSuspend true
                        }

                    val playerBankShards = playerShards.bank
                    val playerInventoryShards = playerShards.inventory

                    if (shards == -1L) shards = playerBankShards

                    if (shards > playerBankShards) {
                        val diamonds = String.format("%.1f", floor((shards / 9.0) * 10) / 10.0)
                        val bankDiamonds = String.format("%.1f", floor((playerBankShards / 9.0) * 10) / 10.0)
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>Cannot withdraw <yellow>$diamonds <aqua>Diamond${if (diamonds != "1.0") "s" else ""} <red>because your bank only contains <yellow>$bankDiamonds <aqua>Diamond${if (bankDiamonds != "1.0") "s" else ""}<red>."
                            )
                        )
                        return@tryWithLockSuspend true
                    }

                    val diamondAmount = shards / 9
                    val shardAmount = shards % 9

                    val emptySlots = sender.inventory.storageContents.filter { it == null }.size
                    val leftOverSpaceDiamonds =
                        sender.inventory.storageContents
                            .filterNotNull()
                            .filter { it.type == Material.DIAMOND }
                            .sumOf { 64 - it.amount }
                    val leftOverSpaceShards =
                        sender.inventory.storageContents
                            .filterNotNull()
                            .filter {
                                it.type == Material.PRISMARINE_SHARD &&
                                    it.persistentDataContainer.has(Shard.namespacedKey)
                            }
                            .sumOf { 64 - it.amount }

                    val emptySlotsAfterDiamonds = if (emptySlots != 0) emptySlots - ceil(diamondAmount / 64.0) else 0.0

                    if (
                        diamondAmount > (emptySlots * 64 + leftOverSpaceDiamonds) ||
                            shardAmount > (emptySlotsAfterDiamonds * 64 + leftOverSpaceShards)
                    ) {
                        val diamonds = String.format("%.1f", floor((shards / 9.0) * 10) / 10.0)
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>You don't have enough inventory space to withdraw <yellow>$diamonds <aqua>Diamond${if (diamonds != "1.0") "s" else ""}<red>."
                            )
                        )
                        return@tryWithLockSuspend true
                    }

                    balanceManager.subtractFromBankShards(sender.uniqueId, shards).getOrElse {
                        handleError(sender.uniqueId, shards, PostgreSQL.PlayerShards(playerBankShards, -1, -1))
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                            )
                        )
                        return@tryWithLockSuspend true
                    }

                    runOnMainThread {
                        if (diamondAmount > 0) {
                            sender.inventory.addItem(ItemStack(Material.DIAMOND, diamondAmount.toInt()))
                        }
                        if (shardAmount > 0) {
                            sender.inventory.addItem(Shard.createItemStack(shardAmount.toInt()))
                        }
                    }

                    val inventoryShards = sender.inventory.countTotal()
                    if (inventoryShards != (playerInventoryShards + shards)) {
                        handleError(sender.uniqueId, shards, playerShards)
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                            )
                        )
                        return@tryWithLockSuspend true
                    }

                    balanceManager.addToPlayerShards(sender.uniqueId, shards, ShardType.INVENTORY).getOrElse {
                        handleError(sender.uniqueId, shards, playerShards)
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>Something went wrong while trying to recount the <aqua>Diamonds<red> amount in your inventory, try opening and closing your inventory to force a recount."
                            )
                        )
                        return@tryWithLockSuspend true
                    }
                    false
                }
            when (lockResult) {
                is TransactionLock.LockResult.Acquired -> {
                    if (lockResult.result) {
                        return@launch
                    }
                }

                is TransactionLock.LockResult.Failed -> {
                    sender.sendMessage(
                        mm.deserialize("${config.prefix}<reset>: <red>You are currently blocked from using /withdraw.")
                    )
                    return@launch
                }
            }

            val diamonds = String.format("%.1f", floor((shards / 9.0) * 10) / 10.0)
            sender.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <green>Successfully withdrew <yellow>$diamonds <aqua>Diamond${if (diamonds != "1.0") "s" else ""} <green>from your bank account."
                )
            )

            balanceManager.insertTransactionLog(sender.uniqueId, shards, null, "Withdraw", null).getOrElse {
                handleError(sender.uniqueId, shards, null, null, true)
            }
        }
        return true
    }
}
