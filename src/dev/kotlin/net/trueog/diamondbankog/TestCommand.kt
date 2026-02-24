package net.trueog.diamondbankog

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

internal class TestCommand : CommandExecutor {
    val tests: Array<Test> = arrayOf(InventoryUtilsTest)

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        GlobalScope.launch {
            for (test in tests) {
                sender.sendMessage("${test.name}:")
                for (result in test.runTests()) {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("   $result"))
                }
            }
        }
        return true
    }
}
