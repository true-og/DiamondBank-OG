package net.trueog.diamondbankog

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.trueog.diamondbankog.Constants.playerUuid
import net.trueog.diamondbankog.InventoryExtensions.countDiamonds
import net.trueog.diamondbankog.InventoryExtensions.countShards
import net.trueog.diamondbankog.InventoryExtensions.countTotal
import net.trueog.diamondbankog.InventoryExtensions.isLocked
import net.trueog.diamondbankog.Utils.mockPlayerInventory
import net.trueog.diamondbankog.Utils.waitForCoroutines
import net.trueog.diamondbankog.commands.Withdraw
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Server
import org.bukkit.command.Command
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class WithdrawTest {
    @MockK private lateinit var config: Config

    @MockK private lateinit var balanceManager: BalanceManager

    @MockK private lateinit var mm: MiniMessage

    @MockK private lateinit var player: Player

    @MockK private lateinit var command: Command

    @SpyK private var transactionLock = TransactionLock()

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        every { config.prefix } returns "DiamondBank-OG"

        val inputSlot = slot<String>()
        every { mm.deserialize(capture(inputSlot)) } answers { Component.text(inputSlot.captured) }

        every { player.uniqueId } returns playerUuid
        every { player.world.name } returns "world"
        every { player.hasPermission("diamondbank-og.withdraw") } returns true

        every { Bukkit.getPlayer("player") } returns player
        every { server.getPlayer("player") } returns player

        coEvery { balanceManager.addToBankShards(any(), any()) } returns Result.success(Unit)
        coEvery { balanceManager.setPlayerShards(any(), any(), PostgreSQL.ShardType.INVENTORY) } returns
            Result.success(Unit)
        coEvery { balanceManager.subtractFromBankShards(any(), any()) } returns Result.success(Unit)
        coEvery { balanceManager.insertTransactionLog(any(), any(), any(), any(), any()) } returns Result.success(Unit)
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

    @ParameterizedTest(name = "{0}")
    @DisplayName("Successful withdraw")
    @CsvSource(
        "2 Diamonds, 2.0 <aqua>Diamonds, 2, 0, 2, 18",
        "2.2 Diamonds, 2.2 <aqua>Diamonds, 2.2, 2, 2, 20",
        "2.9 Diamonds, 3.0 <aqua>Diamonds, 2.9, 0, 3, 27",
        "All, 11.1 <aqua>Diamonds, all, 1, 11, 100",
    )
    fun successfulWithdraw(
        @Suppress("UNUSED_PARAMETER") name: String,
        successDiamondText: String,
        commandArg: String,
        invShardsCount: Long,
        invDiamondsCount: Long,
        removedShardCount: Long,
    ) = runTest {
        coEvery { balanceManager.getBankShards(playerUuid) } returns Result.success(100)
        val inventory = mockPlayerInventory(player, playerUuid, emptyArray())

        val withdraw = Withdraw(config, balanceManager, mm, scope, transactionLock)
        withdraw.onCommand(player, command, "withdraw", arrayOf(commandArg))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text(
                            "DiamondBank-OG<reset>: <green>Successfully withdrew <yellow>$successDiamondText <green>from your bank account."
                        )
                    )
                }
            },
            { coVerify { balanceManager.subtractFromBankShards(playerUuid, removedShardCount) } },
            { assertEquals(invShardsCount, inventory.countShards(), "Shard count") },
            { assertEquals(invDiamondsCount, inventory.countDiamonds(), "Diamond count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Withdraw while in minigame should fail")
    fun withdrawWhileInMinigame() = runTest {
        every { player.world.name } returns "minigame_world"
        val inventory = mockPlayerInventory(player, playerUuid, emptyArray())

        val withdraw = Withdraw(config, balanceManager, mm, scope, transactionLock)
        withdraw.onCommand(player, command, "withdraw", arrayOf("all"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text("DiamondBank-OG<reset>: <red>You cannot use /withdraw when in a minigame.")
                    )
                }
            },
            { coVerify(exactly = 0) { balanceManager.subtractFromBankShards(any(), any()) } },
            { assertEquals(0, inventory.countTotal(), "Total count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Withdraw while locked should fail")
    fun withdrawWhileLocked() = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, emptyArray())

        coEvery { transactionLock.tryWithLockSuspend<Unit>(any(), any()) } returns TransactionLock.LockResult.Failed

        val withdraw = Withdraw(config, balanceManager, mm, scope, transactionLock)
        withdraw.onCommand(player, command, "withdraw", arrayOf("all"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text("DiamondBank-OG<reset>: <red>You are currently blocked from using /withdraw.")
                    )
                }
            },
            { coVerify(exactly = 0) { balanceManager.subtractFromBankShards(any(), any()) } },
            { assertEquals(0, inventory.countTotal(), "Total count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Withdraw no arguments should fail")
    fun withdrawNoArgs() = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, emptyArray())

        val withdraw = Withdraw(config, balanceManager, mm, scope, transactionLock)
        withdraw.onCommand(player, command, "withdraw", arrayOf())
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text(
                            "DiamondBank-OG<reset>: <red>You did not provide the amount of <aqua>Diamonds <red>that you want to withdraw."
                        )
                    )
                }
            },
            { coVerify(exactly = 0) { balanceManager.subtractFromBankShards(any(), any()) } },
            { assertEquals(0, inventory.countTotal(), "Total count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Withdraw too many arguments should fail")
    fun withdrawTooManyArgs() = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, emptyArray())

        val withdraw = Withdraw(config, balanceManager, mm, scope, transactionLock)
        withdraw.onCommand(player, command, "withdraw", arrayOf("1", "2"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text(
                            "DiamondBank-OG<reset>: <red>Please (only) provide the amount of <aqua>Diamonds <red>you want to withdraw. Either a number or \"all\"."
                        )
                    )
                }
            },
            { coVerify(exactly = 0) { balanceManager.subtractFromBankShards(any(), any()) } },
            { assertEquals(0, inventory.countTotal(), "Total count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Withdraw with invalid argument should fail")
    @CsvSource(
        "Not a number, abc, <red>Invalid argument",
        "Negative, -1, <red>You cannot withdraw a negative or zero amount",
        "Zero, 0, <red>You cannot withdraw a negative or zero amount",
        "More than one decimal digit, 1.11, <aqua>Diamonds<red> can only have one decimal digit. Issue /diamondbankhelp for more information",
    )
    fun withdrawInvalidArg(@Suppress("UNUSED_PARAMETER") name: String, argument: String, errorMessage: String) =
        runTest {
            val inventory = mockPlayerInventory(player, playerUuid, emptyArray())

            val withdraw = Withdraw(config, balanceManager, mm, scope, transactionLock)
            withdraw.onCommand(player, command, "withdraw", arrayOf(argument))
            waitForCoroutines(scope)

            assertAll(
                { verify { player.sendMessage(Component.text("DiamondBank-OG<reset>: $errorMessage.")) } },
                { coVerify(exactly = 0) { balanceManager.subtractFromBankShards(any(), any()) } },
                { assertEquals(0, inventory.countTotal(), "Total count") },
                { assertFalse(inventory.isLocked(), "Inventory locked") },
                { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
            )
        }

    @Test
    @DisplayName("Withdraw with no permission should fail")
    fun withdrawNoPermission() = runTest {
        every { player.hasPermission("diamondbank-og.withdraw") } returns false
        val inventory = mockPlayerInventory(player, playerUuid, emptyArray())

        val withdraw = Withdraw(config, balanceManager, mm, scope, transactionLock)
        withdraw.onCommand(player, command, "withdraw", arrayOf("1"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text("DiamondBank-OG<reset>: <red>You do not have permission to use this command.")
                    )
                }
            },
            { coVerify(exactly = 0) { balanceManager.subtractFromBankShards(any(), any()) } },
            { assertEquals(0, inventory.countTotal(), "Total count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Withdraw when not enough Diamonds in bank should fail")
    fun withdrawNotEnoughDiamondsInBank() = runTest {
        coEvery { balanceManager.getBankShards(playerUuid) } returns Result.success(18)
        val inventory = mockPlayerInventory(player, playerUuid, emptyArray())

        val withdraw = Withdraw(config, balanceManager, mm, scope, transactionLock)
        withdraw.onCommand(player, command, "withdraw", arrayOf("5"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text(
                            "DiamondBank-OG<reset>: <red>Cannot withdraw <yellow>5.0 <aqua>Diamonds <red>because your bank only contains <yellow>2.0 <aqua>Diamonds<red>."
                        )
                    )
                }
            },
            { coVerify(exactly = 0) { balanceManager.subtractFromBankShards(any(), any()) } },
            { assertEquals(0, inventory.countTotal(), "Total count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Withdraw when not enough inventory space should fail")
    fun withdrawNotEnoughInventorySpace() = runTest {
        coEvery { balanceManager.getBankShards(playerUuid) } returns Result.success(18)
        val inventory = mockPlayerInventory(player, playerUuid, Array(36) { ItemStack(Material.DIRT, 1) })

        val withdraw = Withdraw(config, balanceManager, mm, scope, transactionLock)
        withdraw.onCommand(player, command, "withdraw", arrayOf("2"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text(
                            "DiamondBank-OG<reset>: <red>You don't have enough inventory space to withdraw <yellow>2.0 <aqua>Diamonds<red>."
                        )
                    )
                }
            },
            { coVerify(exactly = 0) { balanceManager.subtractFromBankShards(any(), any()) } },
            { assertEquals(0, inventory.countTotal(), "Total count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }
}
