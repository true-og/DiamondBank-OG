package net.trueog.diamondbankog

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.luckperms.api.LuckPerms
import net.luckperms.api.model.user.User
import net.luckperms.api.node.Node
import net.trueog.diamondbankog.Constants.otherPlayerUuid
import net.trueog.diamondbankog.Constants.playerUuid
import net.trueog.diamondbankog.InventoryExtensions.countDiamondBlocks
import net.trueog.diamondbankog.InventoryExtensions.countDiamonds
import net.trueog.diamondbankog.InventoryExtensions.countShards
import net.trueog.diamondbankog.InventoryExtensions.countTotal
import net.trueog.diamondbankog.InventoryExtensions.isLocked
import net.trueog.diamondbankog.Utils.mockPlayerInventory
import net.trueog.diamondbankog.Utils.waitForCoroutines
import net.trueog.diamondbankog.commands.Pay
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

class PayTest {
    @MockK private lateinit var config: Config

    @MockK private lateinit var balanceManager: BalanceManager

    @MockK private lateinit var mm: MiniMessage

    @MockK private lateinit var player: Player

    @MockK private lateinit var otherPlayer: Player

    @MockK private lateinit var command: Command

    @MockK private lateinit var luckPerms: LuckPerms

    @SpyK private var transactionLock = TransactionLock()

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        every { config.prefix } returns "DiamondBank-OG"

        val inputSlot = slot<String>()
        every { mm.deserialize(capture(inputSlot)) } answers { Component.text(inputSlot.captured) }

        every { player.uniqueId } returns playerUuid
        every { player.name } returns "Player"
        every { player.world.name } returns "world"
        every { player.hasPermission("diamondbank-og.pay") } returns true
        every { otherPlayer.uniqueId } returns otherPlayerUuid
        every { otherPlayer.name } returns "OtherPlayer"
        every { otherPlayer.isOnline } returns true
        every { otherPlayer.player } returns otherPlayer

        every { Bukkit.getPlayer("player") } returns player
        every { server.getPlayer("player") } returns player
        every { Bukkit.getPlayer(playerUuid) } returns player
        every { server.getPlayer(playerUuid) } returns player

        every { Bukkit.getPlayer("otherplayer") } returns otherPlayer
        every { server.getPlayer("otherplayer") } returns otherPlayer
        every { Bukkit.getPlayer(otherPlayerUuid) } returns otherPlayer
        every { server.getPlayer(otherPlayerUuid) } returns otherPlayer

        every { otherPlayer.hasPlayedBefore() } returns true
        coEvery { balanceManager.addToBankShards(any(), any()) } returns Result.success(Unit)
        coEvery { balanceManager.setPlayerShards(any(), any(), PostgreSQL.ShardType.INVENTORY) } returns
            Result.success(Unit)
        coEvery { balanceManager.subtractFromBankShards(any(), any()) } returns Result.success(Unit)
        coEvery { balanceManager.insertTransactionLog(any(), any(), any(), any(), any()) } returns Result.success(Unit)

        every { player.sendMessage(any<Component>()) } just Runs
        every { otherPlayer.sendMessage(any<Component>()) } just Runs
    }

    companion object {
        lateinit var server: Server

        @JvmStatic
        @BeforeAll
        fun setupBukkit() {
            server = BukkitMock.mockBukkit()
        }

        @JvmStatic
        fun payData() =
            listOf(
                Arguments.of(
                    "1 Diamond from inventory",
                    "1.0 <aqua>Diamond",
                    arrayOf(ItemStack(Material.DIAMOND, 1)),
                    0,
                    -1,
                    "1",
                    arrayOf<Long>(),
                    arrayOf<Long>(9),
                    0,
                    0,
                    0,
                ),
                Arguments.of(
                    "2 Diamonds from inventory",
                    "2.0 <aqua>Diamonds",
                    arrayOf(ItemStack(Material.DIAMOND, 3)),
                    0,
                    -1,
                    "2",
                    arrayOf<Long>(),
                    arrayOf<Long>(2 * 9),
                    0,
                    1,
                    0,
                ),
                Arguments.of(
                    "2.2 Diamonds from inventory only diamonds",
                    "2.2 <aqua>Diamonds",
                    arrayOf(ItemStack(Material.DIAMOND, 3)),
                    0,
                    -1,
                    "2.2",
                    arrayOf<Long>(),
                    arrayOf<Long>(20),
                    7,
                    0,
                    0,
                ),
                Arguments.of(
                    "2.2 Diamonds from inventory",
                    "2.2 <aqua>Diamonds",
                    arrayOf(Shard.createItemStack(2), ItemStack(Material.DIAMOND, 3)),
                    0,
                    -1,
                    "2.2",
                    arrayOf<Long>(),
                    arrayOf<Long>(20),
                    0,
                    1,
                    0,
                ),
                Arguments.of(
                    "2.2 Diamonds from inventory with blocks",
                    "2.2 <aqua>Diamonds",
                    arrayOf(ItemStack(Material.DIAMOND_BLOCK, 1)),
                    0,
                    -1,
                    "2.2",
                    arrayOf<Long>(),
                    arrayOf<Long>(20),
                    7,
                    6,
                    0,
                ),
                Arguments.of(
                    "2.9 Diamonds from inventory",
                    "3.0 <aqua>Diamonds",
                    arrayOf(ItemStack(Material.DIAMOND, 4)),
                    0,
                    -1,
                    "2.9",
                    arrayOf<Long>(),
                    arrayOf<Long>(27),
                    0,
                    1,
                    0,
                ),
                Arguments.of(
                    "1.8 Diamonds from inventory with overflow",
                    "1.8 <aqua>Diamonds",
                    Array(35) { ItemStack(Material.DIRT, 1) } + arrayOf(ItemStack(Material.DIAMOND, 3)),
                    0,
                    -1,
                    "1.8",
                    arrayOf<Long>(1),
                    arrayOf<Long>(17),
                    0,
                    1,
                    0,
                ),
                Arguments.of(
                    "1.1 Diamonds from inventory with diamond block with overflow",
                    "1.1 <aqua>Diamonds",
                    Array(35) { ItemStack(Material.DIRT, 1) } + arrayOf(ItemStack(Material.DIAMOND_BLOCK, 2)),
                    0,
                    -1,
                    "1.1",
                    arrayOf<Long>(63, 8),
                    arrayOf<Long>(10),
                    0,
                    0,
                    1,
                ),
                Arguments.of(
                    "1 Diamond: 0.5 Diamonds from bank and 1 Diamond in inventory",
                    "1.0 <aqua>Diamond",
                    arrayOf(ItemStack(Material.DIAMOND, 1)),
                    5,
                    5,
                    "1",
                    arrayOf<Long>(),
                    arrayOf<Long>(9),
                    5,
                    0,
                    0,
                ),
                Arguments.of(
                    "5 Diamonds from bank",
                    "5.0 <aqua>Diamonds",
                    arrayOf<ItemStack>(),
                    45,
                    45,
                    "5",
                    arrayOf<Long>(),
                    arrayOf<Long>(45),
                    0,
                    0,
                    0,
                ),
            )
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Successful Pay")
    @MethodSource("payData")
    fun successfulPay(
        @Suppress("UNUSED_PARAMETER") name: String,
        successDiamondText: String,
        inventoryContents: Array<ItemStack?>,
        bankShards: Long,
        bankShardsRemoved: Long,
        commandArg: String,
        addedShardsPlayer: Array<Long>,
        addedShardsOtherPlayer: Array<Long>,
        invShardCount: Long,
        invDiamondCount: Long,
        invDiamondBlockCount: Long,
    ) = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, inventoryContents)

        coEvery { balanceManager.getBankShards(playerUuid) } returns Result.success(bankShards)

        val user = mockk<User>()
        every { user.nodes } returns mutableListOf<Node>()
        every { luckPerms.userManager.getUser(any<UUID>()) } returns user

        val pay = Pay(config, balanceManager, mm, scope, luckPerms, transactionLock)
        pay.onCommand(player, command, "pay", arrayOf("otherplayer", commandArg))
        waitForCoroutines(scope)

        assertAll(
            buildList {
                add {
                    verify {
                        player.sendMessage(
                            Component.text(
                                "DiamondBank-OG<reset>: <green>Successfully paid <yellow>$successDiamondText <green>to  OtherPlayer<reset><green>."
                            )
                        )
                    }
                }
                add {
                    verify {
                        otherPlayer.sendMessage(
                            Component.text(
                                "DiamondBank-OG<reset>: <green> Player<reset> <green>has paid you <yellow>$successDiamondText<green>."
                            )
                        )
                    }
                }
                add {
                    if (bankShardsRemoved != -1L)
                        coVerify { balanceManager.subtractFromBankShards(playerUuid, bankShardsRemoved) }
                }
                addedShardsPlayer.forEach { add { coVerify { balanceManager.addToBankShards(playerUuid, it) } } }
                addedShardsOtherPlayer.forEach {
                    add { coVerify { balanceManager.addToBankShards(otherPlayerUuid, it) } }
                }
                add { assertEquals(invShardCount, inventory.countShards(), "Shard count") }
                add { assertEquals(invDiamondCount, inventory.countDiamonds(), "Diamond count") }
                add { assertEquals(invDiamondBlockCount, inventory.countDiamondBlocks(), "Diamond block count") }
                add { assertFalse(inventory.isLocked(), "Inventory locked") }
                add { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") }
            }
        )
    }

    @Test
    @DisplayName("Pay while in minigame should fail")
    fun payWhileInMinigame() = runTest {
        every { player.world.name } returns "minigame_world"
        val inventory = mockPlayerInventory(player, playerUuid, emptyArray())

        val pay = Pay(config, balanceManager, mm, scope, luckPerms, transactionLock)
        pay.onCommand(player, command, "pay", arrayOf("otherplayer", "1"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text("DiamondBank-OG<reset>: <red>You cannot use /pay when in a minigame.")
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
    @DisplayName("Pay while locked should fail")
    fun payWhileLocked() = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, emptyArray())

        coEvery { transactionLock.tryWithLockSuspend<Unit>(any(), any()) } returns TransactionLock.LockResult.Failed

        val pay = Pay(config, balanceManager, mm, scope, luckPerms, transactionLock)
        pay.onCommand(player, command, "pay", arrayOf("otherplayer", "1"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text("DiamondBank-OG<reset>: <red>You are currently blocked from using /pay.")
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
    @DisplayName("Pay no arguments should fail")
    fun payNoArgs() = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, emptyArray())

        val pay = Pay(config, balanceManager, mm, scope, luckPerms, transactionLock)
        pay.onCommand(player, command, "pay", arrayOf())
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text(
                            "DiamondBank-OG<reset>: <red>You did not provide the name or the UUID of a player and the amount of <aqua>Diamonds<red>."
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
    @DisplayName("Pay too few arguments should fail")
    fun payTooFewArgs() = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, emptyArray())

        val pay = Pay(config, balanceManager, mm, scope, luckPerms, transactionLock)
        pay.onCommand(player, command, "pay", arrayOf("1"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text(
                            "DiamondBank-OG<reset>: <red>Please (only) provide the name or the UUID of a player and the amount of <aqua>Diamonds<red>."
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
    @DisplayName("Pay too many arguments should fail")
    fun payTooManyArgs() = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, emptyArray())

        val pay = Pay(config, balanceManager, mm, scope, luckPerms, transactionLock)
        pay.onCommand(player, command, "pay", arrayOf("1", "2", "3"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text(
                            "DiamondBank-OG<reset>: <red>Please (only) provide the name or the UUID of a player and the amount of <aqua>Diamonds<red>."
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
    @DisplayName("Pay with invalid argument should fail")
    @CsvSource(
        "Not a number, abc, Invalid argument",
        "Negative, -1, You cannot pay a negative or zero amount",
        "Zero, 0, You cannot pay a negative or zero amount",
        "More than one decimal digit, 1.11, <aqua>Diamonds<red> can only have one decimal digit. Issue /diamondbankhelp for more information",
    )
    fun payInvalidArg(@Suppress("UNUSED_PARAMETER") name: String, argument: String, errorMessage: String) = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, emptyArray())

        val pay = Pay(config, balanceManager, mm, scope, luckPerms, transactionLock)
        pay.onCommand(player, command, "pay", arrayOf("otherplayer", argument))
        waitForCoroutines(scope)

        assertAll(
            { verify { player.sendMessage(Component.text("DiamondBank-OG<reset>: <red>$errorMessage.")) } },
            { coVerify(exactly = 0) { balanceManager.subtractFromBankShards(any(), any()) } },
            { assertEquals(0, inventory.countTotal(), "Total count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Pay with no permission should fail")
    fun payNoPermission() = runTest {
        every { player.hasPermission("diamondbank-og.pay") } returns false
        val inventory = mockPlayerInventory(player, playerUuid, emptyArray())

        val pay = Pay(config, balanceManager, mm, scope, luckPerms, transactionLock)
        pay.onCommand(player, command, "pay", arrayOf("1"))
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
}
