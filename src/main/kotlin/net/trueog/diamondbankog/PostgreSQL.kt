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

    @Throws(SQLException::class, ClassNotFoundException::class)
    fun initDB() {
        pool =
            PostgreSQLConnectionBuilder.createConnectionPool("${Config.getPostgresdbUrl()}?user=${Config.getPostgresdbUser()}&password=${Config.getPostgresdbPassword()}")
        val createTable =
            pool.sendPreparedStatement("CREATE TABLE IF NOT EXISTS ${Config.getPostgresTable()}(uuid TEXT, balance decimal, unique(uuid))")
        createTable.join()
        val createIndex =
            pool.sendPreparedStatement("CREATE INDEX IF NOT EXISTS idx_balance ON ${Config.getPostgresTable()}(balance)")
        createIndex.join()
    }

    suspend fun setPlayerBalance(uuid: UUID, balance: Long): Boolean {
        try {
            val connection = pool.asSuspending.connect()
            val preparedStatement =
                connection.sendPreparedStatement("INSERT INTO ${Config.getPostgresTable()}(uuid, balance) VALUES('$uuid',$balance) ON CONFLICT (uuid) DO UPDATE SET balance = excluded.balance")
            preparedStatement.await()
        } catch (exception: SQLException) {
            DiamondBankOG.plugin.logger.info(exception.toString())
            return true
        }
        return false
    }

    suspend fun depositToPlayerBalance(uuid: UUID, amount: Long): Boolean {
        val playerBalance = getPlayerBalance(uuid)
        if (playerBalance == -1L) return true

        val error = setPlayerBalance(uuid, playerBalance + amount)
        return error
    }

    suspend fun getPlayerBalance(uuid: UUID): Long {
        var balance = -1L
        try {
            val connection = pool.asSuspending.connect()
            val preparedStatement =
                connection.sendPreparedStatement("SELECT balance FROM ${Config.getPostgresTable()} WHERE uuid = '$uuid' LIMIT 1")
            val result = preparedStatement.await()
            balance = if (result.rows.size != 0) {
                ((result.rows[0] as ArrayRowData).columns[0] as BigDecimal).toLong()
            } else 0L
        } catch (exception: SQLException) {
            DiamondBankOG.plugin.logger.info(exception.toString())
        }
        return balance
    }

    suspend fun getBaltop(offset: Int): MutableMap<String?, Long>? {
        try {
            val connection = pool.asSuspending.connect()
            val preparedStatement =
                connection.sendPreparedStatement("SELECT * FROM ${Config.getPostgresTable()} ORDER BY balance DESC OFFSET $offset LIMIT 10")
            val result = preparedStatement.await()
            val baltop = mutableMapOf<String?, Long>()
            result.rows.forEach {
                val rowData = it as ArrayRowData
                baltop[Bukkit.getOfflinePlayer(UUID.fromString(rowData.columns[0] as String)).name] = (rowData.columns[1] as BigDecimal).toLong()
            }
            return baltop
        } catch (exception: SQLException) {
            DiamondBankOG.plugin.logger.info(exception.toString())
        }
        return null
    }

    suspend fun getNumberOfRows(): Long {
        var number = -1L
        try {
            val connection = pool.asSuspending.connect()
            val preparedStatement =
                connection.sendPreparedStatement("SELECT count(*) AS exact_count FROM ${Config.getPostgresTable()}")
            val result = preparedStatement.await()
            number = if (result.rows.size != 0) {
                (result.rows[0] as ArrayRowData).columns[0] as Long
            } else 0L
        } catch (exception: SQLException) {
            DiamondBankOG.plugin.logger.info(exception.toString())
        }
        return number
    }
}
