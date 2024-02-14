package net.trueog.diamondbankog.commands

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.Config
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.Helper
import net.trueog.diamondbankog.Helper.PostgresFunction
import net.trueog.diamondbankog.PostgreSQL.BalanceType
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class Withdraw : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        GlobalScope.launch {
            if (DiamondBankOG.economyDisabled) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>The economy is disabled because of a severe error. Please notify a staff member."))
                return@launch
            }

            if (sender !is Player) {
                sender.sendMessage("You can only execute this command as a player.")
                return@launch
            }

            if (DiamondBankOG.blockCommandsWithInventoryActionsFor.contains(sender.uniqueId)) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You are currently blocked from using /withdraw."))
                return@launch
            }

            val worldName = sender.world.name
            if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You cannot use /withdraw when in a minigame."))
                return@launch
            }

            if (!sender.hasPermission("diamondbank-og.withdraw")) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You do not have permission to use this command."))
                return@launch
            }

            if (args == null || args.isEmpty()) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You did not provide the amount of <aqua>Diamonds <red>that you want to withdraw."))
                return@launch
            }
            if (args.size != 1) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Please (only) provide the amount of <aqua>Diamonds <red>you want to withdraw. Either a number or \"all\"."))
                return@launch
            }

            var amount = -1L
            if (args[0] != "all") {
                try {
                    amount = args[0].toLong()
                    if (amount < 0) {
                        sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You cannot withdraw a negative amount."))
                        return@launch
                    }
                } catch (_: Exception) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Invalid argument."))
                    return@launch
                }
            }

            val playerBalance =
                DiamondBankOG.postgreSQL.getPlayerBalance(sender.uniqueId, BalanceType.BANK_BALANCE)
            if (playerBalance.bankBalance == null) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong while trying to get your balance."))
                return@launch
            }

            if (amount == -1L) amount = playerBalance.bankBalance

            if (amount > playerBalance.bankBalance) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Cannot withdraw <yellow>$amount <aqua>${if (amount == 1L) "Diamond" else "Diamonds"} <red>because your bank only contains <yellow>${playerBalance.bankBalance} <aqua>${if (playerBalance.bankBalance == 1L) "diamond" else "diamonds"}<red>."))
                return@launch
            }

            DiamondBankOG.blockInventoryFor.add(sender.uniqueId)
            val emptySlots = sender.inventory.storageContents.filter { it == null }.size * 64
            val leftOverSpace = sender.inventory.storageContents.filterNotNull().filter { it.type == Material.DIAMOND }
                .sumOf { 64 - it.amount }
            if (amount > (emptySlots + leftOverSpace)) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You don't have enough inventory space to withdraw <yellow>$amount <aqua>${if (amount == 1L) "Diamond" else "Diamonds"}<red>."))
                DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
                return@launch
            }

            var error = DiamondBankOG.postgreSQL.subtractFromPlayerBalance(
                sender.uniqueId,
                amount,
                BalanceType.BANK_BALANCE
            )
            if (error) {
                Helper.handleError(
                    sender.uniqueId,
                    PostgresFunction.SUBTRACT_FROM_PLAYER_BALANCE,
                    amount,
                    BalanceType.BANK_BALANCE,
                    playerBalance,
                    "withdraw"
                )

                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong while trying to withdraw."))
                DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
                return@launch
            }

            error = DiamondBankOG.postgreSQL.addToPlayerBalance(
                sender.uniqueId,
                amount,
                BalanceType.INVENTORY_BALANCE
            )
            if (error) {
                Helper.handleError(
                    sender.uniqueId,
                    PostgresFunction.ADD_TO_PLAYER_BALANCE,
                    amount,
                    BalanceType.INVENTORY_BALANCE,
                    playerBalance,
                    "withdraw"
                )

                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong while trying to withdraw."))
                DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
                return@launch
            }
            sender.inventory.addItem(ItemStack(Material.DIAMOND, amount.toInt()))
            DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
            sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <green>Successfully withdrew <yellow>$amount <aqua>${if (amount == 1L) "Diamond" else "Diamonds"} <green>from your bank account."))
        }
        return true
    }
}