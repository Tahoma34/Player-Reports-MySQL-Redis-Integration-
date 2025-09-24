package org.tahoma.rwreports;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RwGuiAdditional implements Listener {

    private final RWReports plugin;
    private final RWMain main;
    private final FileConfiguration messages;
    private final FileConfiguration config;

    // хранение последнего сервера и цели для каждого игрока
    private final Map<UUID, String> lastServers = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastTargets = new ConcurrentHashMap<>();

    // отслеживание игроков, выполняющих операции
    private final Set<UUID> processingPlayers = ConcurrentHashMap.newKeySet();

    private static final int GUI_SIZE = 45;

    public RwGuiAdditional(RWReports plugin, RWMain main, FileConfiguration messages) {
        this.plugin = plugin;
        this.main = main;
        this.messages = messages;
        this.config = plugin.getConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openAdditionalGui(Player viewer, String targetName) {
        // асинхронная загрузка из бд
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<ReportRecord> lastReports = loadLastSevenReports(targetName);

            String lastServerFromReports = lastReports.isEmpty()
                    ? "error"
                    : lastReports.get(0).server;
            lastServers.put(viewer.getUniqueId(), lastServerFromReports);

            lastTargets.put(viewer.getUniqueId(), targetName);

            Bukkit.getScheduler().runTask(plugin, () -> {
                String titleTemplate = messages.getString("reports.gui.additional.title",
                        "&0Последние 7 репортов на &6%player%");
                String guiTitle = color(titleTemplate.replace("%player%", targetName));

                Inventory gui = Bukkit.createInventory(null, GUI_SIZE, guiTitle);

                ItemStack filler = createFillerItem(Material.ORANGE_STAINED_GLASS_PANE, "&7 ");
                for (int i = 0; i < GUI_SIZE; i++) {
                    gui.setItem(i, filler);
                }

                String itemNameTemplate = messages.getString("reports.gui.additional.item-name",
                        "&eРепорт #%index%");
                List<String> loreTemplate = messages.getStringList("reports.gui.additional.item-lore");
                if (loreTemplate.isEmpty()) {
                    loreTemplate = Arrays.asList(
                            "&7От: &f%reporter%",
                            "&7Причина: &f%reason%",
                            "&7Сервер: &f%server%",
                            "&7Время: &f%timestamp%"
                    );
                }

                for (int i = 0; i < Math.min(lastReports.size(), 7); i++) {
                    ReportRecord record = lastReports.get(i);

                    String itemName = color(itemNameTemplate.replace("%index%", String.valueOf(i + 1)));

                    List<String> itemLore = new ArrayList<>();
                    for (String line : loreTemplate) {
                        line = line.replace("%reporter%", record.reporter)
                                .replace("%reason%", record.reason)
                                .replace("%server%", record.server)
                                .replace("%timestamp%", record.timestamp);
                        itemLore.add(color(line));
                    }

                    ItemStack reportItem = createReportItem(Material.PAPER, itemName, itemLore);
                    gui.setItem(10 + i, reportItem);
                }

                String moveButtonName = "&a[Перемещение] &7(" + lastServerFromReports + ")";
                List<String> moveButtonLore = messages.getStringList("reports.gui.additional.move-button.lore");
                if (moveButtonLore.isEmpty()) {
                    moveButtonLore = Collections.singletonList("&7Нажмите, чтобы перейти");
                }
                ItemStack moveButton = createButtonItem(Material.ENDER_PEARL, moveButtonName, moveButtonLore);
                gui.setItem(31, moveButton);

                String rewardButtonName = "&6[Вознаграждение]";
                List<String> rewardButtonLore = Collections.singletonList("&7Нажмите, чтобы выдать награду");
                ItemStack rewardButton = createButtonItem(Material.EMERALD, rewardButtonName, rewardButtonLore);
                gui.setItem(30, rewardButton);

                String deleteButtonName = "&c[Удаление]";
                List<String> deleteLore = Collections.singletonList("&7Нажмите, чтобы удалить репорт");
                ItemStack deleteButton = createButtonItem(Material.BARRIER, deleteButtonName, deleteLore);
                gui.setItem(32, deleteButton);

                viewer.openInventory(gui);
            });
        });
    }

    private List<ReportRecord> loadLastSevenReports(String targetName) {
        List<ReportRecord> list = new ArrayList<>();
        String sqlQuery = "SELECT reporter, reason, server, timestamp FROM reports " +
                "WHERE reported_player = ? ORDER BY id DESC LIMIT 7";
        try (Connection conn = main.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlQuery)) {

            stmt.setString(1, targetName);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ReportRecord record = new ReportRecord(
                            rs.getString("reporter"),
                            rs.getString("reason"),
                            rs.getString("server"),
                            rs.getString("timestamp")
                    );
                    list.add(record);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при загрузке последних репортов: " + e.getMessage());
        }
        return list;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle() == null) {
            return;
        }

        String configuredTitleTemplate = messages.getString("reports.gui.additional.title",
                "&0Последние 7 репортов на &6%player%");
        String expectedPrefix = ChatColor.stripColor(color(configuredTitleTemplate.replace("%player%", "")));
        String viewTitleStripped = ChatColor.stripColor(event.getView().getTitle());

        if (viewTitleStripped.startsWith(expectedPrefix)) {

            if (event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getView().getTopInventory())) {

                event.setCancelled(true);

                Player clicker = (Player) event.getWhoClicked();
                UUID playerId = clicker.getUniqueId();

                if (event.getSlot() == 31) {
                    handleMoveButtonClick(clicker, playerId);
                }

                if (event.getSlot() == 30) {
                    handleRewardButtonClick(clicker, playerId);
                }

                if (event.getSlot() == 32) {
                    handleDeleteButtonClick(clicker, playerId);
                }
            }
        }
    }

    private void handleMoveButtonClick(Player clicker, UUID playerId) {
        if (eventClickIsValid(clicker, playerId)) {
            try {
                String lastServer = lastServers.get(playerId);
                if (lastServer != null && !lastServer.isEmpty()) {
                    clicker.performCommand("server " + lastServer);
                } else {
                    clicker.sendMessage(color("&cНе удалось определить сервер."));
                }
            } finally {
                processingPlayers.remove(playerId);
            }
        }
    }

    private void handleRewardButtonClick(Player clicker, UUID playerId) {
        if (!processingPlayers.add(playerId)) {
            clicker.sendMessage(color("&cЗагрузка..."));
            return;
        }

        String targetName = lastTargets.get(playerId);
        if (targetName == null || targetName.isEmpty()) {
            clicker.sendMessage(color("&cНе удалось определить игрока для награды."));
            processingPlayers.remove(playerId);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String reporter = getFirstReporter(targetName);
            if (reporter == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    clicker.sendMessage(color("&cНе найден репортер для данного игрока."));
                    processingPlayers.remove(playerId);
                });
                return;
            }

            double rewardAmount = config.getDouble("reward-amount", 100);

            // награда и удаление репортов в !!! основном !!! потоке
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    String command = String.format("eco give %s %.2f", reporter, rewardAmount);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                    plugin.getLogger().info(String.format("Модератор %s выдал вознаграждение игроку %s", clicker.getName(), reporter));

                    String rewardMessageTemplate = messages.getString("reports.gui.additional.reward-issued", "&aНаграда успешно выдана игроку %reporter%");
                    String rewardMessage = rewardMessageTemplate.replace("%reporter%", reporter);
                    clicker.sendMessage(color(rewardMessage));

                    deleteReportsFromDatabase(clicker, targetName);
                } finally {
                    processingPlayers.remove(playerId);
                }
            });
        });
    }

    private void handleDeleteButtonClick(Player clicker, UUID playerId) {
        if (!processingPlayers.add(playerId)) {
            clicker.sendMessage(color("&cЗагрузка..."));
            return;
        }

        String targetName = lastTargets.get(playerId);
        if (targetName == null || targetName.isEmpty()) {
            clicker.sendMessage(color("&cНе удалось определить игрока для удаления репортов."));
            processingPlayers.remove(playerId);
            return;
        }

        deleteReportsFromDatabase(clicker, targetName);
    }

    private boolean eventClickIsValid(Player clicker, UUID playerId) {
        if (!processingPlayers.add(playerId)) {
            clicker.sendMessage(color("&cЗагрузка..."));
            return false;
        }
        return true;
    }

    private String getFirstReporter(String targetName) {
        String reporter = null;
        String sqlQuery = "SELECT reporter FROM reports WHERE reported_player = ? ORDER BY id DESC LIMIT 1";
        try (Connection conn = main.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlQuery)) {

            stmt.setString(1, targetName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    reporter = rs.getString("reporter");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении первого репортера: " + e.getMessage());
        }
        return reporter;
    }

    private void deleteReportsFromDatabase(Player clicker, String targetName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sqlDelete = "DELETE FROM reports WHERE reported_player = ?";
            try (Connection connection = main.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sqlDelete)) {

                stmt.setString(1, targetName);
                int deletedCount = stmt.executeUpdate();

                plugin.getLogger().info("Игрок " + clicker.getName()
                        + " удалил " + deletedCount + " репорт(ов) нарушителя " + targetName);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (deletedCount > 0) {
                        clicker.sendMessage(color("&aУдалено репортов: " + deletedCount));
                    } else {
                        clicker.sendMessage(color("&cРепортов не найдено."));
                    }
                    clicker.performCommand("reports");
                });

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка при удалении репортов для " + targetName + ": " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    clicker.sendMessage(color("&cОшибка при удалении репортов: " + e.getMessage()));
                });
            }
        });
    }

    private String color(String input) {
        return (input != null) ? ChatColor.translateAlternateColorCodes('&', input) : "";
    }

    private ItemStack createFillerItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createReportItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createButtonItem(Material material, String name, List<String> loreTemplate) {
        List<String> coloredLore = new ArrayList<>();
        for (String line : loreTemplate) {
            coloredLore.add(color(line));
        }
        return createReportItem(material, color(name), coloredLore);
    }

    private static class ReportRecord {
        private final String reporter;
        private final String reason;
        private final String server;
        private final String timestamp;

        public ReportRecord(String reporter, String reason, String server, String timestamp) {
            this.reporter = reporter;
            this.reason = reason;
            this.server = server;
            this.timestamp = timestamp;
        }
    }
}