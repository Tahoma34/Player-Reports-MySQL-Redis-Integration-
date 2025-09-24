package org.tahoma.rwreports;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class RWGui implements Listener {

    private final RWReports plugin;
    private final RWMain.OnlineStatusProvider onlineStatusProvider;
    private final FileConfiguration messages;
    private final RWMain main;
    private final RWCommands commands;

    private final RwGuiAdditional rwGuiAdditional; // Добавлено

    private static final int GUI_SIZE = 54;
    private static final int MAX_PLAYERS = 28;
    private static final List<Integer> FIXED_LAYOUT = Arrays.asList(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );
    private static final int[] ORANGE_PANES_SLOTS = {
            0, 1, 9,
            7, 8, 17,
            36, 45, 46,
            44, 52, 53
    };

    private final Set<UUID> clickCooldowns = new HashSet<>();

    public RWGui(RWReports plugin,
                 RWMain.OnlineStatusProvider onlineStatusProvider,
                 FileConfiguration messages,
                 RWMain main,
                 RWCommands commands) {
        this.plugin = plugin;
        this.onlineStatusProvider = onlineStatusProvider;
        this.messages = messages;
        this.main = main;
        this.commands = commands;

        this.rwGuiAdditional = new RwGuiAdditional(plugin, main, messages);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openReportsGui(Player playerSender,
                               Map<String, Integer> reportsCount,
                               Map<String, RWCommands.ReportInfo> lastReportInfo) {

        List<Map.Entry<String, Integer>> sortedByReports = new ArrayList<>(reportsCount.entrySet());
        sortedByReports.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        if (sortedByReports.size() > MAX_PLAYERS) {
            sortedByReports = sortedByReports.subList(0, MAX_PLAYERS);
        }

        // все нарушители, получаем их онлайн-статусы и имена серверов
        Set<String> allPlayers = new HashSet<>();
        for (Map.Entry<String, Integer> entry : sortedByReports) {
            allPlayers.add(entry.getKey());
        }

        Map<String, Boolean> onlineStatuses = onlineStatusProvider.getOnlineStatus(allPlayers);
        // на каком сервере сейчас игрок
        Map<String, String> currentServers = onlineStatusProvider.getServerNames(allPlayers);

        String guiTitle = color(messages.getString("reports.gui.title", "&0Активные репорты"));
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, guiTitle);

        ItemStack orangePane = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        if (orangePane.getItemMeta() != null) {
            orangePane.getItemMeta().setDisplayName(color("&6 "));
            orangePane.setItemMeta(orangePane.getItemMeta());
        }
        for (int slot : ORANGE_PANES_SLOTS) {
            gui.setItem(slot, orangePane);
        }

        String nameTemplate = messages.getString("reports.gui.item-format.name", "&fЖалоба на: &6%player%");
        List<String> loreTemplate = messages.getStringList("reports.gui.item-format.lore");
        if (loreTemplate.isEmpty()) {
            loreTemplate = Arrays.asList(
                    "&7Всего репортов: &e%count%",
                    "&7Последний репорт от: &e%reporter%",
                    "&7Причина: &e%reason%",
                    "&7Сервер (репорта): &e%server%",
                    "&7Текущий сервер: &e%current_server%",
                    "&7Статус: %status%"
            );
        }

        int index = 0;
        for (Map.Entry<String, Integer> entry : sortedByReports) {
            if (index >= FIXED_LAYOUT.size()) break;

            String targetName = entry.getKey();
            int count = entry.getValue();

            boolean isOnline = onlineStatuses.getOrDefault(targetName.toLowerCase(), false);
            String playerServer = currentServers.getOrDefault(targetName.toLowerCase(), "—");

            RWCommands.ReportInfo reportInfo = lastReportInfo.getOrDefault(
                    targetName.toLowerCase(),
                    new RWCommands.ReportInfo(
                            messages.getString("reports.not-found", "N/A"),
                            messages.getString("reports.no-reason", "—"),
                            messages.getString("reports.no-server", "—"))
            );

            ItemStack headItem = new ItemStack(Material.PLAYER_HEAD, 1);
            SkullMeta skullMeta = (SkullMeta) headItem.getItemMeta();
            if (skullMeta != null) {
                PersistentDataContainer data = skullMeta.getPersistentDataContainer();
                NamespacedKey key = new NamespacedKey(plugin, "reportedPlayer");
                data.set(key, PersistentDataType.STRING, targetName.toLowerCase());

                String displayName = color(nameTemplate.replace("%player%", targetName));
                List<String> finalLore = new ArrayList<>();
                for (String line : loreTemplate) {
                    line = line
                            .replace("%player%", targetName)
                            .replace("%count%", String.valueOf(count))
                            .replace("%reporter%", reportInfo.getReporter())
                            .replace("%reason%", reportInfo.getReason())
                            .replace("%server%", reportInfo.getServer())
                            .replace("%current_server%", playerServer)
                            .replace("%status%", isOnline
                                    ? color(messages.getString("reports.status-online", "&aОнлайн"))
                                    : color(messages.getString("reports.status-offline", "&cОффлайн"))
                            );
                    finalLore.add(color(line));
                }

                skullMeta.setDisplayName(displayName);
                skullMeta.setLore(finalLore);
                headItem.setItemMeta(skullMeta);
            }

            gui.setItem(FIXED_LAYOUT.get(index), headItem);
            index++;
        }

        playerSender.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String guiTitle = color(messages.getString("reports.gui.title", "&0Активные репорты"));
        if (!ChatColor.stripColor(event.getView().getTitle())
                .equals(ChatColor.stripColor(guiTitle))) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player clicker = (Player) event.getWhoClicked();

        if (clickCooldowns.contains(clicker.getUniqueId())) {
            return;
        }
        clickCooldowns.add(clicker.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                clickCooldowns.remove(clicker.getUniqueId()), 2L);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() != Material.PLAYER_HEAD) {
            return;
        }

        boolean isRightClick = event.isRightClick();

        String targetName = extractTargetNameFromSkull(clickedItem);
        if (targetName == null || targetName.isEmpty()) {
            targetName = extractTargetNameFromDisplay(clickedItem);
        }

        if (targetName == null || targetName.isEmpty()) {
            clicker.sendMessage(color("&cPlayer not found. " +
                    "Please use: /reports remove <nick>"));
            return;
        }

        if (isRightClick) {
            rwGuiAdditional.openAdditionalGui(clicker, targetName);
            return;
        }

    }

    private String extractTargetNameFromSkull(ItemStack item) {
        if (!(item.getItemMeta() instanceof SkullMeta)) {
            return null;
        }
        SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
        if (skullMeta == null) {
            return null;
        }

        PersistentDataContainer data = skullMeta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "reportedPlayer");
        String storedName = data.get(key, PersistentDataType.STRING);

        // на случай, если почему-то не сохранилось:
        if (storedName != null && !storedName.isEmpty()) {
            return storedName;
        }

        OfflinePlayer offlineOwner = skullMeta.getOwningPlayer();
        if (offlineOwner != null && offlineOwner.getName() != null) {
            return offlineOwner.getName().toLowerCase();
        }
        return null;
    }

    private String extractTargetNameFromDisplay(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return null;
        }
        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        String prefix = ChatColor.stripColor(
                messages.getString("reports.gui.item-format.name", "Жалоба на: %player%")
                        .split("%player%")[0]
        ).trim();

        if (prefix.isEmpty()) {
            return displayName.toLowerCase();
        }
        if (displayName.startsWith(prefix)) {
            return displayName.substring(prefix.length()).trim().toLowerCase();
        }
        return null;
    }


    private String color(String input) {
        return input != null ? ChatColor.translateAlternateColorCodes('&', input) : "";
    }
}