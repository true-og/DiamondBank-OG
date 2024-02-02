import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.sql.*;
import io.github.andromdaa.Plugin;
public class PostgreSQL {
  Class.forName("org.postgresql.Driver");
  // get database credentials
  String dbURL = (String) Plugin.getPlugin().getConfig().getString("config.postgresdbURL");
  String dbUser = (String) Plugin.getPlugin().getConfig().getString("config.postgresdbUser");
  String dbPass = (String) Plugin.getPlugin().getConfig().getString("config.postgresdbUserPassword");
  // make a database connection
  Connection db = DriverManager.getConnection(dbURL, dbUser, dbPass);
}
