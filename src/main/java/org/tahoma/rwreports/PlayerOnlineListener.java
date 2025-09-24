package org.tahoma.rwreports;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Bukkit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class PlayerOnlineListener implements Listener {

    private final JedisPool jedisPool;
    private final String serverName;
    private static final int ONLINE_STATUS_EXPIRY = 300;

    public PlayerOnlineListener(JedisPool jedisPool, String serverName) {
        this.jedisPool = jedisPool;
        this.serverName = serverName;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String playerName = event.getPlayer().getName().toLowerCase();
        String onlineKey = "online:" + playerName;
        String serverKey = "server:" + playerName;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(onlineKey, ONLINE_STATUS_EXPIRY, "1");
            jedis.setex(serverKey, ONLINE_STATUS_EXPIRY, serverName); // Тута текущего сервера
        } catch (Exception e) {
            Bukkit.getLogger().severe("Error Redis in join player: " + e.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName().toLowerCase();
        String onlineKey = "online:" + playerName;
        String serverKey = "server:" + playerName;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(onlineKey);
            jedis.del(serverKey);
        } catch (Exception e) {
            Bukkit.getLogger().severe("Error Redis in quit player: " + e.getMessage());
        }
    }
}
