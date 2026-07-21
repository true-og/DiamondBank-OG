package net.trueog.diamondbankog

import io.mockk.*
import io.mockk.impl.annotations.MockK
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import net.trueog.diamondbankog.Constants.playerUuid
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.balance.CachingBalanceManager
import net.trueog.diamondbankog.balance.shard.PlayerShards
import net.trueog.diamondbankog.balance.shard.ShardType
import net.trueog.diamondbankog.persistence.PostgreSQL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CachingBalanceManagerTest {
    @MockK private lateinit var postgreSQL: PostgreSQL

    private lateinit var manager: CachingBalanceManager

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        // Constructor is private, only reachable through the factory (which opens a real DB
        // connection). Reflection bypasses that so we can inject a mocked PostgreSQL instead.
        val constructor = CachingBalanceManager::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        manager = constructor.newInstance()
        manager.postgreSQL = postgreSQL
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
            { assertEquals(10, manager.cache.getBalance(playerUuid, shardType).getOrNull()) },
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
            { assertEquals(-1, manager.cache.getBalance(playerUuid, ShardType.BANK).getOrNull()) },
        )
    }

    @Test
    @DisplayName("Add To Bank Shards")
    fun addToBankShards() = runTest {
        coEvery { postgreSQL.addToPlayerShards(playerUuid, 5, ShardType.BANK) } returns Result.success(5)

        val result = manager.addToBankShards(playerUuid, 5)

        assertAll(
            { assertFalse(result.isFailure, "addToBankShards result should not be a failure") },
            { assertEquals(5, manager.cache.getBalance(playerUuid, ShardType.BANK).getOrNull()) },
            { coVerify { postgreSQL.addToPlayerShards(playerUuid, 5, ShardType.BANK) } },
        )
    }

    @Test
    @DisplayName("Subtract From Bank Shards")
    fun subtractFromBankShards() = runTest {
        manager.cache.setBalance(playerUuid, 10, ShardType.BANK)
        coEvery { postgreSQL.addToPlayerShards(playerUuid, -4, ShardType.BANK) } returns Result.success(6)

        val result = manager.subtractFromBankShards(playerUuid, 4)

        assertAll(
            { assertFalse(result.isFailure, "subtractFromBankShards result should not be a failure") },
            { assertEquals(6, manager.cache.getBalance(playerUuid, ShardType.BANK).getOrNull()) },
            { coVerify { postgreSQL.addToPlayerShards(playerUuid, -4, ShardType.BANK) } },
        )
    }

    @Test
    @DisplayName("Add To Bank Shards DB failure propagates")
    fun addToBankShardsDbFail() = runTest {
        coEvery { postgreSQL.addToPlayerShards(playerUuid, 5, ShardType.BANK) } returns
            Result.failure(RuntimeException("db error"))

        val result = manager.addToBankShards(playerUuid, 5)

        assertTrue(result.isFailure, "addToBankShards result should be a failure")
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
    @DisplayName("Get Bank Shards while being modified bypasses cache")
    fun getBankShardsWhileBeingModified() = runTest {
        manager.cache.setBalance(playerUuid, 5, ShardType.BANK)
        manager.beingModified[playerUuid to ShardType.BANK] = AtomicInteger(1)
        coEvery { postgreSQL.getShardTypeShards(playerUuid, ShardType.BANK) } returns Result.success(99)

        val result = manager.getBankShards(playerUuid)

        assertAll(
            { assertEquals(99, result.getOrNull()) },
            { coVerify { postgreSQL.getShardTypeShards(playerUuid, ShardType.BANK) } },
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
    @DisplayName("Get Total Shards while being modified bypasses cache")
    fun getTotalShardsWhileBeingModified() = runTest {
        manager.cache.setBalance(playerUuid, 3, ShardType.TOTAL)
        manager.beingModified[playerUuid to ShardType.BANK] = AtomicInteger(1)
        coEvery { postgreSQL.getAllShards(playerUuid) } returns Result.success(PlayerShards(20, 20, 10))

        val result = manager.getTotalShards(playerUuid)

        assertAll({ assertEquals(50, result.getOrNull()) }, { coVerify { postgreSQL.getAllShards(playerUuid) } })
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
        manager.cache.setBalance(playerUuid, 1, ShardType.BANK)
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
            { assertEquals(1, manager.cache.getBalance(playerUuid, ShardType.BANK).getOrNull()) },
            { assertEquals(2, manager.cache.getBalance(playerUuid, ShardType.INVENTORY).getOrNull()) },
            { assertEquals(3, manager.cache.getBalance(playerUuid, ShardType.ENDER_CHEST).getOrNull()) },
        )
    }

    @Test
    @DisplayName("Remove Cache For Player")
    fun removeCacheForPlayer() {
        manager.cache.setBalance(playerUuid, 1, ShardType.BANK)
        manager.cache.setBalance(playerUuid, 2, ShardType.INVENTORY)
        manager.cache.setBalance(playerUuid, 3, ShardType.ENDER_CHEST)

        manager.removeCacheForPlayer(playerUuid)

        assertAll(
            { assertEquals(-1, manager.cache.getBalance(playerUuid, ShardType.BANK).getOrNull()) },
            { assertEquals(-1, manager.cache.getBalance(playerUuid, ShardType.INVENTORY).getOrNull()) },
            { assertEquals(-1, manager.cache.getBalance(playerUuid, ShardType.ENDER_CHEST).getOrNull()) },
        )
    }
}
