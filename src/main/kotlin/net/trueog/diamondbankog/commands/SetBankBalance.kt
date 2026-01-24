package net.trueog.diamondbankog.commands

import java.util.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.DiamondBankOG.Companion.mm
import net.trueog.diamondbankog.DiamondBankOG.Companion.scope
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.PlayerPrefix.getPrefix
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

internal class SetBankBalance : CommandExecutor {
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

            if (!sender.hasPermission("diamondbank-og.setbankbalance")) {
                sender.sendMessage(
                    mm.deserialize("${config.prefix}<reset>: <red>You do not have permission to use this command.")
                )
                return@launch
            }

            if (args.isNullOrEmpty()) {
                sender.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <red>You did not provide the name or the UUID of a player and the amount of <aqua>Shards<red>."
                    )
                )
                return@launch
            }
            if (args.size != 2) {
                sender.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <red>Incorrect syntax. Usage: /setbankbal(ance) <player name or player uuid> <shards>."
                    )
                )
                return@launch
            }

            val player =
                try {
                    Bukkit.getPlayer(UUID.fromString(args[0])) ?: Bukkit.getOfflinePlayer(UUID.fromString(args[0]))
                } catch (_: Exception) {
                    Bukkit.getPlayer(args[0]) ?: Bukkit.getOfflinePlayer(args[0])
                }
            if (!player.hasPlayedBefore()) {
                sender.sendMessage(mm.deserialize("${config.prefix}<reset>: <red>That player doesn't exist."))
                return@launch
            }

            val balance: Long
            try {
                balance = args[1].toLong()
            } catch (_: Exception) {
                sender.sendMessage(mm.deserialize("${config.prefix}<reset>: <red>Invalid argument."))
                return@launch
            }

            balanceManager.setPlayerShards(player.uniqueId, balance, ShardType.BANK).getOrElse {
                sender.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <red>Something went wrong while trying to set that player's balance."
                    )
                )
                return@launch
            }

            sender.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <green>Successfully set the balance of ${
                        getPrefix(
                            player.uniqueId
                        )
                    }${player.name}<reset> <green>to <yellow>$balance <aqua>${if (balance == 1L) "Shard" else "Shards"}<green>."
                )
            )

            balanceManager
                .insertTransactionLog(
                    player.uniqueId,
                    balance,
                    null,
                    "Set Bank Balance",
                    "Bank Balance set by ${if (sender is Player) "${sender.uniqueId}" else "console"}",
                )
                .getOrElse { handleError(it) }
        }
        return true
    }
}
