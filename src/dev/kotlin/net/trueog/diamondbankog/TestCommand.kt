package net.trueog.diamondbankog

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

internal class TestCommand : CommandExecutor {
    val tests: Array<Test> = arrayOf(InventoryUtilsTest)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        for (test in tests) {
            sender.sendMessage("${test.name}:")
            for (result in test.runTests()) {
                sender.sendMessage(MiniMessage.miniMessage().deserialize("   $result"))
            }
        }
        return true
    }
}
