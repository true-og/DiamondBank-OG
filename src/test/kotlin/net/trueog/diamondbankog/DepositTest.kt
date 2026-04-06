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
import net.trueog.diamondbankog.Utils.mockPlayerInventory
import net.trueog.diamondbankog.Utils.waitForCoroutines
import net.trueog.diamondbankog.balance.BalanceManager
import net.trueog.diamondbankog.balance.shard.Shard
import net.trueog.diamondbankog.balance.shard.ShardType
import net.trueog.diamondbankog.transaction.command.Deposit
import net.trueog.diamondbankog.config.Config
import net.trueog.diamondbankog.transaction.InventoryLockExtensions.isLocked
import net.trueog.diamondbankog.transaction.TransactionLock
import net.trueog.diamondbankog.util.InventoryExtensions.countDiamondBlocks
import net.trueog.diamondbankog.util.InventoryExtensions.countDiamonds
import net.trueog.diamondbankog.util.InventoryExtensions.countShards
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Server
import org.bukkit.command.Command
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

class DepositTest {
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
        every { player.hasPermission("diamondbank-og.deposit") } returns true

        every { Bukkit.getPlayer("player") } returns player
        every { server.getPlayer("player") } returns player
        every { Bukkit.getPlayer(playerUuid) } returns player
        every { server.getPlayer(playerUuid) } returns player

        coEvery { balanceManager.addToBankShards(any(), any()) } returns Result.success(Unit)
        coEvery { balanceManager.setPlayerShards(any(), any(), ShardType.INVENTORY) } returns Result.success(Unit)
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

        @JvmStatic
        fun depositData() =
            listOf(
                Arguments.of(
                    "1 Diamond",
                    "1.0 <aqua>Diamond",
                    arrayOf(ItemStack(Material.DIAMOND, 1)),
                    "1",
                    arrayOf<Long>(9),
                    0,
                    0,
                    0,
                ),
                Arguments.of(
                    "2 Diamonds",
                    "2.0 <aqua>Diamonds",
                    arrayOf(ItemStack(Material.DIAMOND, 3)),
                    "2",
                    arrayOf<Long>(2 * 9),
                    0,
                    1,
                    0,
                ),
                Arguments.of(
                    "2.2 Diamonds only diamonds",
                    "2.2 <aqua>Diamonds",
                    arrayOf(ItemStack(Material.DIAMOND, 3)),
                    "2.2",
                    arrayOf<Long>(20),
                    7,
                    0,
                    0,
                ),
                Arguments.of(
                    "2.2 Diamonds",
                    "2.2 <aqua>Diamonds",
                    arrayOf(Shard.createItemStack(2), ItemStack(Material.DIAMOND, 3)),
                    "2.2",
                    arrayOf<Long>(20),
                    0,
                    1,
                    0,
                ),
                Arguments.of(
                    "2.2 Diamonds with blocks",
                    "2.2 <aqua>Diamonds",
                    arrayOf(ItemStack(Material.DIAMOND_BLOCK, 1)),
                    "2.2",
                    arrayOf<Long>(20),
                    7,
                    6,
                    0,
                ),
                Arguments.of(
                    "2.9 Diamonds",
                    "3.0 <aqua>Diamonds",
                    arrayOf(ItemStack(Material.DIAMOND, 4)),
                    "2.9",
                    arrayOf<Long>(27),
                    0,
                    1,
                    0,
                ),
                Arguments.of(
                    "all with 5 Diamonds",
                    "5.0 <aqua>Diamonds",
                    arrayOf(ItemStack(Material.DIAMOND, 5)),
                    "all",
                    arrayOf<Long>(45),
                    0,
                    0,
                    0,
                ),
                Arguments.of(
                    "all with 5 Diamonds and 8 shards",
                    "5.8 <aqua>Diamonds",
                    arrayOf(Shard.createItemStack(8), ItemStack(Material.DIAMOND, 5)),
                    "all",
                    arrayOf<Long>(53),
                    0,
                    0,
                    0,
                ),
                Arguments.of(
                    "1.8 Diamonds with overflow",
                    "1.8 <aqua>Diamonds",
                    Array(35) { ItemStack(Material.DIRT, 1) } + arrayOf(ItemStack(Material.DIAMOND, 3)),
                    "1.8",
                    arrayOf<Long>(17, 1),
                    0,
                    1,
                    0,
                ),
                Arguments.of(
                    "1.1 Diamonds with diamond block with overflow",
                    "1.1 <aqua>Diamonds",
                    Array(35) { ItemStack(Material.DIRT, 1) } + arrayOf(ItemStack(Material.DIAMOND_BLOCK, 2)),
                    "1.1",
                    arrayOf<Long>(10, 63, 8),
                    0,
                    0,
                    1,
                ),
            )
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Successful Deposit")
    @MethodSource("depositData")
    fun successfulDeposit(
        @Suppress("UNUSED_PARAMETER") name: String,
        successDiamondText: String,
        inventoryContents: Array<ItemStack?>,
        commandArg: String,
        addedShards: Array<Long>,
        invShardCount: Long,
        invDiamondCount: Long,
        invDiamondBlockCount: Long,
    ) = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, inventoryContents)

        val deposit = Deposit(config, balanceManager, mm, scope, transactionLock)
        deposit.onCommand(player, command, "deposit", arrayOf(commandArg))
        waitForCoroutines(scope)

        assertAll(
            buildList {
                add {
                    verify {
                        player.sendMessage(
                            Component.text(
                                "DiamondBank-OG<reset>: <green>Successfully deposited <yellow>$successDiamondText <green>into your bank account."
                            )
                        )
                    }
                }
                addedShards.forEach { add { coVerify { balanceManager.addToBankShards(playerUuid, it) } } }
                add { assertEquals(invShardCount, inventory.countShards(), "Shard count") }
                add { assertEquals(invDiamondCount, inventory.countDiamonds(), "Diamond count") }
                add { assertEquals(invDiamondBlockCount, inventory.countDiamondBlocks(), "Diamond block count") }
                add { assertFalse(inventory.isLocked(), "Inventory locked") }
                add { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") }
            }
        )
    }

    @Test
    @DisplayName("Deposit 2 Diamonds while only having 1")
    fun depositWholeDiamondsWhileShort() = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, arrayOf(ItemStack(Material.DIAMOND, 1)))

        val deposit = Deposit(config, balanceManager, mm, scope, transactionLock)
        deposit.onCommand(player, command, "deposit", arrayOf("2"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text(
                            "DiamondBank-OG<reset>: <red>You do not have <yellow>2.0 <aqua>Diamonds <red>to deposit."
                        )
                    )
                }
            },
            { coVerify(exactly = 0) { balanceManager.addToBankShards(any(), any()) } },
            { assertEquals(1, inventory.countDiamonds(), "Diamond count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Deposit while in minigame should fail")
    fun depositWhileInMinigame() = runTest {
        every { player.world.name } returns "minigame_world"
        val inventory = mockPlayerInventory(player, playerUuid, arrayOf(ItemStack(Material.DIAMOND, 5)))

        val deposit = Deposit(config, balanceManager, mm, scope, transactionLock)
        deposit.onCommand(player, command, "deposit", arrayOf("all"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text("DiamondBank-OG<reset>: <red>You cannot use /deposit when in a minigame.")
                    )
                }
            },
            { coVerify(exactly = 0) { balanceManager.addToBankShards(any(), any()) } },
            { assertEquals(5, inventory.countDiamonds(), "Diamond count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Deposit while locked should fail")
    fun depositWhileLocked() = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, arrayOf(ItemStack(Material.DIAMOND, 5)))

        coEvery { transactionLock.tryWithLockSuspend<Unit>(any(), any()) } returns TransactionLock.LockResult.Failed

        val deposit = Deposit(config, balanceManager, mm, scope, transactionLock)
        deposit.onCommand(player, command, "deposit", arrayOf("all"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text("DiamondBank-OG<reset>: <red>You are currently blocked from using /deposit.")
                    )
                }
            },
            { coVerify(exactly = 0) { balanceManager.addToBankShards(any(), any()) } },
            { assertEquals(5, inventory.countDiamonds(), "Diamond count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Deposit no arguments should fail")
    fun depositNoArgs() = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, arrayOf(ItemStack(Material.DIAMOND, 5)))

        val deposit = Deposit(config, balanceManager, mm, scope, transactionLock)
        deposit.onCommand(player, command, "deposit", arrayOf())
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text(
                            "DiamondBank-OG<reset>: <red>You did not provide the amount of <aqua>Diamonds <red>that you want to deposit."
                        )
                    )
                }
            },
            { coVerify(exactly = 0) { balanceManager.addToBankShards(any(), any()) } },
            { assertEquals(5, inventory.countDiamonds(), "Diamond count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Deposit too many arguments should fail")
    fun depositTooManyArgs() = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, arrayOf(ItemStack(Material.DIAMOND, 5)))

        val deposit = Deposit(config, balanceManager, mm, scope, transactionLock)
        deposit.onCommand(player, command, "deposit", arrayOf("1", "2"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text(
                            "DiamondBank-OG<reset>: <red>Please (only) provide the amount of <aqua>Diamonds <red>you want to deposit. Either a number or \"all\"."
                        )
                    )
                }
            },
            { coVerify(exactly = 0) { balanceManager.addToBankShards(any(), any()) } },
            { assertEquals(5, inventory.countDiamonds(), "Diamond count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Deposit with invalid argument should fail")
    @CsvSource(
        "Not a number, abc, <red>Invalid argument",
        "Negative, -1, <red>You cannot deposit a negative or zero amount",
        "Zero, 0, <red>You cannot deposit a negative or zero amount",
        "More than one decimal digit, 1.11, <aqua>Diamonds<red> can only have one decimal digit. Issue /diamondbankhelp for more information",
    )
    fun depositInvalidArg(@Suppress("UNUSED_PARAMETER") name: String, argument: String, errorMessage: String) =
        runTest {
            val inventory = mockPlayerInventory(player, playerUuid, arrayOf(ItemStack(Material.DIAMOND, 5)))

            val deposit = Deposit(config, balanceManager, mm, scope, transactionLock)
            deposit.onCommand(player, command, "deposit", arrayOf(argument))
            waitForCoroutines(scope)

            assertAll(
                { verify { player.sendMessage(Component.text("DiamondBank-OG<reset>: $errorMessage.")) } },
                { coVerify(exactly = 0) { balanceManager.addToBankShards(any(), any()) } },
                { assertEquals(5, inventory.countDiamonds(), "Diamond count") },
                { assertFalse(inventory.isLocked(), "Inventory locked") },
                { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
            )
        }

    @Test
    @DisplayName("Deposit with no permission should fail")
    fun depositNoPermission() = runTest {
        every { player.hasPermission("diamondbank-og.deposit") } returns false
        val inventory = mockPlayerInventory(player, playerUuid, arrayOf(ItemStack(Material.DIAMOND, 5)))

        val deposit = Deposit(config, balanceManager, mm, scope, transactionLock)
        deposit.onCommand(player, command, "deposit", arrayOf("1"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text("DiamondBank-OG<reset>: <red>You do not have permission to use this command.")
                    )
                }
            },
            { coVerify(exactly = 0) { balanceManager.addToBankShards(any(), any()) } },
            { assertEquals(5, inventory.countDiamonds(), "Diamond count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }
}
