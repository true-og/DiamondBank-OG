package net.trueog.diamondbankog.commands

import kotlin.math.ceil
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.CommonOperations
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.DiamondBankOG.Companion.mm
import net.trueog.diamondbankog.DiamondBankOG.Companion.scope
import net.trueog.diamondbankog.PlayerPrefix.getPrefix
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

internal class Balancetop : CommandExecutor {
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

            if (!sender.hasPermission("diamondbank-og.balancetop")) {
                sender.sendMessage(
                    mm.deserialize("${config.prefix}<reset>: <red>You do not have permission to use this command.")
                )
                return@launch
            }

            if (args == null) return@launch
            if (args.size > 1) {
                sender.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <red>Please (only) optionally provide the page number or the name or UUID of the player you want to see the position of."
                    )
                )
                return@launch
            }

            var offset = 0
            var index = 1
            var player: OfflinePlayer? = null
            if (args.size == 1) {
                try {
                    index = args[0].toInt()
                    offset = 9 * (index - 1)
                } catch (_: Exception) {
                    player = CommonOperations.getPlayerUsingUuidOrName(args[0])
                    if (!player.hasPlayedBefore()) {
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>That player doesn't exist or hasn't joined this server before."
                            )
                        )
                        return@launch
                    }
                }
            }

            if (player != null) {
                val baltopWithUuid =
                    balanceManager.getBaltopWithUuid(player.uniqueId).getOrElse {
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>Something went wrong while trying to get the information for balancetop."
                            )
                        )
                        return@launch
                    }
                val (baltop, offset) = baltopWithUuid

                var baltopMessage = "${config.prefix} <aqua>Top Balances:<reset>"
                baltop.forEach {
                    val uuid = it.key
                    if (uuid == null) {
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>Something went wrong while trying to get the information for balancetop."
                            )
                        )
                        return@launch
                    }

                    val player = Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
                    val playerName = player.name ?: player.uniqueId.toString()

                    val diamonds = CommonOperations.shardsToDiamonds(it.value)
                    baltopMessage +=
                        "\n<green>${baltopMessage.lines().size + offset}<reset>. ${if (playerName == player.name) "<italic>" else ""}${
                            getPrefix(
                                uuid
                            )
                        }$playerName<reset>: <yellow>$diamonds <aqua>Diamonds"
                }
                sender.sendMessage(mm.deserialize(baltopMessage))
                return@launch
            }

            val baltop =
                balanceManager.getBaltop(offset).getOrElse {
                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <red>Something went wrong while trying to get the information for balancetop."
                        )
                    )
                    return@launch
                }

            val numberOfRows =
                balanceManager.getNumberOfRows().getOrElse {
                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <red>Something went wrong while trying to get the information for balancetop."
                        )
                    )
                    return@launch
                }

            if (index > ceil(numberOfRows / 9.0)) {
                sender.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <red>The amount of pages only goes up to and including ${
                            ceil(
                                numberOfRows / 9.0
                            ).toLong()
                        }."
                    )
                )
                return@launch
            }
            var baltopMessage =
                "${config.prefix} <aqua>Top Balances <#2ec2ae>(Page $index/${ceil(numberOfRows / 9.0).toLong()})<aqua>:<reset>"
            baltop.forEach {
                val uuid = it.key
                if (uuid == null) {
                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <red>Something went wrong while trying to get the information for balancetop."
                        )
                    )
                    return@launch
                }

                val player = Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
                val playerName = player.name ?: player.uniqueId.toString()

                val diamonds = CommonOperations.shardsToDiamonds(it.value)
                baltopMessage +=
                    "\n<green>${baltopMessage.lines().size + (9 * (index - 1))}<reset>. ${if (playerName == player.name) "<italic>" else ""}${
                        getPrefix(
                            uuid
                        )
                    }$playerName<reset>: <yellow>$diamonds <aqua>Diamonds"
            }

            sender.sendMessage(mm.deserialize(baltopMessage))
        }
        return true
    }
}
