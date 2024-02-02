package io.github.andromdaa;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
public class PostgreSQL {

    public void initDB() throws SQLException, ClassNotFoundException {

        Class.forName("org.postgresql.Driver");
        Connection db = DriverManager.getConnection(dbURL, dbUser, dbPass);

    }
    // get database credentials
    String dbURL = Plugin.getPlugin().getConfig().getString("config.postgresdbURL");
    String dbUser = Plugin.getPlugin().getConfig().getString("config.postgresdbUser");
    String dbPass = Plugin.getPlugin().getConfig().getString("config.postgresdbUserPassword");

}
