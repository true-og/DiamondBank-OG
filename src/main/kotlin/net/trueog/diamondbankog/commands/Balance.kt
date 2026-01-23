package net.trueog.diamondbankog.commands

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.DiamondBankOG.Companion.mm
import net.trueog.diamondbankog.DiamondBankOG.Companion.scope
import net.trueog.diamondbankog.PlayerPrefix.getPrefix
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*
import kotlin.math.floor

internal class Balance : CommandExecutor {
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

            if (!sender.hasPermission("diamondbank-og.balance")) {
                sender.sendMessage(
                    mm.deserialize("${config.prefix}<reset>: <red>You do not have permission to use this command.")
                )
                return@launch
            }

            if (args == null) return@launch

            if (sender !is Player) {
                if (args.isEmpty()) {
                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <red>Please provide that name or UUID of the player that you want to check the balance of."
                        )
                    )
                    return@launch
                }

                val otherPlayer =
                    try {
                        Bukkit.getPlayer(UUID.fromString(args[0])) ?: Bukkit.getOfflinePlayer(UUID.fromString(args[0]))
                    } catch (_: Exception) {
                        Bukkit.getPlayer(args[0]) ?: Bukkit.getOfflinePlayer(args[0])
                    }
                if (!otherPlayer.hasPlayedBefore()) {
                    sender.sendMessage(mm.deserialize("${config.prefix}<reset>: <red>That player doesn't exist."))
                    return@launch
                }

                val balance =
                    balanceManager.getAllShards(otherPlayer.uniqueId).getOrElse {
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>Something went wrong while trying to get their balance."
                            )
                        )
                        return@launch
                    }

                val totalBalance = balance.bank + balance.inventory + balance.enderChest
                val totalDiamonds = String.format("%.1f", floor((totalBalance / 9.0) * 10) / 10.0)
                val bankDiamonds = String.format("%.1f", floor((balance.bank / 9.0) * 10) / 10.0)
                val inventoryDiamonds = String.format("%.1f", floor((balance.inventory / 9.0) * 10) / 10.0)
                val enderChestDiamonds = String.format("%.1f", floor((balance.enderChest / 9.0) * 10) / 10.0)
                sender.sendMessage(
                    mm.deserialize(
                        "<green>Balance of ${getPrefix(otherPlayer.uniqueId)}${otherPlayer.name}<reset><green>:\n" +
                                "Bank: <yellow>$bankDiamonds <aqua>Diamond${if (bankDiamonds != "1.0") "s" else ""}\n" +
                                "<green>Inventory: <yellow>$inventoryDiamonds <aqua>Diamond${if (inventoryDiamonds != "1.0") "s" else ""}\n" +
                                "<green>Ender Chest: <yellow>$enderChestDiamonds <aqua>Diamond${if (enderChestDiamonds != "1.0") "s" else ""}\n" +
                                "<bold><green>Total: <yellow>$totalDiamonds <aqua>Diamond${if (totalDiamonds != "1.0") "s" else ""}"
                    )
                )
                return@launch
            }

            val balancePlayer =
                if (args.isEmpty()) {
                    sender
                } else {
                    if (!sender.hasPermission("diamondbank-og.balance.others")) {
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>You do not have permission to use this command in that way."
                            )
                        )
                        return@launch
                    }

                    val otherPlayer =
                        try {
                            Bukkit.getPlayer(UUID.fromString(args[0]))
                                ?: Bukkit.getOfflinePlayer(UUID.fromString(args[0]))
                        } catch (_: Exception) {
                            Bukkit.getPlayer(args[0]) ?: Bukkit.getOfflinePlayer(args[0])
                        }
                    if (!otherPlayer.hasPlayedBefore()) {
                        sender.sendMessage(mm.deserialize("${config.prefix}<reset>: <red>That player doesn't exist."))
                        return@launch
                    }
                    otherPlayer
                }
            val balance =
                balanceManager.getAllShards(balancePlayer.uniqueId).getOrElse {
                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <red>Something went wrong while trying to get ${
                                if (balancePlayer.uniqueId != sender.uniqueId) "their" else "your"
                            } balance."
                        )
                    )
                    return@launch
                }

            val totalBalance = balance.bank + balance.inventory + balance.enderChest
            val totalDiamonds = String.format("%.1f", floor((totalBalance / 9.0) * 10) / 10.0)
            val bankDiamonds = String.format("%.1f", floor((balance.bank / 9.0) * 10) / 10.0)
            val inventoryDiamonds = String.format("%.1f", floor((balance.inventory / 9.0) * 10) / 10.0)
            val enderChestDiamonds = String.format("%.1f", floor((balance.enderChest / 9.0) * 10) / 10.0)
            sender.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <green>${
                        if (balancePlayer.uniqueId == sender.uniqueId) "Your Balance" else "Balance of ${
                            getPrefix(
                                balancePlayer.uniqueId
                            )
                        }${balancePlayer.name}"
                    }<reset><green>:\n" +
                            "Bank: <yellow>$bankDiamonds <aqua>Diamond${if (bankDiamonds != "1.0") "s" else ""}\n" +
                            "<green>Inventory: <yellow>$inventoryDiamonds <aqua>Diamond${if (inventoryDiamonds != "1.0") "s" else ""}\n" +
                            "<green>Ender Chest: <yellow>$enderChestDiamonds <aqua>Diamond${if (enderChestDiamonds != "1.0") "s" else ""}\n" +
                            "<bold><green>Total: <yellow>$totalDiamonds <aqua>Diamond${if (totalDiamonds != "1.0") "s" else ""}"
                )
            )
            return@launch
        }
        return true
    }
}
