package net.trueog.diamondbankog.commands

import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.trueog.diamondbankog.BukkitMock
import net.trueog.diamondbankog.Constants.playerUuid
import net.trueog.diamondbankog.Utils.waitForCoroutines
import net.trueog.diamondbankog.balance.BalanceManager
import net.trueog.diamondbankog.balance.command.Balancetop
import net.trueog.diamondbankog.config.Config
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.command.Command
import org.bukkit.entity.Player
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class BalancetopTest {
    @MockK private lateinit var config: Config

    @MockK private lateinit var balanceManager: BalanceManager

    @MockK private lateinit var mm: MiniMessage

    @MockK private lateinit var player: Player

    @MockK private lateinit var command: Command

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        every { config.prefix } returns "DiamondBank-OG"

        val inputSlot = slot<String>()
        every { mm.deserialize(capture(inputSlot)) } answers { Component.text(inputSlot.captured) }

        every { player.uniqueId } returns playerUuid
        every { player.world.name } returns "world"
        every { player.hasPermission("diamondbank-og.balance") } returns true

        every { Bukkit.getPlayer("player") } returns player
        every { server.getPlayer("player") } returns player
        every { Bukkit.getPlayer(playerUuid) } returns player
        every { server.getPlayer(playerUuid) } returns player

        every { player.sendMessage(any<Component>()) } just Runs
    }

    companion object {
        lateinit var server: Server

        @JvmStatic
        @BeforeAll
        fun setupBukkit() {
            server = BukkitMock.mockBukkit()
        }
    }

    @Test
    @DisplayName("Balancetop with no permission should fail")
    fun balancetopNoPermission() = runTest {
        every { player.hasPermission("diamondbank-og.balancetop") } returns false

        val balancetop = Balancetop(config, balanceManager, mm, scope)
        balancetop.onCommand(player, command, "balancetop", arrayOf())
        waitForCoroutines(scope)

        assertAll(
            { verify { player.hasPermission("diamondbank-og.balancetop") } },
            {
                verify {
                    player.sendMessage(
                        Component.text("DiamondBank-OG<reset>: <red>You do not have permission to use this command.")
                    )
                }
            },
            { confirmVerified(player) },
        )
    }
}
