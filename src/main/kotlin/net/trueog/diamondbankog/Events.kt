package net.trueog.diamondbankog

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.PostgreSQL.BalanceType
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.server.ServerCommandEvent
import kotlin.math.ceil

@OptIn(DelicateCoroutinesApi::class)
class Events : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val inventory = event.player.inventory
        val enderChest = event.player.enderChest

        GlobalScope.launch {
            val inventoryDiamonds = inventory.all(Material.DIAMOND).values.sumOf { it.amount }
            var error = DiamondBankOG.postgreSQL.setPlayerBalance(
                event.player.uniqueId,
                inventoryDiamonds.toLong(),
                BalanceType.INVENTORY_BALANCE
            )
            if (error) {
                // TODO: Handle
                return@launch
            }

            val enderChestDiamonds = enderChest.all(Material.DIAMOND).values.sumOf { it.amount }
            error = DiamondBankOG.postgreSQL.setPlayerBalance(
                event.player.uniqueId,
                enderChestDiamonds.toLong(),
                BalanceType.ENDER_CHEST_BALANCE
            )
            if (error) {
                // TODO: Handle
                return@launch
            }
        }
    }

    @EventHandler
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        if (DiamondBankOG.blockInventoryFor.contains(event.entity.uniqueId)) {
            event.isCancelled = true
            return
        }
        if (event.item.itemStack.type != Material.DIAMOND) return
        if (event.entity !is Player) return
        val diamondAmount = event.item.itemStack.amount.toLong()
        GlobalScope.launch {
            val error = DiamondBankOG.postgreSQL.addToPlayerBalance(
                event.entity.uniqueId,
                diamondAmount,
                BalanceType.INVENTORY_BALANCE
            )
            if (error) {
                // TODO: Handle
            }
        }
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (DiamondBankOG.blockInventoryFor.contains(event.player.uniqueId)) {
            event.isCancelled = true
            return
        }
        if (event.itemDrop.itemStack.type != Material.DIAMOND) return
        GlobalScope.launch {
            val error = DiamondBankOG.postgreSQL.subtractFromPlayerBalance(
                event.player.uniqueId,
                event.itemDrop.itemStack.amount.toLong(),
                BalanceType.INVENTORY_BALANCE
            )
            if (error) {
                // TODO: Handle
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val inventory = event.player.inventory
        val enderChest = if (event.inventory.type == InventoryType.ENDER_CHEST) event.player.enderChest else null

        GlobalScope.launch {
            val inventoryDiamonds = inventory.all(Material.DIAMOND).values.sumOf { it.amount }
            var error = DiamondBankOG.postgreSQL.setPlayerBalance(
                event.player.uniqueId,
                inventoryDiamonds.toLong(),
                BalanceType.INVENTORY_BALANCE
            )
            if (error) {
                // TODO: Handle
                return@launch
            }

            if (enderChest == null) return@launch
            val enderChestDiamonds = enderChest.all(Material.DIAMOND).values.sumOf { it.amount }
            error = DiamondBankOG.postgreSQL.setPlayerBalance(
                event.player.uniqueId,
                enderChestDiamonds.toLong(),
                BalanceType.ENDER_CHEST_BALANCE
            )
            if (error) {
                // TODO: Handle
                return@launch
            }
        }
    }

    private enum class CommandType {
        BALANCE, BALANCETOP
    }

    @EventHandler
    fun onPlayerCommandPreprocess(event: PlayerCommandPreprocessEvent) {
        val args = event.message.split(" ", limit = 3)
        if (args[0] != "/baltop" && args[0] != "/balancetop") {
            if (args[0] == "/balance" || args[0] == "/bal") {
                event.isCancelled = true
                handleCommands(event.player as CommandSender, args.drop(1), CommandType.BALANCE)
                return
            }
            return
        }

        event.isCancelled = true
        handleCommands(event.player as CommandSender, args.drop(1), CommandType.BALANCETOP)
    }

    @EventHandler
    fun onServerCommand(event: ServerCommandEvent) {
        val args = event.command.split(" ", limit = 3)
        if (args[0] != "baltop" && args[0] != "balancetop") {
            if (args[0] == "balance" || args[0] == "bal") {
                event.isCancelled = true
                handleCommands(event.sender, args.drop(1), CommandType.BALANCE)
                return
            }
            return
        }

        event.isCancelled = true
        handleCommands(event.sender, args.drop(1), CommandType.BALANCETOP)
    }

    private fun handleCommands(sender: CommandSender, args: List<String>, commandType: CommandType) {
        // TODO: Handle /give command
        GlobalScope.launch {
            if (commandType == CommandType.BALANCE) {
                if (!sender.hasPermission("diamondbank-og.balance")) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You do not have permission to use this command."))
                    return@launch
                }

                if (sender !is Player) {
                    if (args.isEmpty()) {
                        sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Please provide that name or UUID of the player that you want to check the balance of."))
                        return@launch
                    }

                    val otherPlayer = Bukkit.getOfflinePlayer(args[0])
                    if (!otherPlayer.hasPlayedBefore()) {
                        sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>That player doesn't exist."))
                        return@launch
                    }

                    val balance = DiamondBankOG.postgreSQL.getPlayerBalance(
                        otherPlayer.uniqueId,
                        BalanceType.ALL
                    )
                    if (balance.bankBalance == null || balance.inventoryBalance == null || balance.enderChestBalance == null) {
                        sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to get your balance."))
                        return@launch
                    }
                    val totalBalance = balance.bankBalance + balance.inventoryBalance + balance.enderChestBalance
                    sender.sendMessage(DiamondBankOG.mm.deserialize("<green>Balance of <red>${otherPlayer.name}<green>: <yellow>$totalBalance <aqua>${if (totalBalance == 1L) "Diamond" else "Diamonds"} <white>(<red>Bank: <yellow>${balance.bankBalance}<white>, <red>Inventory: <yellow>${balance.inventoryBalance}<white>, <red>Ender Chest: <yellow>${balance.enderChestBalance}<white>)."))
                    return@launch
                }

                val balancePlayer = if (args.isEmpty()) {
                    sender
                } else {
                    if (!sender.hasPermission("diamondbank-og.balance.others")) {
                        sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You do not have permission to use this command."))
                        return@launch
                    }

                    val otherPlayer = Bukkit.getOfflinePlayer(args[0])
                    if (!otherPlayer.hasPlayedBefore()) {
                        sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>That player doesn't exist."))
                        return@launch
                    }
                    otherPlayer
                }
                val balance = DiamondBankOG.postgreSQL.getPlayerBalance(
                    balancePlayer.uniqueId,
                    BalanceType.ALL
                )
                if (balance.bankBalance == null || balance.inventoryBalance == null || balance.enderChestBalance == null) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to get your balance."))
                    return@launch
                }
                val totalBalance = balance.bankBalance + balance.inventoryBalance + balance.enderChestBalance
                sender.sendMessage(DiamondBankOG.mm.deserialize("<green>Balance${if (balancePlayer.uniqueId != sender.uniqueId) " of <red>${balancePlayer.name}" else ""}<green>: <yellow>$totalBalance <aqua>${if (totalBalance == 1L) "Diamond" else "Diamonds"} <white>(<red>Bank: <yellow>${balance.bankBalance}<white>, <red>Inventory: <yellow>${balance.inventoryBalance}<white>, <red>Ender Chest: <yellow>${balance.enderChestBalance}<white>)."))
                return@launch
            }

            if (!sender.hasPermission("diamondbank-og.baltop")) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You do not have permission to use this command."))
                return@launch
            }

            if (args.isNotEmpty() && args.size != 1) {
                sender.sendMessage("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Please do not provide any arguments or only provide the number of the page.")
                return@launch
            }

            var offset = 0
            var index = 1
            if (args.size == 1) {
                try {
                    index = args[0].toInt()
                } catch (_: Exception) {
                    sender.sendMessage("Invalid argument")
                    return@launch
                }
                offset = 10 * (index - 1)
            }

            val baltop = DiamondBankOG.postgreSQL.getBaltop(offset)
            if (baltop == null) {
                sender.sendMessage("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to get the information for balancetop.")
                return@launch
            }
            val numberOfRows = DiamondBankOG.postgreSQL.getNumberOfRows()
            if (numberOfRows == null) {
                sender.sendMessage("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to get the information for balancetop.")
                return@launch
            }

            if (index > ceil(numberOfRows / 10.0)) {
                sender.sendMessage("The amount of pages only goes up to $numberOfRows")
                return@launch
            }
            var baltopMessage =
                ("<yellow>---- <gold>Balancetop <yellow>-- <gold>Page <red>$index<gold>/<red>${ceil(numberOfRows / 10.0).toLong()} <yellow>----<reset>")
            baltop.forEach {
                if (it.key == null) {
                    sender.sendMessage("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to get the information for balancetop.")
                    return@launch
                }
                baltopMessage += "\n<red>${baltopMessage.lines().size + (10 * (index - 1))}<reset>. ${if (it.key == sender.name) "<red>" else ""}${it.key}<reset>, ${it.value}"
            }

            sender.sendMessage(DiamondBankOG.mm.deserialize(baltopMessage))
        }
    }
}