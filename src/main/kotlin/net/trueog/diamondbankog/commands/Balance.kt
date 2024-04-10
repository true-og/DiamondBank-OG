package net.trueog.diamondbankog.commands

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.Config
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.PostgreSQL.DiamondType
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

class Balance : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        GlobalScope.launch {
            if (DiamondBankOG.economyDisabled) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>The economy is disabled because of a severe error. Please notify a staff member."))
                return@launch
            }

            if (!sender.hasPermission("diamondbank-og.balance")) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You do not have permission to use this command."))
                return@launch
            }

            if (args == null) return@launch

            if (sender !is Player) {
                if (args.isEmpty()) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Please provide that name or UUID of the player that you want to check the balance of."))
                    return@launch
                }

                val otherPlayer = try {
                    Bukkit.getPlayer(UUID.fromString(args[0])) ?: Bukkit.getOfflinePlayer(UUID.fromString(args[0]))
                } catch (_: Exception) {
                    Bukkit.getPlayer(args[0]) ?: Bukkit.getOfflinePlayer(args[0])
                }
                if (!otherPlayer.hasPlayedBefore()) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>That player doesn't exist."))
                    return@launch
                }

                val balance = DiamondBankOG.postgreSQL.getPlayerDiamonds(
                    otherPlayer.uniqueId,
                    DiamondType.ALL
                )
                if (balance.amountInBank == null || balance.amountInInventory == null || balance.amountInEnderChest == null) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong while trying to get your balance."))
                    return@launch
                }
                val totalBalance = balance.amountInBank + balance.amountInInventory + balance.amountInEnderChest
                sender.sendMessage(DiamondBankOG.mm.deserialize("<green>Balance of <red>${otherPlayer.name}<green>: <yellow>$totalBalance <aqua>${if (totalBalance == 1) "Diamond" else "Diamonds"} <white>(<red>Bank: <yellow>${balance.amountInBank}<white>, <red>Inventory: <yellow>${balance.amountInInventory}<white>, <red>Ender Chest: <yellow>${balance.amountInEnderChest}<white>)."))
                return@launch
            }

            val balancePlayer = if (args.isEmpty()) {
                sender
            } else {
                if (!sender.hasPermission("diamondbank-og.balance.others")) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You do not have permission to use this command."))
                    return@launch
                }

                val otherPlayer = try {
                    Bukkit.getPlayer(UUID.fromString(args[0])) ?: Bukkit.getOfflinePlayer(UUID.fromString(args[0]))
                } catch (_: Exception) {
                    Bukkit.getPlayer(args[0]) ?: Bukkit.getOfflinePlayer(args[0])
                }
                if (!otherPlayer.hasPlayedBefore()) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>That player doesn't exist."))
                    return@launch
                }
                otherPlayer
            }
            val balance = DiamondBankOG.postgreSQL.getPlayerDiamonds(
                balancePlayer.uniqueId,
                DiamondType.ALL
            )
            if (balance.amountInBank == null || balance.amountInInventory == null || balance.amountInEnderChest == null) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong while trying to get your balance."))
                return@launch
            }
            val totalBalance = balance.amountInBank + balance.amountInInventory + balance.amountInEnderChest
            sender.sendMessage(DiamondBankOG.mm.deserialize("<green>Balance${if (balancePlayer.uniqueId != sender.uniqueId) " of <red>${balancePlayer.name}" else ""}<green>: <yellow>$totalBalance <aqua>${if (totalBalance == 1) "Diamond" else "Diamonds"} <white>(<red>Bank: <yellow>${balance.amountInBank}<white>, <red>Inventory: <yellow>${balance.amountInInventory}<white>, <red>Ender Chest: <yellow>${balance.amountInEnderChest}<white>)."))
            return@launch
        }
        return true
    }
}