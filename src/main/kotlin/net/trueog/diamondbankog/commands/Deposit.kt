package net.trueog.diamondbankog.commands

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class Deposit : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (sender !is Player) {
            sender.sendMessage("You can only execute this command as a player.")
            return true
        }

        if (!sender.hasPermission("diamondbank-og.deposit")) {
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You do not have permission to use this command."))
            return true
        }

        if (args == null || args.isEmpty()) {
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You did not provide the amount of <aqua>dDiamonds <red>that you want to deposit."))
            return true
        }
        if (args.size != 1) {
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Please (only) provide the amount of <aqua>dDiamonds <red>you want to deposit. Either a number or \"all\"."))
            return true
        }

        val allDiamonds = sender.inventory.all(Material.DIAMOND).values.sumOf { it.amount }
        if (allDiamonds == 0) {
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You don't have any <aqua>Diamonds <red>to deposit."))
            return true
        }

        var diamondsToDeposit = allDiamonds

        if (args[0] != "all") {
            val amount: Int
            try {
                amount = args[0].toInt()
            } catch (_: Exception) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Invalid argument."))
                return true
            }

            if (amount > allDiamonds) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You do not have <yellow>$amount <aqua>${if (amount == 1) "Diamond" else "Diamonds"} <red>in your inventory."))
                return true
            }
            diamondsToDeposit = amount
        }

        sender.inventory.removeItem(ItemStack(Material.DIAMOND, diamondsToDeposit))
        GlobalScope.launch {
            val error = DiamondBankOG.postgreSQL.depositToPlayerBalance(sender.uniqueId, diamondsToDeposit.toLong())
            if (error) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to deposit."))
            }
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <green>Successfully deposited <yellow>$diamondsToDeposit <aqua>${if (diamondsToDeposit == 1) "Diamond" else "Diamonds"} <green>into your bank account."))
        }

        return true
    }
}