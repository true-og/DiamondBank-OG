package net.trueog.diamondbankog

import java.util.*
import net.trueog.diamondbankog.balance.Cache
import net.trueog.diamondbankog.balance.shard.ShardType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CacheTest {
    @ParameterizedTest(name = "{0}")
    @DisplayName("Cache Set")
    @CsvSource("Bank Shards, BANK", "Inventory Shards, INVENTORY", "Ender Chest Shards, ENDER_CHEST")
    fun cacheSet(@Suppress("UNUSED_PARAMETER") name: String, enumName: String) {
        val shardType = ShardType.valueOf(enumName)
        val cache = Cache()
        val uuid = UUID.randomUUID()
        val result = cache.setBalance(uuid, 3, shardType)
        assertFalse(result.isFailure, "setBalance result should not be a failure")
        assertEquals(3, cache.getBalance(uuid, shardType))
    }

    @Test
    @DisplayName("Cache Set fail")
    fun cacheSetFail() {
        val cache = Cache()
        val uuid = UUID.randomUUID()
        val result = cache.setBalance(uuid, 3, ShardType.TOTAL)
        assertTrue(result.isFailure, "setBalance result should be a failure with ShardType.TOTAL")
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Cache Add (after set)")
    @CsvSource("Bank Shards, BANK", "Inventory Shards, INVENTORY", "Ender Chest Shards, ENDER_CHEST")
    fun cacheAddAfterSet(@Suppress("UNUSED_PARAMETER") name: String, enumName: String) {
        val shardType = ShardType.valueOf(enumName)
        val cache = Cache()
        val uuid = UUID.randomUUID()
        cache.setBalance(uuid, 3, shardType)
        val result = cache.addBalance(uuid, 3, shardType)
        assertFalse(result.isFailure, "addBalance result should not be a failure")
        assertEquals(6, result.getOrNull())
        assertEquals(6, cache.getBalance(uuid, shardType))
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Cache Add")
    @CsvSource("Bank Shards, BANK", "Inventory Shards, INVENTORY", "Ender Chest Shards, ENDER_CHEST")
    fun cacheAdd(@Suppress("UNUSED_PARAMETER") name: String, enumName: String) {
        val shardType = ShardType.valueOf(enumName)
        val cache = Cache()
        val uuid = UUID.randomUUID()
        val result = cache.addBalance(uuid, 3, shardType)
        assertFalse(result.isFailure, "addBalance result should not be a failure")
        assertEquals(3, result.getOrNull())
        assertEquals(3, cache.getBalance(uuid, shardType))
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Cache Add (twice)")
    @CsvSource("Bank Shards, BANK", "Inventory Shards, INVENTORY", "Ender Chest Shards, ENDER_CHEST")
    fun cacheAddTwice(@Suppress("UNUSED_PARAMETER") name: String, enumName: String) {
        val shardType = ShardType.valueOf(enumName)
        val cache = Cache()
        val uuid = UUID.randomUUID()
        val result1 = cache.addBalance(uuid, 3, shardType)
        assertFalse(result1.isFailure, "addBalance result should not be a failure")
        val result2 = cache.addBalance(uuid, 3, shardType)
        assertFalse(result2.isFailure, "addBalance result should not be a failure")
        assertEquals(6, result2.getOrNull())
        assertEquals(6, cache.getBalance(uuid, shardType))
    }

    @Test
    @DisplayName("Cache Add fail")
    fun cacheAddFail() {
        val cache = Cache()
        val uuid = UUID.randomUUID()
        val result = cache.addBalance(uuid, 3, ShardType.TOTAL)
        assertTrue(result.isFailure, "addBalance result should be a failure with ShardType.TOTAL")
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Cache Get (after set)")
    @CsvSource(
        "Bank Shards, BANK",
        "Inventory Shards, INVENTORY",
        "Ender Chest Shards, ENDER_CHEST",
        "Total Shards, TOTAL",
    )
    fun cacheGetAfterSet(@Suppress("UNUSED_PARAMETER") name: String, enumName: String) {
        val shardType = ShardType.valueOf(enumName)
        val cache = Cache()
        val uuid = UUID.randomUUID()
        cache.setBalance(uuid, 3, if (shardType != ShardType.TOTAL) shardType else ShardType.BANK)
        val result = cache.getBalance(uuid, shardType)
        assertEquals(3, result)
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Cache Get")
    @CsvSource(
        "Bank Shards, BANK",
        "Inventory Shards, INVENTORY",
        "Ender Chest Shards, ENDER_CHEST",
        "Total Shards, TOTAL",
    )
    fun cacheGet(@Suppress("UNUSED_PARAMETER") name: String, enumName: String) {
        val shardType = ShardType.valueOf(enumName)
        val cache = Cache()
        val uuid = UUID.randomUUID()
        val result = cache.getBalance(uuid, shardType)
        assertEquals(-1, result)
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Cache Remove")
    @CsvSource("Bank Shards, BANK", "Inventory Shards, INVENTORY", "Ender Chest Shards, ENDER_CHEST")
    fun cacheRemove(@Suppress("UNUSED_PARAMETER") name: String, enumName: String) {
        val shardType = ShardType.valueOf(enumName)
        val cache = Cache()
        val uuid = UUID.randomUUID()
        cache.addBalance(uuid, 3, shardType)
        val result = cache.removeBalance(uuid, shardType)
        assertFalse(result.isFailure, "removeBalance result should not be a failure")
        assertEquals(-1, cache.getBalance(uuid, shardType))
    }

    @Test
    @DisplayName("Cache Remove fail")
    fun cacheRemoveFail() {
        val cache = Cache()
        val uuid = UUID.randomUUID()
        val result = cache.removeBalance(uuid, ShardType.TOTAL)
        assertTrue(result.isFailure, "removeBalance result should be a failure with ShardType.TOTAL")
    }
}
