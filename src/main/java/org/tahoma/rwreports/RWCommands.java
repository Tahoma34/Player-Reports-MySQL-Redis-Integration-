package org.tahoma.rwreports;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RWCommands implements CommandExecutor {

    private final RWReports plugin;
    private final RWMain.OnlineStatusProvider onlineStatusProvider;
    private final RWMain main;
    private final FileConfiguration messages;
    private final Map<UUID, Long> reportCooldowns = new ConcurrentHashMap<>();
    private static final long REPORT_COOLDOWN_MS = 15_000;
    private static final int MAX_REASON_LENGTH = 200;
    private static final int MAX_PLAYERNAME_LENGTH = 16;

    public RWCommands(RWReports plugin,
                      RWMain.OnlineStatusProvider onlineStatusProvider,
                      RWMain main,
                      FileConfiguration messages) {
        this.plugin = plugin;
        this.onlineStatusProvider = onlineStatusProvider;
        this.main = main;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("report")) {
            return handleReportCommand(sender, args);
        } else if (command.getName().equalsIgnoreCase("reports")) {
            return handleReportsCommand(sender);
        }
        return false;
    }

    private boolean handleReportCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rwreports.report")) {
            sender.sendMessage(color(messages.getString("report.no-permission")));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(color(messages.getString("report.usage")));
            return true;
        }

        String reportedPlayerName = args[0];
        Player reportedPlayerObj = Bukkit.getPlayerExact(reportedPlayerName);

        if (reportedPlayerObj == null || !reportedPlayerObj.isOnline()) {
            sender.sendMessage(color(messages.getString("report.player-offline")));
            return true;
        }

        String reportedPlayerLower = reportedPlayerName.toLowerCase();
        if (!isValidPlayerName(reportedPlayerLower)) {
            sender.sendMessage(color(messages.getString("report.invalid-player")));
            return true;
        }

        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (reason.length() > MAX_REASON_LENGTH) {
            reason = reason.substring(0, MAX_REASON_LENGTH);
            sender.sendMessage(color(messages.getString("report.reason-truncated")));
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            long currentTime = System.currentTimeMillis();
            Long nextAllowedTime = reportCooldowns.get(player.getUniqueId());

            if (nextAllowedTime != null && currentTime < nextAllowedTime) {
                long secondsLeft = (nextAllowedTime - currentTime) / 1000;
                String cooldownMsg = messages.getString("report.cooldown",
                                "&cПодождите %seconds% сек. перед повторной отправкой репорта.")
                        .replace("%seconds%", String.valueOf(secondsLeft));
                sender.sendMessage(color(cooldownMsg));
                return true;
            }
            reportCooldowns.put(player.getUniqueId(), currentTime + REPORT_COOLDOWN_MS);
        }

        final String finalReason = reason;
        final String finalReportedPlayer = reportedPlayerLower;
        final String reporter = (sender instanceof Player) ? sender.getName() : "CONSOLE";
        final String serverName = plugin.getServerName();

        // сохраняем репорт в базу !!! асинхронно !!!
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = main.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "INSERT INTO reports (reported_player, reporter, reason, server) VALUES (?, ?, ?, ?)")) {

                stmt.setString(1, finalReportedPlayer);
                stmt.setString(2, reporter);
                stmt.setString(3, finalReason);
                stmt.setString(4, serverName);
                stmt.executeUpdate();

                int totalReports = getTotalReports(connection);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sendSuccessMessages(sender, finalReportedPlayer, totalReports);
                });

            } catch (SQLException e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String errorMsg = messages.getString("report.save-error")
                            .replace("%error%", e.getMessage());
                    sender.sendMessage(color(errorMsg));
                });
            }
        });

        return true;
    }

    private int getTotalReports(Connection connection) throws SQLException {
        try (PreparedStatement countStmt = connection.prepareStatement(
                "SELECT COUNT(*) AS total FROM reports");
             ResultSet rs = countStmt.executeQuery()) {
            return rs.next() ? rs.getInt("total") : 0;
        }
    }

    private void sendSuccessMessages(CommandSender sender, String reportedPlayer, int totalReports) {
        String successMsg = messages.getString("report.success")
                .replace("%player%", reportedPlayer);
        sender.sendMessage(color(successMsg));

        String notifyMsg = messages.getString("report.notify-admins")
                .replace("%player%", reportedPlayer)
                .replace("%total%", String.valueOf(totalReports));

        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("rwreports.reports"))
                .forEach(p -> p.sendMessage(color(notifyMsg)));
    }

    private boolean handleReportsCommand(CommandSender sender) {
        if (!sender.hasPermission("rwreports.reports")) {
            sender.sendMessage(color(messages.getString("reports.no-permission")));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(color(messages.getString("reports.only-player")));
            return true;
        }

        Player playerSender = (Player) sender;
        // загружаем данные о репортах в !!! асинхронном !!! потоке
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = main.getConnection()) {
                Map<String, Integer> reportsCount = loadReportsCount(connection);
                Map<String, ReportInfo> lastReportInfo = loadLastReportInfo(connection, reportsCount);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    main.getRwGui().openReportsGui(playerSender, reportsCount, lastReportInfo);
                });

            } catch (SQLException e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String errorMsg = messages.getString("reports.load-error")
                            .replace("%error%", e.getMessage());
                    playerSender.sendMessage(color(errorMsg));
                });
            }
        });

        return true;
    }

    private Map<String, Integer> loadReportsCount(Connection connection) throws SQLException {
        Map<String, Integer> reportsCount = new HashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT LOWER(reported_player) AS reported_player, COUNT(*) AS total FROM reports GROUP BY LOWER(reported_player)");
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                reportsCount.put(rs.getString("reported_player"), rs.getInt("total"));
            }
        }
        return reportsCount;
    }

    private Map<String, ReportInfo> loadLastReportInfo(Connection connection,
                                                       Map<String, Integer> reportsCount) throws SQLException {
        Map<String, ReportInfo> lastReportInfo = new HashMap<>();
        String queryTemplate = "SELECT reporter, reason, server FROM reports " +
                "WHERE LOWER(reported_player) = ? ORDER BY timestamp DESC LIMIT 1";

        for (String target : reportsCount.keySet()) {
            try (PreparedStatement stmt = connection.prepareStatement(queryTemplate)) {
                stmt.setString(1, target);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        lastReportInfo.put(target, new ReportInfo(
                                rs.getString("reporter"),
                                rs.getString("reason"),
                                rs.getString("server")));
                    } else {
                        lastReportInfo.put(target, new ReportInfo("N/A", "—", "—"));
                    }
                }
            }
        }
        return lastReportInfo;
    }

    private boolean isValidPlayerName(String name) {
        return name != null && !name.isEmpty() &&
                name.length() <= MAX_PLAYERNAME_LENGTH &&
                name.matches("^[a-zA-Z0-9_]+$");
    }

    public static class ReportInfo {
        private final String reporter;
        private final String reason;
        private final String server;

        public ReportInfo(String reporter, String reason, String server) {
            this.reporter = reporter;
            this.reason = reason;
            this.server = server;
        }

        public String getReporter() { return reporter; }
        public String getReason() { return reason; }
        public String getServer() { return server; }
    }

    private String color(String msg) {
        return msg != null ? msg.replace("&", "§") : "";
    }
}