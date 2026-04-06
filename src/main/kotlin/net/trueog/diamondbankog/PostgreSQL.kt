package net.trueog.diamondbankog

import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.pool.ConnectionPool
import com.github.jasync.sql.db.postgresql.PostgreSQLConnection
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import java.util.*
import kotlinx.coroutines.future.await
import net.trueog.diamondbankog.DiamondBankException.*
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.DiamondBankOG.Companion.plugin
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource

class PostgreSQL private constructor() {
    lateinit var pool: ConnectionPool<PostgreSQLConnection>

    class NoRowsException : Exception()

    enum class ShardType(val string: String) {
        BANK("bank_shards"),
        INVENTORY("inventory_shards"),
        ENDER_CHEST("ender_chest_shards"),
        TOTAL("bank_shards, inventory_shards, ender_chest_shards"),
    }

    companion object {
        fun create(): PostgreSQL? {
            val postgreSQL = PostgreSQL()

            try {
                val pg =
                    PGSimpleDataSource().apply {
                        serverNames = arrayOf(config.postgresHost)
                        portNumbers = intArrayOf(config.postgresPort)
                        databaseName = config.postgresDatabase
                        user = config.postgresUser
                        password = config.postgresPassword
                    }
                val flyway = Flyway.configure(DiamondBankOG::class.java.classLoader).dataSource(pg).load()
                flyway.migrate()

                postgreSQL.pool =
                    PostgreSQLConnectionBuilder.createConnectionPool {
                        host = config.postgresHost
                        port = config.postgresPort
                        database = config.postgresDatabase
                        username = config.postgresUser
                        password = config.postgresPassword
                    }
                return postgreSQL
            } catch (e: Exception) {
                economyDisabled = true
                plugin.logger.severe(
                    "ECONOMY DISABLED! Something went wrong while trying to initialise PostgreSQL. Is PostgreSQL running? Are the PostgreSQL config variables correct?"
                )
                e.printStackTrace()
                return null
            }
        }
    }

    data class PlayerShards(val bank: Long, val inventory: Long, val enderChest: Long) {
        val total = bank + inventory + enderChest
    }

    suspend fun setPlayerShards(uuid: UUID, shards: Long, type: ShardType): Result<Unit> {
        if (type == ShardType.TOTAL) return Result.failure(InvalidArgumentException())
        val playerShards: PlayerShards

        try {
            val connection = pool.asSuspending.connect()

            val result =
                connection
                    .inTransaction { conn ->
                        conn.sendPreparedStatement(
                            "INSERT INTO diamond(uuid, ${type.string}) VALUES(?, ?) ON CONFLICT (uuid) DO UPDATE SET ${type.string} = excluded.${type.string} " +
                                "RETURNING bank_shards, inventory_shards, ender_chest_shards",
                            listOf(uuid, shards),
                        )
                    }
                    .await()

            if (result.rows.isEmpty()) {
                throw Exception()
            }
            val row = result.rows[0]
            val bankShards = row.getLong("bank_shards")
            val inventoryShards = row.getLong("inventory_shards")
            val enderChestShards = row.getLong("ender_chest_shards")
            if (bankShards == null || inventoryShards == null || enderChestShards == null) {
                throw Exception()
            }

            playerShards = PlayerShards(bankShards, inventoryShards, enderChestShards)
        } catch (e: Exception) {
            return Result.failure(DatabaseException(e.message ?: "Database exception"))
        }

        DiamondBankOG.eventManager.sendUpdate(uuid, playerShards)
        return Result.success(Unit)
    }

    suspend fun addToPlayerShards(uuid: UUID, shards: Long, type: ShardType): Result<Long> {
        if (type == ShardType.TOTAL) return Result.failure(InvalidArgumentException())
        val playerShards: PlayerShards

        try {
            val connection = pool.asSuspending.connect()

            val result =
                connection
                    .inTransaction { conn ->
                        conn.sendPreparedStatement(
                            "INSERT INTO diamond(uuid, ${type.string}) VALUES(?, ?) ON CONFLICT (uuid) DO UPDATE SET ${type.string} = diamond.${type.string} + excluded.${type.string} " +
                                "WHERE diamond.${type.string} + excluded.${type.string} >= 0 RETURNING bank_shards, inventory_shards, ender_chest_shards",
                            listOf(uuid, shards),
                        )
                    }
                    .await()

            if (result.rows.isEmpty()) {
                val shards =
                    balanceManager.getShardTypeShards(uuid, type).getOrElse {
                        return Result.failure(it)
                    }
                throw InsufficientBalanceException(shards)
            }
            val row = result.rows[0]
            val bankShards = row.getLong("bank_shards")
            val inventoryShards = row.getLong("inventory_shards")
            val enderChestShards = row.getLong("ender_chest_shards")
            if (bankShards == null || inventoryShards == null || enderChestShards == null) {
                throw Exception()
            }

            playerShards = PlayerShards(bankShards, inventoryShards, enderChestShards)
        } catch (e: Exception) {
            if (e is InsufficientBalanceException) {
                return Result.failure(e)
            }
            return Result.failure(DatabaseException(e.message ?: "Database exception"))
        }

        DiamondBankOG.eventManager.sendUpdate(uuid, playerShards)
        return when (type) {
            ShardType.BANK -> Result.success(playerShards.bank)
            ShardType.INVENTORY -> Result.success(playerShards.inventory)
            ShardType.ENDER_CHEST -> Result.success(playerShards.enderChest)
            else -> Result.failure(InvalidArgumentException())
        }
    }

    suspend fun getTotalShards(uuid: UUID): Result<Long> {
        var totalShards: Long?
        try {
            val connection = pool.asSuspending.connect()

            val result =
                connection
                    .inTransaction { conn ->
                        conn.sendPreparedStatement(
                            "SELECT total_shards FROM diamond WHERE uuid = ? LIMIT 1",
                            listOf(uuid),
                        )
                    }
                    .await()

            totalShards =
                if (result.rows.isNotEmpty()) {
                    val row = result.rows[0]
                    row.getLong("total_shards") ?: 0
                } else {
                    0
                }
        } catch (e: Exception) {
            plugin.logger.severe(e.toString())
            return Result.failure(DatabaseException(e.message ?: "Database exception"))
        }

        return Result.success(totalShards)
    }

    suspend fun getAllShards(uuid: UUID): Result<PlayerShards> {
        var bankShards: Long?
        var inventoryShards: Long?
        var enderChestShards: Long?
        try {
            val connection = pool.asSuspending.connect()

            val result =
                connection
                    .inTransaction { conn ->
                        conn.sendPreparedStatement(
                            "SELECT bank_shards, inventory_shards, ender_chest_shards FROM diamond WHERE uuid = ? LIMIT 1",
                            listOf(uuid),
                        )
                    }
                    .await()

            if (result.rows.isNotEmpty()) {
                val row = result.rows[0]
                bankShards = row.getLong("bank_shards") ?: 0
                inventoryShards = row.getLong("inventory_shards") ?: 0
                enderChestShards = row.getLong("ender_chest_shards") ?: 0
            } else {
                bankShards = 0
                inventoryShards = 0
                enderChestShards = 0
            }
        } catch (e: Exception) {
            plugin.logger.severe(e.toString())
            return Result.failure(DatabaseException(e.message ?: "Database exception"))
        }

        return Result.success(PlayerShards(bankShards, inventoryShards, enderChestShards))
    }

    suspend fun getShardTypeShards(uuid: UUID, type: ShardType): Result<Long> {
        var shards: Long?
        try {
            val connection = pool.asSuspending.connect()

            val result =
                connection
                    .inTransaction { conn ->
                        conn.sendPreparedStatement(
                            "SELECT ${type.string} FROM diamond WHERE uuid = ? LIMIT 1",
                            listOf(uuid),
                        )
                    }
                    .await()

            shards =
                if (result.rows.isNotEmpty()) {
                    val row = result.rows[0]
                    row.getLong(type.string) ?: 0
                } else {
                    0
                }
        } catch (e: Exception) {
            plugin.logger.severe(e.toString())
            return Result.failure(DatabaseException(e.message ?: "Database exception"))
        }

        return Result.success(shards)
    }

    suspend fun getBaltop(offset: Int): Result<Map<UUID?, Long>> {
        try {
            val connection = pool.asSuspending.connect()
            val result =
                connection
                    .inTransaction { conn ->
                        conn.sendPreparedStatement(
                            "SELECT uuid, total_shards " +
                                "FROM diamond " +
                                "ORDER BY total_shards DESC, uuid DESC OFFSET ? LIMIT 9",
                            listOf(offset),
                        )
                    }
                    .await()
            val baltop = mutableMapOf<UUID?, Long>()
            result.rows.forEach {
                val uuid = it.getAs<UUID>("uuid")
                val totalShards = it.getLong("total_shards") ?: 0
                baltop[uuid] = totalShards
            }
            return Result.success(baltop)
        } catch (e: Exception) {
            plugin.logger.severe(e.toString())
            return Result.failure(DatabaseException(e.message ?: "Database exception"))
        }
    }

    /**
     * @return Pair with as the first value a map with the player name and total balance and as the second value the
     *   offset
     */
    suspend fun getBaltopWithUuid(uuid: UUID): Result<Pair<Map<UUID?, Long>, Long>> {
        try {
            val connection = pool.asSuspending.connect()
            val result =
                connection
                    .inTransaction { conn ->
                        // @formatter:off
                        conn.sendPreparedStatement(
                            "WITH ranked AS (" +
                                "SELECT " +
                                "uuid, total_shards, ROW_NUMBER() OVER (ORDER BY total_shards DESC, uuid DESC) AS rn " +
                                "FROM diamond" +
                                "), " +
                                "target AS (" +
                                "SELECT rn, ((rn - 1) / 9) * 9 AS page_offset FROM ranked WHERE uuid = ?), " +
                                "paged AS (" +
                                "SELECT ranked.*, target.page_offset FROM ranked JOIN target ON true " +
                                "WHERE ranked.rn > target.page_offset AND ranked.rn <= target.page_offset + 9" +
                                ") " +
                                "SELECT uuid, total_shards, page_offset FROM paged ORDER BY rn",
                            listOf(uuid),
                        )
                        // @formatter:on
                    }
                    .await()
            val baltop = mutableMapOf<UUID?, Long>()
            var offset = 0L
            result.rows.forEach {
                val uuid = it.getAs<UUID>("uuid")
                val totalShards = it.getLong("total_shards") ?: 0
                offset = it.getLong("page_offset") ?: 0
                baltop[uuid] = totalShards
            }
            return Result.success(Pair(baltop, offset))
        } catch (e: Exception) {
            plugin.logger.severe(e.toString())
            return Result.failure(DatabaseException(e.message ?: "Database exception"))
        }
    }

    suspend fun getNumberOfRows(): Result<Long> {
        var number: Long?
        try {
            val connection = pool.asSuspending.connect()
            val result =
                connection
                    .inTransaction { conn -> conn.sendPreparedStatement("SELECT count(*) AS exact_count FROM diamond") }
                    .await()

            if (result.rows.isNotEmpty()) {
                val row = result.rows[0]
                number = row.getLong(0) ?: 0
            } else {
                return Result.failure(NoRowsException())
            }
            return Result.success(number)
        } catch (e: Exception) {
            plugin.logger.severe(e.toString())
            return Result.failure(DatabaseException(e.message ?: "Database exception"))
        }
    }

    suspend fun insertTransactionLog(
        playerUuid: UUID,
        transferredShards: Long,
        playerToUuid: UUID?,
        transactionReason: String,
        notes: String?,
    ): Result<Unit> {
        try {
            val connection = pool.asSuspending.connect()

            connection
                .inTransaction { conn ->
                    conn.sendPreparedStatement(
                        "INSERT INTO diamond_log(player_uuid, transferred_shards, player_to_uuid, transaction_reason, notes) " +
                            "VALUES(?, ?, ?, ?, ?)",
                        listOf(playerUuid, transferredShards, playerToUuid, transactionReason, notes),
                    )
                }
                .await()
        } catch (e: Exception) {
            return Result.failure(DatabaseException(e.message ?: "Database exception"))
        }
        return Result.success(Unit)
    }

    suspend fun hasEntry(uuid: UUID): Result<Boolean> {
        try {
            val connection = pool.asSuspending.connect()
            val result =
                connection
                    .inTransaction { conn ->
                        conn.sendPreparedStatement("SELECT 1 FROM diamond WHERE uuid = ?", listOf(uuid))
                    }
                    .await()

            if (result.rows.size == 1) {
                return Result.success(true)
            }
        } catch (e: Exception) {
            return Result.failure(DatabaseException(e.message ?: "Database exception"))
        }
        return Result.success(false)
    }
}
