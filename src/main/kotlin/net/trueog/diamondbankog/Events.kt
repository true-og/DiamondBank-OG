package net.trueog.diamondbankog

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import kotlin.math.ceil

@OptIn(DelicateCoroutinesApi::class)
class Events : Listener {
    @EventHandler
    fun onCommandPreprocess(event: PlayerCommandPreprocessEvent) {
        val args = event.message.split(" ", limit = 3)
        val player = event.player
        if (args[0] != "/baltop" && args[0] != "/balancetop") {
            if (args[0] == "/balance" || args[0] == "/bal") {
                if (!player.hasPermission("diamondbank-og.balance")) {
                    player.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You do not have permission to use this command."))
                    return
                }
                event.isCancelled = true

                val balancePlayer = if (args.size == 1) {
                    player
                } else {
                    if (!player.hasPermission("diamondbank-og.balance.others")) {
                        player.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You do not have permission to use this command."))
                        return
                    }

                    val otherPlayer = Bukkit.getOfflinePlayer(args[1])
                    if (!otherPlayer.hasPlayedBefore()) {
                        player.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>That player doesn't exist."))
                        return
                    }
                    otherPlayer
                }
                GlobalScope.launch {
                    val balance = DiamondBankOG.postgreSQL.getPlayerBalance(balancePlayer.uniqueId)
                    if (balance == -1L) {
                        player.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to get your balance."))
                        return@launch
                    }
                    player.sendMessage(DiamondBankOG.mm.deserialize("<green>Balance${if (balancePlayer.uniqueId != player.uniqueId) " of <red>${balancePlayer.name}" else ""}<green>: <yellow>$balance <aqua>${if (balance == 1L) "Diamond" else "Diamonds"}<green>."))
                }
            }
            return
        }
        event.isCancelled = true

        if (!player.hasPermission("diamondbank-og.baltop")) {
            player.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You do not have permission to use this command."))
            return
        }

        if (args.size != 1 && args.size != 2) {
            player.sendMessage("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Please do not provide any arguments or only provide the number of the page.")
            return
        }

        GlobalScope.launch {
            var offset = 0
            var index = 1
            if (args.size == 2) {
                try {
                    index = args[1].toInt()
                } catch (_: Exception) {
                    player.sendMessage("Invalid argument")
                    return@launch
                }
                offset = 10 * (index - 1)
            }
            val baltop = DiamondBankOG.postgreSQL.getBaltop(offset)
            if (baltop == null) {
                player.sendMessage("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to get the information for balancetop.")
                return@launch
            }
            val numberOfRows = DiamondBankOG.postgreSQL.getNumberOfRows()
            if (numberOfRows == -1L) {
                player.sendMessage("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to get the information for balancetop.")
                return@launch
            }

            if (index > ceil(numberOfRows / 10.0)) {
                player.sendMessage("The amount of pages only goes up to $numberOfRows")
                return@launch
            }
            var baltopMessage =
                ("<yellow>---- <gold>Balancetop <yellow>-- <gold>Page <red>$index<gold>/<red>${ceil(numberOfRows / 10.0).toLong()} <yellow>----<reset>")
            baltop.forEach {
                if (it.key == null) {
                    player.sendMessage("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to get the information for balancetop.")
                    return@launch
                }
                baltopMessage += "\n<red>${baltopMessage.lines().size + (10 * (index - 1))}<reset>. ${if (it.key == player.name) "<red>" else ""}${it.key}<reset>, ${it.value}"
            }

            player.sendMessage(DiamondBankOG.mm.deserialize(baltopMessage))
        }
        return
    }
}