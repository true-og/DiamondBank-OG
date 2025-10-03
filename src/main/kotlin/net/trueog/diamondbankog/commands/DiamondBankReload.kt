package net.trueog.diamondbankog.commands

import net.trueog.diamondbankog.BalanceManager
import net.trueog.diamondbankog.Config
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.mm
import net.trueog.diamondbankog.DiamondBankOG.Companion.plugin
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

internal class DiamondBankReload : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        config =
            Config.create()
                ?: run {
                    sender.sendMessage(
                        mm.deserialize("<red>Failed to reload the config. Check the console for more information.")
                    )
                    Bukkit.getPluginManager().disablePlugin(plugin)
                    return true
                }

        balanceManager = BalanceManager()
        try {
            balanceManager.init()
        } catch (e: Exception) {
            e.printStackTrace()
            sender.sendMessage(
                mm.deserialize("<red>Failed to reload the config. Check the console for more information.")
            )
            Bukkit.getPluginManager().disablePlugin(plugin)
            return true
        }

        sender.sendMessage(mm.deserialize("${config.prefix}<reset>: <green>Successfully reloaded the config."))
        return true
    }
}
