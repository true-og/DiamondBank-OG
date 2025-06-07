package net.trueog.diamondbankog

import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.general.ArrayRowData
import com.github.jasync.sql.db.pool.ConnectionPool
import com.github.jasync.sql.db.postgresql.PostgreSQLConnection
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import kotlinx.coroutines.future.await
import org.bukkit.Bukkit
import java.sql.SQLException
import java.util.*

class PostgreSQL {
    lateinit var pool: ConnectionPool<PostgreSQLConnection>

    enum class ShardType(val string: String) {
        BANK("bank_shards"), INVENTORY("inventory_shards"), ENDER_CHEST("ender_chest_shards"), ALL("bank_shards, inventory_shards, ender_chest_shards")
    }

    @Throws(SQLException::class, ClassNotFoundException::class)
    fun initDB() {
        try {
            pool =
                PostgreSQLConnectionBuilder.createConnectionPool("${Config.postgresUrl}?user=${Config.postgresUser}&password=${Config.postgresPassword}")
            val createTable =
                pool.sendPreparedStatement("CREATE TABLE IF NOT EXISTS ${Config.postgresTable}(uuid UUID PRIMARY KEY, bank_shards INTEGER, inventory_shards INTEGER, ender_chest_shards INTEGER, total_shards INTEGER GENERATED ALWAYS AS ( COALESCE(bank_shards, 0) + COALESCE(inventory_shards, 0) + COALESCE(ender_chest_shards, 0) ) STORED)")
            createTable.join()

            val createTotalShardsIndex =
                pool.sendPreparedStatement("CREATE INDEX IF NOT EXISTS idx_total_shards ON ${Config.postgresTable}(total_shards DESC)")
            createTotalShardsIndex.join()
        } catch (_: Exception) {
            DiamondBankOG.economyDisabled = true
            DiamondBankOG.plugin.logger.severe("ECONOMY DISABLED! Something went wrong while trying to initialise PostgreSQL. Is PostgreSQL running? Are the PostgreSQL config variables correct?")
            return
        }
    }

    data class PlayerShards(val shardsInBank: Int?, val shardsInInventory: Int?, val shardsInEnderChest: Int?) {
        fun isNeededShardTypeNull(type: ShardType): Boolean {
            when (type) {
                ShardType.BANK -> {
                    if (this.shardsInBank == null) {
                        return true
                    }
                }

                ShardType.INVENTORY -> {
                    if (this.shardsInInventory == null) {
                        return true
                    }
                }

                ShardType.ENDER_CHEST -> {
                    if (this.shardsInEnderChest == null) {
                        return true
                    }
                }

                ShardType.ALL -> {
                    if (this.shardsInBank == null || this.shardsInInventory == null || this.shardsInEnderChest == null) {
                        return true
                    }
                }
            }
            return false
        }
    }

    /**
     * @return True if failed
     */
    suspend fun setPlayerShards(uuid: UUID, shards: Int, type: ShardType): Boolean {
        if (type == ShardType.ALL) return true
        try {
            val connection = pool.asSuspending.connect()

            val preparedStatement =
                connection.sendPreparedStatement(
                    "INSERT INTO ${Config.postgresTable}(uuid, ${type.string}) VALUES(?, ?) ON CONFLICT (uuid) DO UPDATE SET ${type.string} = excluded.${type.string}",
                    listOf(uuid, shards)
                )
            preparedStatement.await()
        } catch (_: Exception) {
            return true
        }
        return false
    }

    /**
     * @return True if failed
     */
    suspend fun addToPlayerShards(uuid: UUID, shards: Int, type: ShardType): Boolean {
        if (type == ShardType.ALL) return true

        val playerDiamonds = getPlayerShardsWrapper(uuid, type) ?: return true

        val error = setPlayerShards(uuid, playerDiamonds + shards, type)
        return error
    }

    /**
     * @return True if failed
     */
    suspend fun subtractFromPlayerShards(uuid: UUID, shards: Int, type: ShardType): Boolean {
        if (type == ShardType.ALL) return true

        val playerDiamonds = getPlayerShardsWrapper(uuid, type) ?: return true

        val error = setPlayerShards(uuid, playerDiamonds - shards, type)
        return error
    }

    private suspend fun getPlayerShardsWrapper(uuid: UUID, type: ShardType): Int? {
        val getResponse = getPlayerShards(uuid, type)

        return when (type) {
            ShardType.BANK -> getResponse.shardsInBank
            ShardType.INVENTORY -> getResponse.shardsInInventory
            ShardType.ENDER_CHEST -> getResponse.shardsInEnderChest
            ShardType.ALL -> if (getResponse.shardsInBank != null && getResponse.shardsInInventory != null && getResponse.shardsInEnderChest != null) getResponse.shardsInBank + getResponse.shardsInInventory + getResponse.shardsInEnderChest else null
        }
    }

    suspend fun getPlayerShards(uuid: UUID, type: ShardType): PlayerShards {
        var bankShards: Int? = null
        var inventoryShards: Int? = null
        var enderChestShards: Int? = null
        try {
            val connection = pool.asSuspending.connect()

            val preparedStatement =
                connection.sendPreparedStatement(
                    "SELECT ${type.string} FROM ${Config.postgresTable} WHERE uuid = ? LIMIT 1",
                    listOf(uuid)
                )
            val result = preparedStatement.await()

            if (result.rows.isNotEmpty()) {
                val rowData = result.rows[0] as ArrayRowData

                when (type) {
                    ShardType.BANK -> {
                        bankShards = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                    }

                    ShardType.INVENTORY -> {
                        inventoryShards = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                    }

                    ShardType.ENDER_CHEST -> {
                        enderChestShards = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                    }

                    ShardType.ALL -> {
                        bankShards = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                        inventoryShards = if (rowData.columns[1] != null) {
                            rowData.columns[1] as Int
                        } else 0
                        enderChestShards = if (rowData.columns[2] != null) {
                            rowData.columns[2] as Int
                        } else 0
                    }
                }
            } else {
                bankShards = 0
                inventoryShards = 0
                enderChestShards = 0
            }
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.severe(e.toString())
        }

        return PlayerShards(bankShards, inventoryShards, enderChestShards)
    }

    suspend fun getBaltop(offset: Int): MutableMap<String?, Int>? {
        try {
            val connection = pool.asSuspending.connect()
            val preparedStatement =
                connection.sendPreparedStatement(
                    "SELECT uuid, bank_shards, inventory_shards, ender_chest_shards " +
                            "FROM ${Config.postgresTable} " +
                            "ORDER BY total_shards DESC OFFSET ? LIMIT 10", listOf(offset)
                )
            val result = preparedStatement.await()
            val baltop = mutableMapOf<String?, Int>()
            result.rows.forEach {
                val rowData = it as ArrayRowData
                val bankDiamonds = if (rowData.columns[1] != null) {
                    rowData.columns[1] as Int
                } else 0
                val inventoryDiamonds = if (rowData.columns[2] != null) {
                    rowData.columns[2] as Int
                } else 0
                val enderChestDiamonds = if (rowData.columns[3] != null) {
                    rowData.columns[3] as Int
                } else 0

                val player =
                    Bukkit.getPlayer(rowData.columns[0] as UUID) ?: Bukkit.getOfflinePlayer(rowData.columns[0] as UUID)
                baltop[player.name] = bankDiamonds + inventoryDiamonds + enderChestDiamonds
            }
            return baltop
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.severe(e.toString())
        }
        return null
    }

    suspend fun getNumberOfRows(): Long? {
        var number: Long? = null
        try {
            val connection = pool.asSuspending.connect()
            val preparedStatement =
                connection.sendPreparedStatement("SELECT count(*) AS exact_count FROM ${Config.postgresTable}")
            val result = preparedStatement.await()

            if (result.rows.isNotEmpty()) {
                val rowData = result.rows[0] as ArrayRowData
                number = if (rowData.columns[0] != null) {
                    rowData.columns[0] as Long
                } else 0
            }
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.severe(e.toString())
        }
        return number
    }
}
