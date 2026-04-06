package net.trueog.diamondbankog.config.command

import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.balance.CachingBalanceManager
import net.trueog.diamondbankog.config.YamlConfig
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

internal class DiamondBankReload : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        DiamondBankOG.config =
            YamlConfig.create()
                ?: run {
                    sender.sendMessage(
                        DiamondBankOG.mm.deserialize(
                            "${DiamondBankOG.config.prefix}<reset>: <red>Failed to reload the config. Check the console for more information."
                        )
                    )
                    Bukkit.getPluginManager().disablePlugin(DiamondBankOG.plugin)
                    return true
                }

        DiamondBankOG.balanceManager =
            CachingBalanceManager.create()
                ?: run {
                    sender.sendMessage(
                        DiamondBankOG.mm.deserialize(
                            "${DiamondBankOG.config.prefix}<reset>: <red>Failed to reload the config. Check the console for more information."
                        )
                    )
                    Bukkit.getPluginManager().disablePlugin(DiamondBankOG.plugin)
                    return true
                }

        sender.sendMessage(
            DiamondBankOG.mm.deserialize(
                "${DiamondBankOG.config.prefix}<reset>: <green>Successfully reloaded the config."
            )
        )
        return true
    }
}
