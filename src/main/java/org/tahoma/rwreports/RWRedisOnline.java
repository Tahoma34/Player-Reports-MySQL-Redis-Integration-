package org.tahoma.rwreports;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RWRedisOnline implements RWMain.OnlineStatusProvider {

    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private JedisPool jedisPool;
    private boolean connected = false;
    private final Plugin plugin;

    public RWRedisOnline(String host, int port, String password, int database, Plugin plugin) {
        this.host = host;
        this.port = port;
        this.password = (password != null) ? password : "";
        this.database = database;
        this.plugin = plugin;
    }

    @Override
    public void connectToAPI() {
        if (connected) return;

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(16);
        poolConfig.setMaxIdle(8);
        poolConfig.setMinIdle(2);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinEvictableIdleTimeMillis(TimeUnit.SECONDS.toMillis(30));
        poolConfig.setTimeBetweenEvictionRunsMillis(TimeUnit.SECONDS.toMillis(10));

        try {
            if (password.isEmpty()) {
                jedisPool = new JedisPool(poolConfig, host, port, 2000, null, database);
            } else {
                jedisPool = new JedisPool(poolConfig, host, port, 2000, password, database);
            }

            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
                connected = true;
                plugin.getLogger().info("Успешно подключено к Redis.");
            }
        } catch (JedisException e) {
            connected = false;
            plugin.getLogger().severe("Ошибка подключения к Redis: " + e.getMessage());
            throw new RuntimeException("Ошибка подключения к Redis", e);
        }
    }

    @Override
    public void disconnectFromAPI() {
        connected = false;
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            plugin.getLogger().info("Отключено от Redis.");
        }
    }

    @Override
    public boolean isOnline(String playerName) {
        if (!connected || playerName == null || playerName.isEmpty()) {
            return false;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "online:" + playerName.toLowerCase();
            String status = jedis.get(key);
            return "1".equals(status);
        } catch (JedisException e) {
            handleRedisException(e);
            return false;
        }
    }

    @Override
    public Map<String, Boolean> getOnlineStatus(Collection<String> playerNames) {
        Map<String, Boolean> statuses = new HashMap<>();
        if (!connected) {
            return statuses;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            for (String name : playerNames) {
                if (name == null || name.isEmpty()) {
                    continue;
                }
                String key = "online:" + name.toLowerCase();
                String status = jedis.get(key);

                statuses.put(name.toLowerCase(), "1".equals(status));
            }
        } catch (JedisException e) {
            handleRedisException(e);
        }
        return statuses;
    }

    @Override
    public Map<String, String> getServerNames(Collection<String> playerNames) {
        Map<String, String> serverMap = new HashMap<>();
        if (!connected) {
            return serverMap;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            for (String name : playerNames) {
                if (name == null || name.isEmpty()) {
                    continue;
                }
                String serverKey = "server:" + name.toLowerCase();
                String serverName = jedis.get(serverKey);

                serverMap.put(name.toLowerCase(), (serverName != null) ? serverName : "—");
            }
        } catch (JedisException e) {
            handleRedisException(e);
        }
        return serverMap;
    }

    private void handleRedisException(JedisException e) {
        connected = false;
        plugin.getLogger().severe("Ошибка Redis: " + e.getMessage());
        try {
            if (jedisPool != null && !jedisPool.isClosed()) {
                jedisPool.close();
            }
        } catch (Exception ignored) {}
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                connectToAPI();
            } catch (Exception ex) {
                plugin.getLogger().severe("Не удалось переподключиться к Redis: " + ex.getMessage());
            }
        }, 200L);
    }

    public JedisPool getJedisPool() {
        return jedisPool;
    }
}