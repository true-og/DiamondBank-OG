package net.trueog.diamondbankog

import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.general.ArrayRowData
import com.github.jasync.sql.db.pool.ConnectionPool
import com.github.jasync.sql.db.postgresql.PostgreSQLConnection
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import kotlinx.coroutines.future.await
import org.bukkit.Bukkit
import java.math.BigDecimal
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
                PostgreSQLConnectionBuilder.createConnectionPool("${Config.getPostgresUrl()}?user=${Config.getPostgresUser()}&password=${Config.getPostgresPassword()}")
            val createTable =
                pool.sendPreparedStatement("CREATE TABLE IF NOT EXISTS ${Config.getPostgresTable()}(uuid TEXT, bank_balance decimal, inventory_balance decimal, ender_chest_balance decimal, unique(uuid))")
            createTable.join()
            val createIndex =
                pool.sendPreparedStatement("CREATE INDEX IF NOT EXISTS idx_balance ON ${Config.getPostgresTable()}(bank_balance, inventory_balance, ender_chest_balance)")
            createIndex.join()
        } catch (e: Exception) {
            DiamondBankOG.economyDisabled = true
            DiamondBankOG.plugin.logger.severe("ECONOMY DISABLED! Something went wrong while trying to initialise PostgreSQL. Is PostgreSQL running? Are the PostgreSQL config variables correct?")
            return
        }
    }

    suspend fun setPlayerBalance(uuid: UUID, balance: Long, type: BalanceType): Boolean {
        try {
            val connection = pool.asSuspending.connect()

            val balanceType = when (type) {
                BalanceType.BANK_BALANCE -> "bank_balance"
                BalanceType.INVENTORY_BALANCE -> "inventory_balance"
                BalanceType.ENDER_CHEST_BALANCE -> "ender_chest_balance"
                else -> return true
            }

            val preparedStatement =
                connection.sendPreparedStatement("INSERT INTO ${Config.getPostgresTable()}(uuid, $balanceType) VALUES('$uuid', $balance) ON CONFLICT (uuid) DO UPDATE SET $balanceType = excluded.$balanceType")
            preparedStatement.await()
        } catch (e: Exception) {
            return true
        }
        return false
    }

    suspend fun addToPlayerBalance(uuid: UUID, amount: Long, type: BalanceType): Boolean {
        val playerBalance = getPlayerBalanceWrapper(uuid, type) ?: return true

        val error = setPlayerBalance(uuid, playerBalance + amount, type)
        return error
    }

    suspend fun subtractFromPlayerBalance(uuid: UUID, amount: Long, type: BalanceType): Boolean {
        val playerBalance = getPlayerBalanceWrapper(uuid, type) ?: return true

        val error = setPlayerBalance(uuid, playerBalance - amount, type)
        return error
    }

    private suspend fun getPlayerBalanceWrapper(uuid: UUID, type: BalanceType): Long? {
        val playerBalance = getPlayerBalance(uuid, type)

        when (type) {
            BalanceType.BANK_BALANCE -> if (playerBalance.bankBalance == null) return null
            BalanceType.INVENTORY_BALANCE -> if (playerBalance.inventoryBalance == null) return null
            BalanceType.ENDER_CHEST_BALANCE -> if (playerBalance.enderChestBalance == null) return null
            else -> return null
        }

        return when (type) {
            BalanceType.BANK_BALANCE -> playerBalance.bankBalance
            BalanceType.INVENTORY_BALANCE -> playerBalance.inventoryBalance
            BalanceType.ENDER_CHEST_BALANCE -> playerBalance.enderChestBalance

            // Shouldn't be called anyway
            else -> 0
        }
    }

    data class PlayerBalance(val bankBalance: Long?, val inventoryBalance: Long?, val enderChestBalance: Long?)

    suspend fun getPlayerBalance(uuid: UUID, type: BalanceType): PlayerBalance {
        var bankBalance: Long? = null
        var inventoryBalance: Long? = null
        var enderChestBalance: Long? = null
        try {
            val connection = pool.asSuspending.connect()

            val balanceType = when (type) {
                BalanceType.BANK_BALANCE -> "bank_balance"
                BalanceType.INVENTORY_BALANCE -> "inventory_balance"
                BalanceType.ENDER_CHEST_BALANCE -> "ender_chest_balance"
                BalanceType.ALL -> "bank_balance, inventory_balance, ender_chest_balance"
            }

            val preparedStatement =
                connection.sendPreparedStatement("SELECT $balanceType FROM ${Config.getPostgresTable()} WHERE uuid = '$uuid' LIMIT 1")
            val result = preparedStatement.await()

            if (result.rows.size != 0) {
                val rowData = result.rows[0] as ArrayRowData

                when (type) {
                    BalanceType.BANK_BALANCE -> {
                        bankBalance = if (rowData.columns[0] != null) {
                            (rowData.columns[0] as BigDecimal).toLong()
                        } else 0L
                    }

                    BalanceType.INVENTORY_BALANCE -> {
                        inventoryBalance = if (rowData.columns[0] != null) {
                            (rowData.columns[0] as BigDecimal).toLong()
                        } else 0L
                    }

                    BalanceType.ENDER_CHEST_BALANCE -> {
                        enderChestBalance = if (rowData.columns[0] != null) {
                            (rowData.columns[0] as BigDecimal).toLong()
                        } else 0L
                    }

                    BalanceType.ALL -> {
                        bankBalance = if (rowData.columns[0] != null) {
                            (rowData.columns[0] as BigDecimal).toLong()
                        } else 0L
                        inventoryBalance = if (rowData.columns[1] != null) {
                            (rowData.columns[1] as BigDecimal).toLong()
                        } else 0L
                        enderChestBalance = if (rowData.columns[2] != null) {
                            (rowData.columns[2] as BigDecimal).toLong()
                        } else 0L
                    }
                }
            } else {
                bankBalance = 0L
                inventoryBalance = 0L
                enderChestBalance = 0L
            }
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.info(e.toString())
        }
        return PlayerBalance(bankBalance, inventoryBalance, enderChestBalance)
    }

    suspend fun getBaltop(offset: Int): MutableMap<String?, Long>? {
        try {
            val connection = pool.asSuspending.connect()
            val preparedStatement =
                connection.sendPreparedStatement("SELECT * FROM ${Config.getPostgresTable()} ORDER BY bank_balance DESC, inventory_balance DESC, ender_chest_balance DESC OFFSET $offset LIMIT 10")
            val result = preparedStatement.await()
            val baltop = mutableMapOf<String?, Long>()
            result.rows.forEach {
                val rowData = it as ArrayRowData
                val bankBalance = if (rowData.columns[1] != null) {
                    (rowData.columns[1] as BigDecimal).toLong()
                } else 0L
                val inventoryBalance = if (rowData.columns[2] != null) {
                    (rowData.columns[2] as BigDecimal).toLong()
                } else 0L
                val enderChestBalance = if (rowData.columns[3] != null) {
                    (rowData.columns[3] as BigDecimal).toLong()
                } else 0L

                baltop[Bukkit.getOfflinePlayer(UUID.fromString(rowData.columns[0] as String)).name] =
                    bankBalance + inventoryBalance + enderChestBalance
            }
            return baltop
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.info(e.toString())
        }
        return null
    }

    suspend fun getNumberOfRows(): Long? {
        var number: Long? = null
        try {
            val connection = pool.asSuspending.connect()
            val preparedStatement =
                connection.sendPreparedStatement("SELECT count(*) AS exact_count FROM ${Config.getPostgresTable()}")
            val result = preparedStatement.await()

            if (result.rows.size != 0) {
                val rowData = result.rows[0] as ArrayRowData
                number = if (rowData.columns[0] != null) {
                    rowData.columns[0] as Long
                } else 0L
            }
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.info(e.toString())
        }
        return number
    }
}
