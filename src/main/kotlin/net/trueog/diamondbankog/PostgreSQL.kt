package net.trueog.diamondbankog

import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.pool.ConnectionPool
import com.github.jasync.sql.db.postgresql.PostgreSQLConnection
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import kotlinx.coroutines.future.await
import net.trueog.diamondbankog.DiamondBankException.*
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.DiamondBankOG.Companion.plugin
import java.sql.SQLException
import java.util.*

class PostgreSQL {
    lateinit var pool: ConnectionPool<PostgreSQLConnection>

    class NoRowsException : Exception()

    enum class ShardType(val string: String) {
        BANK("bank_shards"),
        INVENTORY("inventory_shards"),
        ENDER_CHEST("ender_chest_shards"),
        TOTAL("bank_shards, inventory_shards, ender_chest_shards"),
    }

    @Throws(SQLException::class, ClassNotFoundException::class)
    fun initDB() {
        try {
            pool =
                PostgreSQLConnectionBuilder.createConnectionPool(
                    "${config.postgresUrl}?user=${config.postgresUser}&password=${config.postgresPassword}"
                )
            val createTable =
                pool.sendPreparedStatement(
                    "CREATE TABLE IF NOT EXISTS ${config.postgresTable}(uuid UUID PRIMARY KEY, bank_shards BIGINT DEFAULT 0, inventory_shards BIGINT DEFAULT 0, ender_chest_shards BIGINT DEFAULT 0, total_shards BIGINT GENERATED ALWAYS AS ( COALESCE(bank_shards, 0) + COALESCE(inventory_shards, 0) + COALESCE(ender_chest_shards, 0) ) STORED)"
                )
            createTable.join()
            val createTotalShardsIndex =
                pool.sendPreparedStatement(
                    "CREATE INDEX IF NOT EXISTS idx_total_shards ON ${config.postgresTable}(total_shards DESC)"
                )
            createTotalShardsIndex.join()

            val createLogTable =
                pool.sendPreparedStatement(
                    "CREATE TABLE IF NOT EXISTS ${config.postgresLogTable}(id SERIAL PRIMARY KEY, player_uuid UUID NOT NULL, transferred_shards BIGINT NOT NULL, player_to_uuid UUID, transaction_reason TEXT, notes TEXT, timestamp TIMESTAMPTZ DEFAULT NOW())"
                )
            createLogTable.join()
            // @formatter:off
            val createFunction =
                pool.sendPreparedStatement(
                    "CREATE OR REPLACE FUNCTION fifo_limit_trigger() RETURNS TRIGGER AS $$" +
                        "DECLARE " +
                        "max_rows CONSTANT BIGINT := ${config.postgresLogLimit};" +
                        "BEGIN " +
                        "DELETE FROM ${config.postgresLogTable} " +
                        "WHERE id <= (" +
                        "SELECT id FROM ${config.postgresLogTable} " +
                        "ORDER BY id DESC " +
                        "OFFSET max_rows LIMIT 1" +
                        ");" +
                        "RETURN NEW;" +
                        "END;" +
                        "$$ LANGUAGE plpgsql;"
                )
            // @formatter:on
            createFunction.join()
            val createTrigger =
                pool.sendPreparedStatement(
                    "CREATE OR REPLACE TRIGGER fifo_limit_trigger\n" +
                            "AFTER INSERT ON ${config.postgresLogTable}\n" +
                            "FOR EACH STATEMENT\n" +
                            "EXECUTE FUNCTION fifo_limit_trigger();"
                )
            createTrigger.join()
        } catch (e: Exception) {
            economyDisabled = true
            plugin.logger.severe(
                "ECONOMY DISABLED! Something went wrong while trying to initialise PostgreSQL. Is PostgreSQL running? Are the PostgreSQL config variables correct?"
            )
            e.printStackTrace()
            return
        }
    }

    data class PlayerShards(val bank: Long, val inventory: Long, val enderChest: Long)

    suspend fun setPlayerShards(uuid: UUID, shards: Long, type: ShardType): Result<Unit> {
        if (type == ShardType.TOTAL) return Result.failure(InvalidArgumentException())
        val playerShards: PlayerShards

        try {
            val connection = pool.asSuspending.connect()

            val preparedStatement =
                connection.sendPreparedStatement(
                    "INSERT INTO ${config.postgresTable}(uuid, ${type.string}) VALUES(?, ?) ON CONFLICT (uuid) DO UPDATE SET ${type.string} = excluded.${type.string} " +
                            "RETURNING bank_shards, inventory_shards, ender_chest_shards",
                    listOf(uuid, shards),
                )
            val result = preparedStatement.await()

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

    suspend fun addToPlayerShards(uuid: UUID, shards: Long, type: ShardType): Result<Unit> {
        if (type == ShardType.TOTAL) return Result.failure(InvalidArgumentException())
        val playerShards: PlayerShards

        try {
            val connection = pool.asSuspending.connect()

            val preparedStatement =
                connection.sendPreparedStatement(
                    "INSERT INTO ${config.postgresTable}(uuid, ${type.string}) VALUES(?, ?) ON CONFLICT (uuid) DO UPDATE SET ${type.string} = ${config.postgresTable}.${type.string} + excluded.${type.string} " +
                            "WHERE ${config.postgresTable}.${type.string} + excluded.${type.string} >= 0 RETURNING bank_shards, inventory_shards, ender_chest_shards",
                    listOf(uuid, shards),
                )
            val result = preparedStatement.await()

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
        return Result.success(Unit)
    }

    suspend fun getTotalShards(uuid: UUID): Result<Long> {
        var totalShards: Long?
        try {
            val connection = pool.asSuspending.connect()

            val preparedStatement =
                connection.sendPreparedStatement(
                    "SELECT total_shards FROM ${config.postgresTable} WHERE uuid = ? LIMIT 1",
                    listOf(uuid),
                )
            val result = preparedStatement.await()

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

            val preparedStatement =
                connection.sendPreparedStatement(
                    "SELECT bank_shards, inventory_shards, ender_chest_shards FROM ${config.postgresTable} WHERE uuid = ? LIMIT 1",
                    listOf(uuid),
                )
            val result = preparedStatement.await()

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

            val preparedStatement =
                connection.sendPreparedStatement(
                    "SELECT ${type.string} FROM ${config.postgresTable} WHERE uuid = ? LIMIT 1",
                    listOf(uuid),
                )
            val result = preparedStatement.await()

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
            val preparedStatement =
                connection.sendPreparedStatement(
                    "SELECT uuid, total_shards " +
                            "FROM ${config.postgresTable} " +
                            "ORDER BY total_shards DESC, uuid DESC OFFSET ? LIMIT 9",
                    listOf(offset),
                )
            val result = preparedStatement.await()
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
            // @formatter:off
            val preparedStatement =
                connection.sendPreparedStatement(
                    "WITH ranked AS (" +
                        "SELECT " +
                        "uuid, total_shards, ROW_NUMBER() OVER (ORDER BY total_shards DESC, uuid DESC) AS rn " +
                        "FROM ${config.postgresTable}" +
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
            val result = preparedStatement.await()
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
            val preparedStatement =
                connection.sendPreparedStatement("SELECT count(*) AS exact_count FROM ${config.postgresTable}")
            val result = preparedStatement.await()

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

            val preparedStatement =
                connection.sendPreparedStatement(
                    "INSERT INTO ${config.postgresLogTable}(player_uuid, transferred_shards, player_to_uuid, transaction_reason, notes) " +
                            "VALUES(?, ?, ?, ?, ?)",
                    listOf(playerUuid, transferredShards, playerToUuid, transactionReason, notes),
                )
            preparedStatement.await()
        } catch (e: Exception) {
            return Result.failure(DatabaseException(e.message ?: "Database exception"))
        }
        return Result.success(Unit)
    }

    suspend fun hasEntry(uuid: UUID): Result<Boolean> {
        try {
            val connection = pool.asSuspending.connect()
            val preparedStatement =
                connection.sendPreparedStatement("SELECT 1 FROM ${config.postgresTable} WHERE uuid = ?", listOf(uuid))
            val result = preparedStatement.await()

            if (result.rows.size == 1) {
                return Result.success(true)
            }
        } catch (e: Exception) {
            return Result.failure(DatabaseException(e.message ?: "Database exception"))
        }
        return Result.success(false)
    }
}
