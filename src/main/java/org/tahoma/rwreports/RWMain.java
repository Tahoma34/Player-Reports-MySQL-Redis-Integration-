package org.tahoma.rwreports;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RWMain {

    private final RWReports plugin;
    private final FileConfiguration messagesConfig;
    private OnlineStatusProvider onlineStatusProvider;
    private RWMySQLOnline mysqlStorage;
    private RWCommands commands;
    private HikariDataSource dataSource;
    private RWGui rwGui;

    public RWMain(RWReports plugin, FileConfiguration messagesConfig) {
        this.plugin = plugin;
        this.messagesConfig = messagesConfig;
    }

    public void init() {
        try {
            connectToDatabase();
            setupOnlineProvider();
            registerCommands();
            registerGui();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Ошибка инициализации плагина", e);
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }

    private void setupOnlineProvider() {
        // тип провайдера из конфигурации
        String providerType = plugin.getConfig().getString("status-provider", "mysql").toLowerCase();

        if ("redis".equals(providerType)) {
            setupRedisProvider();
        } else {
            // по умолчанию MySQL
            mysqlStorage = new RWMySQLOnline(this);
            onlineStatusProvider = mysqlStorage;
            getLogger().info("Выбран провайдер MySQL");
            onlineStatusProvider.connectToAPI();
        }
    }

    private void setupRedisProvider() {
        String host = plugin.getConfig().getString("redis.host", "localhost");
        int port = plugin.getConfig().getInt("redis.port", 6379);
        String pass = plugin.getConfig().getString("redis.password", "");
        int db = plugin.getConfig().getInt("redis.database", 0);
        String serverName = plugin.getConfig().getString("server-name", "default-server");

        JedisPool jedisPool;
        if (pass.isEmpty()) {
            jedisPool = new JedisPool(host, port);
        } else {
            jedisPool = new JedisPool(new JedisPoolConfig(), host, port, 2000, pass, db);
        }

        plugin.getServer().getPluginManager().registerEvents(
                new PlayerOnlineListener(jedisPool, serverName), plugin
        );

        RWRedisOnline redisProvider = new RWRedisOnline(host, port, pass, db, plugin);
        onlineStatusProvider = redisProvider;
        getLogger().info("Выбран провайдер Redis");
        onlineStatusProvider.connectToAPI();
    }

    private void registerCommands() {
        commands = new RWCommands(plugin, onlineStatusProvider, this, messagesConfig);
        RWAdminCommands adminCommands = new RWAdminCommands(plugin, this, messagesConfig);

        plugin.getCommand("report").setExecutor(commands);
        plugin.getCommand("reports").setExecutor((sender, cmd, label, args) -> {
            if (args.length > 0 && isAdminCommand(args[0])) {
                return adminCommands.onCommand(sender, cmd, label, args);
            }
            return commands.onCommand(sender, cmd, label, args);
        });
    }

    private boolean isAdminCommand(String arg) {
        String sub = arg.toLowerCase();
        return sub.equals("remove") || sub.equals("removeall") || sub.equals("reload");
    }

    private void registerGui() {
        rwGui = new RWGui(plugin, onlineStatusProvider, messagesConfig, this, commands);
    }

    public void shutdown() {
        try {
            if (onlineStatusProvider != null) {
                onlineStatusProvider.disconnectFromAPI();
            }
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            if (rwGui != null) {
                org.bukkit.event.HandlerList.unregisterAll(rwGui);
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Ошибка при выключении плагина", e);
        }
    }

    public void reloadAll() {
        shutdown();
        plugin.reloadConfig();
        plugin.loadMessagesConfig();
        init();
        getLogger().info("Перезагрузка плагина RWReports выполнена.");
    }

    private void connectToDatabase() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" +
                plugin.getConfig().getString("mysql.host", "localhost") + ":" +
                plugin.getConfig().getInt("mysql.port", 3306) + "/" +
                plugin.getConfig().getString("mysql.database", "minecraft"));

        config.setUsername(plugin.getConfig().getString("mysql.user", "user"));
        config.setPassword(plugin.getConfig().getString("mysql.password", "password"));

        // параметры пула (HikariCP)
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(30000);

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        dataSource = new HikariDataSource(config);

        try (Connection connection = dataSource.getConnection()) {
            createReportsTable(connection);
            getLogger().info("Успешное подключение к БД для репортов!");
        }
    }

    private void createReportsTable(Connection connection) throws SQLException {
        String createQuery = "CREATE TABLE IF NOT EXISTS `reports` ("
                + "`id` INT AUTO_INCREMENT PRIMARY KEY,"
                + "`reported_player` VARCHAR(64) NOT NULL,"
                + "`reporter` VARCHAR(64) NOT NULL,"
                + "`reason` TEXT NOT NULL,"
                + "`server` VARCHAR(64) NOT NULL,"
                + "`timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + "INDEX `reported_player_idx` (`reported_player`),"
                + "INDEX `reporter_idx` (`reporter`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (PreparedStatement stmt = connection.prepareStatement(createQuery)) {
            stmt.executeUpdate();
        }

        String checkColumnSQL = "SHOW COLUMNS FROM `reports` LIKE 'timestamp';";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkColumnSQL);
             ResultSet rs = checkStmt.executeQuery()) {
            if (!rs.next()) {
                String alterQuery = "ALTER TABLE `reports` "
                        + "ADD COLUMN `timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP AFTER `server`;";
                try (PreparedStatement alterStmt = connection.prepareStatement(alterQuery)) {
                    alterStmt.executeUpdate();
                    getLogger().info("Добавлен столбец 'timestamp' в таблицу `reports`.");
                }
            }
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private Logger getLogger() {
        return plugin.getLogger();
    }

    public interface OnlineStatusProvider {
        void connectToAPI();
        void disconnectFromAPI();
        boolean isOnline(String playerName);

        Map<String, Boolean> getOnlineStatus(java.util.Collection<String> playerNames);

        Map<String, String> getServerNames(java.util.Collection<String> playerNames);
    }

    public RWGui getRwGui() {
        return rwGui;
    }
}