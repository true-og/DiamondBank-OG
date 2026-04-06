package net.trueog.diamondbankog

import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.just
import io.mockk.slot
import io.mockk.verify
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
import net.trueog.diamondbankog.transaction.command.Compress
import net.trueog.diamondbankog.config.Config
import net.trueog.diamondbankog.transaction.InventoryLockExtensions.isLocked
import net.trueog.diamondbankog.transaction.TransactionLock
import net.trueog.diamondbankog.util.InventoryExtensions.countDiamondBlocks
import net.trueog.diamondbankog.util.InventoryExtensions.countDiamonds
import net.trueog.diamondbankog.util.InventoryExtensions.countShards
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Server
import org.bukkit.block.ShulkerBox
import org.bukkit.command.Command
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class CompressTest {
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
        every { player.hasPermission("diamondbank-og.compress") } returns true

        every { Bukkit.getPlayer("player") } returns player
        every { server.getPlayer("player") } returns player
        every { Bukkit.getPlayer(playerUuid) } returns player
        every { server.getPlayer(playerUuid) } returns player

        every { player.sendMessage(any<Component>()) } just Runs

        coEvery { balanceManager.setPlayerShards(any(), any(), ShardType.INVENTORY) } returns Result.success(Unit)
    }

    companion object {
        lateinit var server: Server

        @JvmStatic
        @BeforeAll
        fun setupBukkit() {
            server = BukkitMock.mockBukkit()
        }

        @JvmStatic
        fun compressData() =
            listOf(
                Arguments.of("Nothing", arrayOf<ItemStack>(), "<#FFA500>Nothing found to compress.", 0, 0, 0),
                Arguments.of(
                    "9 shards to 1 diamond",
                    arrayOf(Shard.createItemStack(9)),
                    "Compression Summary:\n<red>-9 Diamond Shards\n<green>+1 Diamond",
                    0,
                    1,
                    0,
                ),
                Arguments.of(
                    "9 diamonds to 1 diamond block",
                    arrayOf(ItemStack(Material.DIAMOND, 9)),
                    "Compression Summary:\n<red>-9 Diamonds\n<green>+1 Diamond Block",
                    0,
                    0,
                    1,
                ),
                Arguments.of(
                    "9 diamonds and 9 shards to 1 diamond block and 1 diamond",
                    arrayOf(Shard.createItemStack(9), ItemStack(Material.DIAMOND, 9)),
                    "Compression Summary:\n<red>-9 Diamond Shards\n<red>-8 Diamonds\n<green>+1 Diamond Block",
                    0,
                    1,
                    1,
                ),
            )

        @JvmStatic
        fun overflowCompressData() =
            listOf(
                Arguments.of(
                    "10 shards",
                    Array(35) { ItemStack(Material.DIRT, 1) } + arrayOf(Shard.createItemStack(10)),
                    "You do not have enough space in your inventory to compress all the Diamond currency items (<green>+1 <aqua>Diamonds<red>).",
                    10,
                    0,
                    0,
                ),
                Arguments.of(
                    "10 diamonds",
                    Array(35) { ItemStack(Material.DIRT, 1) } + arrayOf(ItemStack(Material.DIAMOND, 10)),
                    "You do not have enough space in your inventory to compress all the Diamond currency items (<green>+1 <aqua>Diamond Blocks<red>).",
                    0,
                    10,
                    0,
                ),
            )
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Successful Compress")
    @MethodSource("compressData")
    fun successfulCompress(
        @Suppress("UNUSED_PARAMETER") name: String,
        inventoryContents: Array<ItemStack?>,
        compressionSummary: String,
        invShardCount: Long,
        invDiamondCount: Long,
        invDiamondBlockCount: Long,
    ) = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, inventoryContents)

        val compress = Compress(config, balanceManager, mm, scope, transactionLock)
        compress.onCommand(player, command, "compress", arrayOf())
        waitForCoroutines(scope)

        assertAll(
            { verify { player.sendMessage(Component.text("DiamondBank-OG<reset>: $compressionSummary")) } },
            { assertEquals(invShardCount, inventory.countShards(), "Shard count") },
            { assertEquals(invDiamondCount, inventory.countDiamonds(), "Diamond count") },
            { assertEquals(invDiamondBlockCount, inventory.countDiamondBlocks(), "Diamond block count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Shulker box Successful Compress")
    @MethodSource("compressData")
    fun successfulShulkerBoxCompress(
        @Suppress("UNUSED_PARAMETER") name: String,
        inventoryContents: Array<ItemStack>,
        compressionSummary: String,
        invShardCount: Long,
        invDiamondCount: Long,
        invDiamondBlockCount: Long,
    ) = runTest {
        val shulkerBox = ItemStack(Material.SHULKER_BOX)
        val blockStateMeta = shulkerBox.itemMeta as BlockStateMeta
        val shulkerBoxState = blockStateMeta.blockState as ShulkerBox
        inventoryContents.forEach { shulkerBoxState.inventory.addItem(it) }
        blockStateMeta.blockState = shulkerBoxState
        shulkerBox.itemMeta = blockStateMeta

        val inventory = mockPlayerInventory(player, playerUuid, arrayOf(shulkerBox))

        val compress = Compress(config, balanceManager, mm, scope, transactionLock)
        compress.onCommand(player, command, "compress", arrayOf("yes"))
        waitForCoroutines(scope)

        assertAll(
            { verify { player.sendMessage(Component.text("DiamondBank-OG<reset>: $compressionSummary")) } },
            { assertEquals(invShardCount, shulkerBoxState.inventory.countShards(), "Shard count") },
            { assertEquals(invDiamondCount, shulkerBoxState.inventory.countDiamonds(), "Diamond count") },
            {
                assertEquals(
                    invDiamondBlockCount,
                    shulkerBoxState.inventory.countDiamondBlocks(),
                    "Diamond block count",
                )
            },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Overflow Compress")
    @MethodSource("overflowCompressData")
    fun overflowCompress(
        @Suppress("UNUSED_PARAMETER") name: String,
        inventoryContents: Array<ItemStack?>,
        error: String,
        invShardCount: Long,
        invDiamondCount: Long,
        invDiamondBlockCount: Long,
    ) = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, inventoryContents)

        val compress = Compress(config, balanceManager, mm, scope, transactionLock)
        compress.onCommand(player, command, "compress", arrayOf())
        waitForCoroutines(scope)

        assertAll(
            { verify { player.sendMessage(Component.text("DiamondBank-OG<reset>: <red>$error")) } },
            { assertEquals(invShardCount, inventory.countShards(), "Shard count") },
            { assertEquals(invDiamondCount, inventory.countDiamonds(), "Diamond count") },
            { assertEquals(invDiamondBlockCount, inventory.countDiamondBlocks(), "Diamond block count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Compress while in minigame should fail")
    fun compressWhileInMinigame() = runTest {
        every { player.world.name } returns "minigame_world"
        val inventory = mockPlayerInventory(player, playerUuid, arrayOf(ItemStack(Material.DIAMOND, 9)))

        val compress = Compress(config, balanceManager, mm, scope, transactionLock)
        compress.onCommand(player, command, "compress", arrayOf("all"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text("DiamondBank-OG<reset>: <red>You cannot use /compress when in a minigame.")
                    )
                }
            },
            { assertEquals(9, inventory.countDiamonds(), "Diamond count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Compress while locked should fail")
    fun compressWhileLocked() = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, arrayOf(ItemStack(Material.DIAMOND, 9)))

        coEvery { transactionLock.tryWithLockSuspend<Unit>(any(), any()) } returns TransactionLock.LockResult.Failed

        val compress = Compress(config, balanceManager, mm, scope, transactionLock)
        compress.onCommand(player, command, "compress", arrayOf("all"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text("DiamondBank-OG<reset>: <red>You are currently blocked from using /compress.")
                    )
                }
            },
            { assertEquals(9, inventory.countDiamonds(), "Diamond count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Compress too many arguments should fail")
    fun compressTooManyArgs() = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, arrayOf(ItemStack(Material.DIAMOND, 9)))

        val compress = Compress(config, balanceManager, mm, scope, transactionLock)
        compress.onCommand(player, command, "compress", arrayOf("1", "2"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text(
                            "DiamondBank-OG<reset>: <red>Do not provide more arguments than \"yes\" if you want to compress the items in the shulker box you're holding."
                        )
                    )
                }
            },
            { assertEquals(9, inventory.countDiamonds(), "Diamond count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Compress with invalid argument should fail")
    fun compressInvalidArg() = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, arrayOf(ItemStack(Material.DIAMOND, 9)))

        val compress = Compress(config, balanceManager, mm, scope, transactionLock)
        compress.onCommand(player, command, "compress", arrayOf("invalid"))
        waitForCoroutines(scope)

        assertAll(
            { verify { player.sendMessage(Component.text("DiamondBank-OG<reset>: <red>Invalid argument.")) } },
            { assertEquals(9, inventory.countDiamonds(), "Diamond count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Compress with no permission should fail")
    fun compressNoPermission() = runTest {
        every { player.hasPermission("diamondbank-og.compress") } returns false
        val inventory = mockPlayerInventory(player, playerUuid, arrayOf(ItemStack(Material.DIAMOND, 9)))

        val compress = Compress(config, balanceManager, mm, scope, transactionLock)
        compress.onCommand(player, command, "compress", arrayOf("1"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text("DiamondBank-OG<reset>: <red>You do not have permission to use this command.")
                    )
                }
            },
            { assertEquals(9, inventory.countDiamonds(), "Diamond count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Compress with yes while not holding a shulker box should fail")
    fun compressYesNotHoldingShulkerBox() = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, arrayOf(ItemStack(Material.DIAMOND, 9)))

        val compress = Compress(config, balanceManager, mm, scope, transactionLock)
        compress.onCommand(player, command, "compress", arrayOf("yes"))
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(Component.text("DiamondBank-OG<reset>: <red>You are not holding a shulker box."))
                }
            },
            { assertEquals(9, inventory.countDiamonds(), "Diamond count") },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }

    @Test
    @DisplayName("Compress without yes while holding a shulker box should fail")
    fun compressNoYesHoldingShulkerBox() = runTest {
        val inventory = mockPlayerInventory(player, playerUuid, arrayOf(ItemStack(Material.SHULKER_BOX)))

        val compress = Compress(config, balanceManager, mm, scope, transactionLock)
        compress.onCommand(player, command, "compress", arrayOf())
        waitForCoroutines(scope)

        assertAll(
            {
                verify {
                    player.sendMessage(
                        Component.text(
                            "DiamondBank-OG<reset>: <#FFA500>Are you sure you want to compress the Diamond currency items in the shulker box you're holding? If so, run \"/compress yes\""
                        )
                    )
                }
            },
            { assertFalse(inventory.isLocked(), "Inventory locked") },
            { assertFalse(transactionLock.isLocked(playerUuid), "Transaction lock") },
        )
    }
}
