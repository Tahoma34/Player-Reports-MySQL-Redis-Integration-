package org.tahoma.rwreports;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class RWReports extends JavaPlugin {

    private RWMain rwMain;
    private String serverName;
    private FileConfiguration messagesConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        serverName = getConfig().getString("server-name", "default-server");
        loadMessagesConfig();
        rwMain = new RWMain(this, messagesConfig);
        rwMain.init();
        getLogger().info("Плагин RWReports включён.");
    }

    @Override
    public void onDisable() {
        if (rwMain != null) {
            rwMain.shutdown();
            org.bukkit.event.HandlerList.unregisterAll(rwMain.getRwGui());
        }
        getLogger().info("Плагин RWReports отключён.");
    }

    public String getServerName() {
        return serverName;
    }


    public void loadMessagesConfig() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }
}