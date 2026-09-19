package net.trueog.diamondbankog.commands

import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coVerify
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
import net.trueog.diamondbankog.balance.command.SetBankBalance
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

class SetBankBalanceTest {
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
        every { player.hasPermission("diamondbank-og.setbankbalance") } returns true

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
    @DisplayName("SetBankBalance with no permission should fail")
    fun setBankBalanceNoPermission() = runTest {
        every { player.hasPermission("diamondbank-og.setbankbalance") } returns false

        val setBankBalance = SetBankBalance(config, balanceManager, mm, scope)
        setBankBalance.onCommand(player, command, "setbankbalance", arrayOf())
        waitForCoroutines(scope)

        assertAll(
            { verify { player.hasPermission("diamondbank-og.setbankbalance") } },
            {
                verify {
                    player.sendMessage(
                        Component.text("DiamondBank-OG<reset>: <red>You do not have permission to use this command.")
                    )
                }
            },
            { coVerify(exactly = 0) { balanceManager.setPlayerShards(any(), any(), any()) } },
        )
    }
}
