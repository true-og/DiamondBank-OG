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

    enum class DiamondType {
        BANK, INVENTORY, ENDER_CHEST, ALL
    }

    @Throws(SQLException::class, ClassNotFoundException::class)
    fun initDB() {
        try {
            pool =
                PostgreSQLConnectionBuilder.createConnectionPool("${Config.postgresUrl}?user=${Config.postgresUser}&password=${Config.postgresPassword}")
            val createTable =
                pool.sendPreparedStatement("CREATE TABLE IF NOT EXISTS ${Config.postgresTable}(uuid TEXT, bank_diamonds integer, inventory_diamonds integer, ender_chest_diamonds integer, bank_shards integer, inventory_shards integer, ender_chest_shards integer, unique(uuid))")
            createTable.join()
            val createIndex =
                pool.sendPreparedStatement("CREATE INDEX IF NOT EXISTS idx_balance ON ${Config.postgresTable}(bank_diamonds, inventory_diamonds, ender_chest_diamonds, bank_shards, inventory_shards, ender_chest_shards)")
            createIndex.join()
        } catch (e: Exception) {
            DiamondBankOG.economyDisabled = true
            DiamondBankOG.plugin.logger.severe("ECONOMY DISABLED! Something went wrong while trying to initialise PostgreSQL. Is PostgreSQL running? Are the PostgreSQL config variables correct?")
            return
        }
    }

    suspend fun setPlayerDiamonds(uuid: UUID, balance: Int, type: DiamondType): Boolean {
        try {
            val connection = pool.asSuspending.connect()

            val diamondType = when (type) {
                DiamondType.BANK -> "bank_diamonds"
                DiamondType.INVENTORY -> "inventory_diamonds"
                DiamondType.ENDER_CHEST -> "ender_chest_diamonds"
                else -> return true
            }

            val preparedStatement =
                connection.sendPreparedStatement("INSERT INTO ${Config.postgresTable}(uuid, $diamondType) VALUES('$uuid', $balance) ON CONFLICT (uuid) DO UPDATE SET $diamondType = excluded.$diamondType")
            preparedStatement.await()
        } catch (e: Exception) {
            return true
        }
        return false
    }

    suspend fun addToPlayerDiamonds(uuid: UUID, amount: Int, type: DiamondType): Boolean {
        val playerDiamonds = getPlayerDiamondsWrapper(uuid, type) ?: return true

        val error = setPlayerDiamonds(uuid, playerDiamonds + amount, type)
        return error
    }

    suspend fun subtractFromPlayerDiamonds(uuid: UUID, amount: Int, type: DiamondType): Boolean {
        val playerDiamonds = getPlayerDiamondsWrapper(uuid, type) ?: return true

        val error = setPlayerDiamonds(uuid, playerDiamonds - amount, type)
        return error
    }

    private suspend fun getPlayerDiamondsWrapper(uuid: UUID, type: DiamondType): Int? {
        val playerDiamonds = getPlayerDiamonds(uuid, type)

        return when (type) {
            DiamondType.BANK -> playerDiamonds.bankDiamonds
            DiamondType.INVENTORY -> playerDiamonds.inventoryDiamonds
            DiamondType.ENDER_CHEST -> playerDiamonds.enderChestDiamonds
            DiamondType.ALL -> if (playerDiamonds.bankDiamonds != null && playerDiamonds.inventoryDiamonds != null && playerDiamonds.enderChestDiamonds != null) playerDiamonds.bankDiamonds + playerDiamonds.inventoryDiamonds + playerDiamonds.enderChestDiamonds else null
        }
    }

    data class PlayerDiamonds(val bankDiamonds: Int?, val inventoryDiamonds: Int?, val enderChestDiamonds: Int?)

    suspend fun getPlayerDiamonds(uuid: UUID, type: DiamondType): PlayerDiamonds {
        var bankDiamonds: Int? = null
        var inventoryDiamonds: Int? = null
        var enderChestDiamonds: Int? = null
        try {
            val connection = pool.asSuspending.connect()

            val diamondType = when (type) {
                DiamondType.BANK -> "bank_diamonds"
                DiamondType.INVENTORY -> "inventory_diamonds"
                DiamondType.ENDER_CHEST -> "ender_chest_diamonds"
                DiamondType.ALL -> "bank_diamonds, inventory_diamonds, ender_chest_diamonds"
            }

            val preparedStatement =
                connection.sendPreparedStatement("SELECT $diamondType FROM ${Config.postgresTable} WHERE uuid = '$uuid' LIMIT 1")
            val result = preparedStatement.await()

            if (result.rows.size != 0) {
                val rowData = result.rows[0] as ArrayRowData

                when (type) {
                    DiamondType.BANK -> {
                        bankDiamonds = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                    }

                    DiamondType.INVENTORY -> {
                        inventoryDiamonds = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                    }

                    DiamondType.ENDER_CHEST -> {
                        enderChestDiamonds = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                    }

                    DiamondType.ALL -> {
                        bankDiamonds = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                        inventoryDiamonds = if (rowData.columns[1] != null) {
                            rowData.columns[1] as Int
                        } else 0
                        enderChestDiamonds = if (rowData.columns[2] != null) {
                            rowData.columns[2] as Int
                        } else 0
                    }
                }
            } else {
                bankDiamonds = 0
                inventoryDiamonds = 0
                enderChestDiamonds = 0
            }
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.info(e.toString())
        }
        return PlayerDiamonds(bankDiamonds, inventoryDiamonds, enderChestDiamonds)
    }

    suspend fun getBaltop(offset: Int): MutableMap<String?, Int>? {
        try {
            val connection = pool.asSuspending.connect()
            val preparedStatement =
                connection.sendPreparedStatement("SELECT * FROM ${Config.postgresTable} ORDER BY bank_diamonds DESC, inventory_diamonds DESC, ender_chest_diamonds DESC OFFSET $offset LIMIT 10")
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

                val player = Bukkit.getPlayer(UUID.fromString(rowData.columns[0] as String)) ?: Bukkit.getOfflinePlayer(
                    UUID.fromString(rowData.columns[0] as String)
                )
                baltop[player.name] = bankDiamonds + inventoryDiamonds + enderChestDiamonds
            }
            return baltop
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.info(e.toString())
        }
        return null
    }

    suspend fun getNumberOfRows(): Int? {
        var number: Int? = null
        try {
            val connection = pool.asSuspending.connect()
            val preparedStatement =
                connection.sendPreparedStatement("SELECT count(*) AS exact_count FROM ${Config.postgresTable}")
            val result = preparedStatement.await()

            if (result.rows.size != 0) {
                val rowData = result.rows[0] as ArrayRowData
                number = if (rowData.columns[0] != null) {
                    rowData.columns[0] as Int
                } else 0
            }
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.info(e.toString())
        }
        return number
    }
}
