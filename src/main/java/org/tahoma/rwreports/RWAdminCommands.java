package org.tahoma.rwreports;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class RWAdminCommands implements CommandExecutor {

    private final RWReports plugin;
    private final RWMain main;
    private final FileConfiguration messages;

    public RWAdminCommands(RWReports plugin, RWMain main, FileConfiguration messages) {
        this.plugin = plugin;
        this.main = main;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rwreports.admin")) {
            sender.sendMessage(color(messages.getString("admin.no-permission")));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(color(messages.getString("admin.usage")));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "remove":
                if (!sender.hasPermission("rwreports.admin.remove")) {
                    sender.sendMessage(color(messages.getString("admin.remove.no-permission")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(color(messages.getString("admin.remove-usage")));
                    return true;
                }
                removeReportsForPlayer(sender, args[1]);
                return true;

            case "removeall":
                if (!sender.hasPermission("rwreports.admin.removeall")) {
                    sender.sendMessage(color(messages.getString("admin.removeall.no-permission")));
                    return true;
                }
                removeAllReports(sender);
                return true;

            case "reload":
                if (!sender.hasPermission("rwreports.admin.reload")) {
                    sender.sendMessage(color(messages.getString("admin.reload.no-permission")));
                    return true;
                }
                reloadPlugin(sender);
                return true;

            default:
                return false;
        }
    }

    private void removeReportsForPlayer(CommandSender sender, String playerName) {
        String sql = "DELETE FROM reports WHERE reported_player = ?";

        try (Connection connection = main.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, playerName);
            int deleted = stmt.executeUpdate();

            String successMsg = messages.getString("admin.remove-success")
                    .replace("%player%", playerName)
                    .replace("%count%", String.valueOf(deleted));
            sender.sendMessage(color(successMsg));

        } catch (SQLException e) {
            String errorMsg = messages.getString("admin.remove-error")
                    .replace("%error%", e.getMessage());
            sender.sendMessage(color(errorMsg));
        }
    }

    private void removeAllReports(CommandSender sender) {
        String sql = "DELETE FROM reports";

        try (Connection connection = main.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            int deleted = stmt.executeUpdate();

            String successMsg = messages.getString("admin.removeall-success")
                    .replace("%count%", String.valueOf(deleted));
            sender.sendMessage(color(successMsg));

        } catch (SQLException e) {
            String errorMsg = messages.getString("admin.removeall-error")
                    .replace("%error%", e.getMessage());
            sender.sendMessage(color(errorMsg));
        }
    }

    private void reloadPlugin(CommandSender sender) {
        plugin.getLogger().info(messages.getString("admin.reload-log"));
        main.reloadAll();
        sender.sendMessage(color(messages.getString("admin.reload-success")));
    }

    private String color(String msg) {
        return msg != null ? msg.replace("&", "§") : "";
    }
}