package io.github.andromdaa;

import java.sql.*;
import java.util.UUID;

public class PostgreSQL {
    Connection db = null;
    // get database credentials
    String dbURL = Plugin.getPlugin().getConfig().getString("config.postgresdbURL");
    String dbUser = Plugin.getPlugin().getConfig().getString("config.postgresdbUser");
    String dbPass = Plugin.getPlugin().getConfig().getString("config.postgresdbUserPassword");
    String dbTable = Plugin.getPlugin().getConfig().getString("config.postgresTable");

    public void initDB() throws SQLException, ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
        this.db = DriverManager.getConnection(dbURL, dbUser, dbPass);
    }

    public void setPlayerBalance(UUID uuid, double balance) {
        try {
            PreparedStatement preparedStatement = this.db.prepareStatement(String.format("INSERT INTO %s(uuid, balance) VALUES(?,?) ON CONFLICT (uuid) DO UPDATE SET balance = excluded.balance;", dbTable));
            preparedStatement.setString(1, uuid.toString());
            preparedStatement.setDouble(2, balance);
            preparedStatement.executeUpdate();
            preparedStatement.close();
        } catch (SQLException exception) {
            Plugin.getPlugin().getLogger().info(exception.toString());
        }
    }

    public void depositToPlayerBalance(UUID uuid, double amount) {
        double playerBalance = getPlayerBalance(uuid);
        setPlayerBalance(uuid, playerBalance+amount);
    }

    public void withdrawFromPlayerBalance(UUID uuid, double amount) {
        double playerBalance = getPlayerBalance(uuid);
        setPlayerBalance(uuid, playerBalance-amount);
        //return playerBalance-amount;
    }

    public double getPlayerBalance(UUID uuid) {
        double balance = -1;
        try {
            PreparedStatement preparedStatement = this.db.prepareStatement(String.format("SELECT * FROM %s WHERE uuid = ?", dbTable));
            preparedStatement.setString(1, uuid.toString());
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                balance = resultSet.getDouble(2);
            }
            resultSet.close();
            preparedStatement.close();
        } catch (SQLException exception) {
            Plugin.getPlugin().getLogger().info(exception.toString());
        }
        return balance;
    }
}
