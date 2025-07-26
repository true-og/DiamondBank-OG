package net.trueog.diamondbankog

import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.general.ArrayRowData
import com.github.jasync.sql.db.pool.ConnectionPool
import com.github.jasync.sql.db.postgresql.PostgreSQLConnection
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import java.sql.SQLException
import java.util.*
import kotlinx.coroutines.future.await
import net.trueog.diamondbankog.DiamondBankException.DatabaseException
import net.trueog.diamondbankog.DiamondBankException.InsufficientBalanceException
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.DiamondBankOG.Companion.plugin

class PostgreSQL {
    lateinit var pool: ConnectionPool<PostgreSQLConnection>

    object InvalidArgumentException : Exception() {
        @Suppress("unused") private fun readResolve(): Any = InvalidArgumentException
    }

    object NoRowsException : Exception() {
        @Suppress("unused") private fun readResolve(): Any = NoRowsException
    }

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
                    "CREATE TABLE IF NOT EXISTS ${config.postgresTable}(uuid UUID PRIMARY KEY, bank_shards BIGINT, inventory_shards BIGINT, ender_chest_shards BIGINT, total_shards BIGINT GENERATED ALWAYS AS ( COALESCE(bank_shards, 0) + COALESCE(inventory_shards, 0) + COALESCE(ender_chest_shards, 0) ) STORED)"
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
        } catch (_: Exception) {
            economyDisabled = true
            plugin.logger.severe(
                "ECONOMY DISABLED! Something went wrong while trying to initialise PostgreSQL. Is PostgreSQL running? Are the PostgreSQL config variables correct?"
            )
            return
        }
    }

    data class PlayerShards(val bank: Int, val inventory: Int, val enderChest: Int)

    suspend fun setPlayerShards(uuid: UUID, shards: Int, type: ShardType): Result<Unit> {
        if (type == ShardType.TOTAL) return Result.failure(InvalidArgumentException)

        try {
            val connection = pool.asSuspending.connect()

            val preparedStatement =
                connection.sendPreparedStatement(
                    "INSERT INTO ${config.postgresTable}(uuid, ${type.string}) VALUES(?, ?) ON CONFLICT (uuid) DO UPDATE SET ${type.string} = excluded.${type.string}",
                    listOf(uuid, shards),
                )
            preparedStatement.await()
        } catch (e: Exception) {
            return Result.failure(DatabaseException(e.message ?: "Database exception"))
        }
        return Result.success(Unit)
    }

    suspend fun addToPlayerShards(uuid: UUID, shards: Int, type: ShardType): Result<Unit> {
        if (type == ShardType.TOTAL) return Result.failure(InvalidArgumentException)

        val playerShards =
            when (type) {
                ShardType.BANK -> getBankShards(uuid)
                ShardType.INVENTORY -> getInventoryShards(uuid)
                ShardType.ENDER_CHEST -> getEnderChestShards(uuid)
                else -> {
                    return Result.failure(InvalidArgumentException)
                }
            }.getOrElse {
                return Result.failure(it)
            }

        return setPlayerShards(uuid, playerShards + shards, type)
    }

    suspend fun subtractFromBankShards(uuid: UUID, shards: Int): Result<Unit> {
        val bankShards =
            getBankShards(uuid).getOrElse {
                return Result.failure(it)
            }
        val newBalance = bankShards - shards
        if (newBalance < 0) {
            return Result.failure(InsufficientBalanceException(bankShards))
        }

        return setPlayerShards(uuid, newBalance, ShardType.BANK)
    }

    suspend fun getBankShards(uuid: UUID) = getShardTypeShards(uuid, ShardType.BANK)

    suspend fun getInventoryShards(uuid: UUID) = getShardTypeShards(uuid, ShardType.INVENTORY)

    suspend fun getEnderChestShards(uuid: UUID) = getShardTypeShards(uuid, ShardType.ENDER_CHEST)

    suspend fun getTotalShards(uuid: UUID): Result<Int> {
        var totalShards: Int?
        try {
            val connection = pool.asSuspending.connect()

            val preparedStatement =
                connection.sendPreparedStatement(
                    "SELECT bank_shards, inventory_shards, ender_chest_shards FROM ${config.postgresTable} WHERE uuid = ? LIMIT 1",
                    listOf(uuid),
                )
            val result = preparedStatement.await()

            totalShards =
                if (result.rows.isNotEmpty()) {
                    val rowData = result.rows[0] as ArrayRowData
                    val bankShards =
                        if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                    val inventoryShards =
                        if (rowData.columns[1] != null) {
                            rowData.columns[1] as Int
                        } else 0
                    val enderChestShards =
                        if (rowData.columns[2] != null) {
                            rowData.columns[2] as Int
                        } else 0

                    bankShards + inventoryShards + enderChestShards
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
        var bankShards: Int?
        var inventoryShards: Int?
        var enderChestShards: Int?
        try {
            val connection = pool.asSuspending.connect()

            val preparedStatement =
                connection.sendPreparedStatement(
                    "SELECT bank_shards, inventory_shards, ender_chest_shards FROM ${config.postgresTable} WHERE uuid = ? LIMIT 1",
                    listOf(uuid),
                )
            val result = preparedStatement.await()

            if (result.rows.isNotEmpty()) {
                val rowData = result.rows[0] as ArrayRowData
                bankShards =
                    if (rowData.columns[0] != null) {
                        rowData.columns[0] as Int
                    } else 0
                inventoryShards =
                    if (rowData.columns[1] != null) {
                        rowData.columns[1] as Int
                    } else 0
                enderChestShards =
                    if (rowData.columns[2] != null) {
                        rowData.columns[2] as Int
                    } else 0
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

    private suspend fun getShardTypeShards(uuid: UUID, type: ShardType): Result<Int> {
        var shards: Int?
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
                    val rowData = result.rows[0] as ArrayRowData
                    if (rowData.columns[0] != null) {
                        rowData.columns[0] as Int
                    } else 0
                } else {
                    0
                }
        } catch (e: Exception) {
            plugin.logger.severe(e.toString())
            return Result.failure(DatabaseException(e.message ?: "Database exception"))
        }

        return Result.success(shards)
    }

    suspend fun getBaltop(offset: Int): Result<Map<UUID?, Int>> {
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
            val baltop = mutableMapOf<UUID?, Int>()
            result.rows.forEach {
                val rowData = it as ArrayRowData
                val uuid =
                    if (rowData.columns[0] != null) {
                        rowData.columns[0] as UUID
                    } else null
                val totalShards =
                    if (rowData.columns[1] != null) {
                        rowData.columns[1] as Int
                    } else 0

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
    suspend fun getBaltopWithUuid(uuid: UUID): Result<Pair<Map<UUID?, Int>, Long>> {
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
            val baltop = mutableMapOf<UUID?, Int>()
            var offset = 0L
            result.rows.forEach {
                val rowData = it as ArrayRowData
                val uuid =
                    if (rowData.columns[0] != null) {
                        rowData.columns[0] as UUID
                    } else null
                val totalShards =
                    if (rowData.columns[1] != null) {
                        rowData.columns[1] as Int
                    } else 0

                offset =
                    if (rowData.columns[2] != null) {
                        rowData.columns[2] as Long
                    } else 0L

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
                val rowData = result.rows[0] as ArrayRowData
                number =
                    if (rowData.columns[0] != null) {
                        rowData.columns[0] as Long
                    } else 0
            } else {
                return Result.failure(NoRowsException)
            }
            return Result.success(number)
        } catch (e: Exception) {
            plugin.logger.severe(e.toString())
            return Result.failure(DatabaseException(e.message ?: "Database exception"))
        }
    }

    suspend fun insertTransactionLog(
        playerUuid: UUID,
        transferredShards: Int,
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
}
