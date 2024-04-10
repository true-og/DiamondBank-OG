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

    enum class BalanceType {
        BANK_BALANCE, INVENTORY_BALANCE, ENDER_CHEST_BALANCE, ALL
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

    suspend fun setPlayerBalance(uuid: UUID, balance: Int, type: BalanceType): Boolean {
        try {
            val connection = pool.asSuspending.connect()

            val balanceType = when (type) {
                BalanceType.BANK_BALANCE -> "bank_diamonds"
                BalanceType.INVENTORY_BALANCE -> "inventory_diamonds"
                BalanceType.ENDER_CHEST_BALANCE -> "ender_chest_diamonds"
                else -> return true
            }

            val preparedStatement =
                connection.sendPreparedStatement("INSERT INTO ${Config.postgresTable}(uuid, $balanceType) VALUES('$uuid', $balance) ON CONFLICT (uuid) DO UPDATE SET $balanceType = excluded.$balanceType")
            preparedStatement.await()
        } catch (e: Exception) {
            return true
        }
        return false
    }

    suspend fun addToPlayerBalance(uuid: UUID, amount: Int, type: BalanceType): Boolean {
        val playerBalance = getPlayerBalanceWrapper(uuid, type) ?: return true

        val error = setPlayerBalance(uuid, playerBalance + amount, type)
        return error
    }

    suspend fun subtractFromPlayerBalance(uuid: UUID, amount: Int, type: BalanceType): Boolean {
        val playerBalance = getPlayerBalanceWrapper(uuid, type) ?: return true

        val error = setPlayerBalance(uuid, playerBalance - amount, type)
        return error
    }

    private suspend fun getPlayerBalanceWrapper(uuid: UUID, type: BalanceType): Int? {
        val playerBalance = getPlayerBalance(uuid, type)

        return when (type) {
            BalanceType.BANK_BALANCE -> playerBalance.bankBalance
            BalanceType.INVENTORY_BALANCE -> playerBalance.inventoryBalance
            BalanceType.ENDER_CHEST_BALANCE -> playerBalance.enderChestBalance
            BalanceType.ALL -> if (playerBalance.bankBalance != null && playerBalance.inventoryBalance != null && playerBalance.enderChestBalance != null) playerBalance.bankBalance + playerBalance.inventoryBalance + playerBalance.enderChestBalance else null
        }
    }

    data class PlayerBalance(val bankBalance: Int?, val inventoryBalance: Int?, val enderChestBalance: Int?)

    suspend fun getPlayerBalance(uuid: UUID, type: BalanceType): PlayerBalance {
        var bankBalance: Int? = null
        var inventoryBalance: Int? = null
        var enderChestBalance: Int? = null
        try {
            val connection = pool.asSuspending.connect()

            val balanceType = when (type) {
                BalanceType.BANK_BALANCE -> "bank_diamonds"
                BalanceType.INVENTORY_BALANCE -> "inventory_diamonds"
                BalanceType.ENDER_CHEST_BALANCE -> "ender_chest_diamonds"
                BalanceType.ALL -> "bank_diamonds, inventory_diamonds, ender_chest_diamonds"
            }

            val preparedStatement =
                connection.sendPreparedStatement("SELECT $balanceType FROM ${Config.postgresTable} WHERE uuid = '$uuid' LIMIT 1")
            val result = preparedStatement.await()

            if (result.rows.size != 0) {
                val rowData = result.rows[0] as ArrayRowData

                when (type) {
                    BalanceType.BANK_BALANCE -> {
                        bankBalance = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                    }

                    BalanceType.INVENTORY_BALANCE -> {
                        inventoryBalance = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                    }

                    BalanceType.ENDER_CHEST_BALANCE -> {
                        enderChestBalance = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                    }

                    BalanceType.ALL -> {
                        bankBalance = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                        inventoryBalance = if (rowData.columns[1] != null) {
                            rowData.columns[1] as Int
                        } else 0
                        enderChestBalance = if (rowData.columns[2] != null) {
                            rowData.columns[2] as Int
                        } else 0
                    }
                }
            } else {
                bankBalance = 0
                inventoryBalance = 0
                enderChestBalance = 0
            }
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.info(e.toString())
        }
        return PlayerBalance(bankBalance, inventoryBalance, enderChestBalance)
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
                val bankBalance = if (rowData.columns[1] != null) {
                    rowData.columns[1] as Int
                } else 0
                val inventoryBalance = if (rowData.columns[2] != null) {
                    rowData.columns[2] as Int
                } else 0
                val enderChestBalance = if (rowData.columns[3] != null) {
                    rowData.columns[3] as Int
                } else 0

                val player = Bukkit.getPlayer(UUID.fromString(rowData.columns[0] as String)) ?: Bukkit.getOfflinePlayer(
                    UUID.fromString(rowData.columns[0] as String)
                )
                baltop[player.name] = bankBalance + inventoryBalance + enderChestBalance
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
