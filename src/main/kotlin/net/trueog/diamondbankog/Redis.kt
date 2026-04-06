package net.trueog.diamondbankog

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisConnectionException
import io.lettuce.core.RedisURI
import net.trueog.diamondbankog.DiamondBankOG.Companion.config

internal class Redis {
    private val redisClient: RedisClient = RedisClient.create(RedisURI.Builder.redis(config.redisHost, config.redisPort).withPassword(config.redisPassword).withDatabase(1).build())

    /** @return True if failed */
    fun testConnection(): Boolean {
        try {
            val connection = redisClient.connect()
            connection.close()
            return false
        } catch (_: RedisConnectionException) {
            return true
        }
    }

    fun getValue(key: String): String? {
        val connection = redisClient.connect()
        val commands = connection.sync()
        val value = commands.get(key)
        connection.close()
        return value
    }

    fun setValue(key: String, value: String) {
        val connection = redisClient.connect()
        val commands = connection.sync()
        commands.set(key, value)
        connection.close()
    }

    fun shutdown() {
        redisClient.shutdown()
    }
}
