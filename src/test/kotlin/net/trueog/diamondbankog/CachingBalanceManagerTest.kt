package net.trueog.diamondbankog

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import kotlinx.coroutines.test.runTest
import net.trueog.diamondbankog.Constants.otherPlayerUuid
import net.trueog.diamondbankog.Constants.playerUuid
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.balance.Cache
import net.trueog.diamondbankog.balance.CachingBalanceManager
import net.trueog.diamondbankog.balance.shard.PlayerShards
import net.trueog.diamondbankog.balance.shard.ShardType
import net.trueog.diamondbankog.persistence.PostgreSQL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CachingBalanceManagerTest {
    @MockK private lateinit var postgreSQL: PostgreSQL
    @SpyK private var cache = Cache()

    private lateinit var manager: CachingBalanceManager

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        // Constructor is private, only reachable through the factory (which opens a real DB
        // connection). Reflection bypasses that so we can inject a mocked PostgreSQL instead.
        val constructor = CachingBalanceManager::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        manager = constructor.newInstance()
        val postgreSQL = manager::class.java.getDeclaredField("postgreSQL")
        postgreSQL.isAccessible = true
        postgreSQL.set(manager, this.postgreSQL)
        val cache = manager::class.java.getDeclaredField("cache")
        cache.isAccessible = true
        cache.set(manager, this.cache)
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Set Player Shards")
    @CsvSource("Bank Shards, BANK", "Inventory Shards, INVENTORY", "Ender Chest Shards, ENDER_CHEST")
    fun setPlayerShards(@Suppress("UNUSED_PARAMETER") name: String, enumName: String) = runTest {
        val shardType = ShardType.valueOf(enumName)
        coEvery { postgreSQL.setPlayerShards(playerUuid, 10, shardType) } returns Result.success(Unit)

        val result = manager.setPlayerShards(playerUuid, 10, shardType)

        assertAll(
            { assertFalse(result.isFailure, "setPlayerShards result should not be a failure") },
            { assertEquals(10, cache.getBalance(playerUuid, shardType).getOrNull()) },
            { coVerify { postgreSQL.setPlayerShards(playerUuid, 10, shardType) } },
        )
    }

    @Test
    @DisplayName("Set Player Shards TOTAL should fail")
    fun setPlayerShardsTotalFail() = runTest {
        val result = manager.setPlayerShards(playerUuid, 10, ShardType.TOTAL)
        assertTrue(result.isFailure, "setPlayerShards result should be a failure with ShardType.TOTAL")
    }

    @Test
    @DisplayName("Set Player Shards fails when economy disabled")
    fun setPlayerShardsEconomyDisabled() = runTest {
        economyDisabled = true
        try {
            val result = manager.setPlayerShards(playerUuid, 10, ShardType.BANK)
            assertTrue(result.isFailure, "setPlayerShards result should be a failure when economy disabled")
        } finally {
            economyDisabled = false
        }
    }

    @Test
    @DisplayName("Set Player Shards DB failure propagates, cache untouched")
    fun setPlayerShardsDbFail() = runTest {
        coEvery { postgreSQL.setPlayerShards(playerUuid, 10, ShardType.BANK) } returns
            Result.failure(RuntimeException("db error"))

        val result = manager.setPlayerShards(playerUuid, 10, ShardType.BANK)

        assertAll(
            { assertTrue(result.isFailure, "setPlayerShards result should be a failure") },
            { assertEquals(-1, cache.getBalance(playerUuid, ShardType.BANK).getOrNull()) },
        )
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Add To Bank Shards")
    @CsvSource("No existing balance, 5, 0", "Existing balance, 10, 5")
    fun addToBankShards(@Suppress("UNUSED_PARAMETER") name: String, toRemove: Long, existingBalance: Long) = runTest {
        coEvery { postgreSQL.getShardTypeShards(playerUuid, ShardType.BANK) } returns Result.success(existingBalance)
        coEvery { postgreSQL.addToPlayerShards(playerUuid, toRemove, ShardType.BANK) } returns
            Result.success(toRemove - existingBalance)

        val result = manager.addToBankShards(playerUuid, toRemove)

        assertAll(
            { assertFalse(result.isFailure, "addToBankShards result should not be a failure") },
            { assertEquals(toRemove - existingBalance, cache.getBalance(playerUuid, ShardType.BANK).getOrNull()) },
            { coVerify { postgreSQL.addToPlayerShards(playerUuid, toRemove, ShardType.BANK) } },
        )
    }

    @Test
    @DisplayName("Subtract From Bank Shards")
    fun subtractFromBankShards() = runTest {
        coEvery { postgreSQL.getShardTypeShards(playerUuid, ShardType.BANK) } returns Result.success(10)
        coEvery { postgreSQL.addToPlayerShards(playerUuid, -4, ShardType.BANK) } returns Result.success(6)

        val result = manager.subtractFromBankShards(playerUuid, 4)

        assertAll(
            { assertFalse(result.isFailure, "subtractFromBankShards result should not be a failure") },
            { assertEquals(6, cache.getBalance(playerUuid, ShardType.BANK).getOrNull()) },
            { coVerify { postgreSQL.addToPlayerShards(playerUuid, -4, ShardType.BANK) } },
        )
    }

    @Test
    @DisplayName("Subtract From Bank Shards (insufficient balance)")
    fun subtractFromBankShardsInsufficientBalance() = runTest {
        cache.setBalance(playerUuid, 2, ShardType.BANK)
        coEvery { postgreSQL.getShardTypeShards(playerUuid, ShardType.BANK) } returns Result.success(2)
        coEvery { postgreSQL.addToPlayerShards(playerUuid, -4, ShardType.BANK) } returns Result.failure(Exception())

        val result = manager.subtractFromBankShards(playerUuid, 4)

        assertAll(
            {
                assertTrue(
                    result.exceptionOrNull() is DiamondBankException.InsufficientBalanceException,
                    "subtractFromBankShards result should be an InsufficientBalanceException",
                )
            },
            { assertEquals(2, cache.getBalance(playerUuid, ShardType.BANK).getOrNull()) },
            { coVerify(exactly = 0) { postgreSQL.addToPlayerShards(playerUuid, -4, ShardType.BANK) } },
        )
    }

    @Test
    @DisplayName("Add To Bank Shards DB failure propagates")
    fun addToBankShardsDbFail() = runTest {
        coEvery { postgreSQL.getShardTypeShards(playerUuid, ShardType.BANK) } returns Result.success(5)
        coEvery { postgreSQL.addToPlayerShards(playerUuid, 5, ShardType.BANK) } returns
            Result.failure(RuntimeException("db error"))

        val result = manager.addToBankShards(playerUuid, 5)

        assertTrue(result.isFailure, "addToBankShards result should be a failure")
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Transfer Bank Shards")
    @CsvSource("No existing balance, 5, 0", "Existing balance, 5, 5")
    fun transferBankShards(
        @Suppress("UNUSED_PARAMETER") name: String,
        toTransfer: Long,
        receiverExistingBalance: Long,
    ) = runTest {
        val senderExistingBalance = 10L
        val newSenderBalance = senderExistingBalance - toTransfer
        val newReceiverBalance = receiverExistingBalance + toTransfer

        coEvery { postgreSQL.getShardTypeShards(playerUuid, ShardType.BANK) } returns
            Result.success(senderExistingBalance)
        coEvery { postgreSQL.getShardTypeShards(otherPlayerUuid, ShardType.BANK) } returns
            Result.success(receiverExistingBalance)
        coEvery { postgreSQL.transferBankShards(playerUuid, otherPlayerUuid, toTransfer, toTransfer) } returns
            Result.success(mapOf(playerUuid to newSenderBalance, otherPlayerUuid to newReceiverBalance))

        val result = manager.transferBankShards(playerUuid, otherPlayerUuid, toTransfer, toTransfer)

        assertAll(
            { assertFalse(result.isFailure, "transferBankShards result should not be a failure") },
            { assertEquals(newSenderBalance, cache.getBalance(playerUuid, ShardType.BANK).getOrNull()) },
            { assertEquals(newReceiverBalance, cache.getBalance(otherPlayerUuid, ShardType.BANK).getOrNull()) },
            { coVerify { postgreSQL.transferBankShards(playerUuid, otherPlayerUuid, toTransfer, toTransfer) } },
        )
    }

    @Test
    @DisplayName("Transfer Bank Shards (insufficient balance)")
    fun transferBankShardsInsufficientBalance() = runTest {
        cache.setBalance(playerUuid, 2, ShardType.BANK)
        cache.setBalance(otherPlayerUuid, 0, ShardType.BANK)
        coEvery { postgreSQL.getShardTypeShards(playerUuid, ShardType.BANK) } returns Result.success(2)
        coEvery { postgreSQL.transferBankShards(playerUuid, otherPlayerUuid, 4, 4) } returns Result.failure(Exception())

        val result = manager.transferBankShards(playerUuid, otherPlayerUuid, 4, 4)

        assertAll(
            {
                assertTrue(
                    result.exceptionOrNull() is DiamondBankException.InsufficientBalanceException,
                    "transferBankShards result should be an InsufficientBalanceException",
                )
            },
            { assertEquals(2, cache.getBalance(playerUuid, ShardType.BANK).getOrNull()) },
            { assertEquals(0, cache.getBalance(otherPlayerUuid, ShardType.BANK).getOrNull()) },
            { coVerify(exactly = 0) { postgreSQL.transferBankShards(playerUuid, otherPlayerUuid, 4, 4) } },
        )
    }

    @Test
    @DisplayName("Transfer Bank Shards fails for negative shardsToSubtractFromSender")
    fun transferBankShardsNegativeSubtract() = runTest {
        assertThrows<IllegalArgumentException> { manager.transferBankShards(playerUuid, playerUuid, -1, 5) }
    }

    @Test
    @DisplayName("Transfer Bank Shards fails for negative shardsToAddToReceiver")
    fun transferBankShardsNegativeAdd() = runTest {
        assertThrows<IllegalArgumentException> { manager.transferBankShards(playerUuid, playerUuid, 5, -1) }
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Get Shard Type Shards (cache miss then hit)")
    @CsvSource("Bank Shards, BANK", "Inventory Shards, INVENTORY", "Ender Chest Shards, ENDER_CHEST")
    fun getShardTypeShardsCacheMissThenHit(@Suppress("UNUSED_PARAMETER") name: String, enumName: String) = runTest {
        val shardType = ShardType.valueOf(enumName)
        coEvery { postgreSQL.getShardTypeShards(playerUuid, shardType) } returns Result.success(7)

        val first = manager.getShardTypeShards(playerUuid, shardType)
        val second = manager.getShardTypeShards(playerUuid, shardType)

        assertAll(
            { assertEquals(7, first.getOrNull()) },
            { assertEquals(7, second.getOrNull()) },
            { coVerify(exactly = 1) { postgreSQL.getShardTypeShards(playerUuid, shardType) } },
        )
    }

    @Test
    @DisplayName("Get Total Shards (cache miss then hit)")
    fun getTotalShardsCacheMissThenHit() = runTest {
        coEvery { postgreSQL.getAllShards(playerUuid) } returns Result.success(PlayerShards(5, 6, 4))

        val first = manager.getTotalShards(playerUuid)
        val second = manager.getTotalShards(playerUuid)

        assertAll(
            { assertEquals(15, first.getOrNull()) },
            { assertEquals(15, second.getOrNull()) },
            { coVerify(exactly = 1) { postgreSQL.getAllShards(playerUuid) } },
        )
    }

    @Test
    @DisplayName("Get All Shards (cache miss then hit)")
    fun getAllShardsCacheMissThenHit() = runTest {
        coEvery { postgreSQL.getAllShards(playerUuid) } returns Result.success(PlayerShards(1, 2, 3))

        val first = manager.getAllShards(playerUuid)
        val second = manager.getAllShards(playerUuid)

        assertAll(
            { assertEquals(PlayerShards(1, 2, 3), first.getOrNull()) },
            { assertEquals(PlayerShards(1, 2, 3), second.getOrNull()) },
            { coVerify(exactly = 1) { postgreSQL.getAllShards(playerUuid) } },
        )
    }

    @Test
    @DisplayName("Get All Shards partial cache miss still hits DB")
    fun getAllShardsPartialCacheMiss() = runTest {
        cache.setBalance(playerUuid, 1, ShardType.BANK)
        coEvery { postgreSQL.getAllShards(playerUuid) } returns Result.success(PlayerShards(1, 2, 3))

        val result = manager.getAllShards(playerUuid)

        assertAll(
            { assertEquals(PlayerShards(1, 2, 3), result.getOrNull()) },
            { coVerify(exactly = 1) { postgreSQL.getAllShards(playerUuid) } },
        )
    }

    @Test
    @DisplayName("Cache For Player")
    fun cacheForPlayer() = runTest {
        coEvery { postgreSQL.getAllShards(playerUuid) } returns Result.success(PlayerShards(1, 2, 3))

        val result = manager.cacheForPlayer(playerUuid)

        assertAll(
            { assertFalse(result.isFailure, "cacheForPlayer result should not be a failure") },
            { assertEquals(1, cache.getBalance(playerUuid, ShardType.BANK).getOrNull()) },
            { assertEquals(2, cache.getBalance(playerUuid, ShardType.INVENTORY).getOrNull()) },
            { assertEquals(3, cache.getBalance(playerUuid, ShardType.ENDER_CHEST).getOrNull()) },
        )
    }

    @Test
    @DisplayName("Remove Cache For Player")
    suspend fun removeCacheForPlayer() {
        cache.setBalance(playerUuid, 1, ShardType.BANK)
        cache.setBalance(playerUuid, 2, ShardType.INVENTORY)
        cache.setBalance(playerUuid, 3, ShardType.ENDER_CHEST)

        manager.removeCacheForPlayer(playerUuid)

        assertAll(
            { assertEquals(-1, cache.getBalance(playerUuid, ShardType.BANK).getOrNull()) },
            { assertEquals(-1, cache.getBalance(playerUuid, ShardType.INVENTORY).getOrNull()) },
            { assertEquals(-1, cache.getBalance(playerUuid, ShardType.ENDER_CHEST).getOrNull()) },
        )
    }
}
