package me.hazer.hotplug;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GUI список плагинов и действий по клику.
 *
 * <p>Цвета:
 * <ul>
 *   <li>Зеленый — LOADED</li>
 *   <li>Желтый — PENDING</li>
 *   <li>Красный — DISABLED/ERROR/MISSING</li>
 * </ul>
 * ЛКМ: load/reload, ПКМ: unload.</p>
 */
public final class PluginInventoryGUI implements Listener {

    private final Main plugin;
    private final PluginRegistry registry;
    private final PluginLoader loader;
    private final PluginUnloader unloader;
    private final LoggerUtil logger;

    private final String title = "§8HazerHotPlug - Plugins";
    private final Map<String, String> slotPluginMap = new HashMap<>();

    public PluginInventoryGUI(Main plugin,
                              PluginRegistry registry,
                              PluginLoader loader,
                              PluginUnloader unloader,
                              LoggerUtil logger) {
        this.plugin = plugin;
        this.registry = registry;
        this.loader = loader;
        this.unloader = unloader;
        this.logger = logger;
    }

    /**
     * Открывает GUI игроку.
     */
    public void open(Player player) {
        List<PluginRegistry.PluginRecord> records = registry.snapshot();
        int size = Math.max(9, ((records.size() / 9) + 1) * 9);
        size = Math.min(size, 54);

        Inventory inv = Bukkit.createInventory(null, size, title);
        slotPluginMap.clear();

        for (int i = 0; i < records.size() && i < size; i++) {
            PluginRegistry.PluginRecord record = records.get(i);
            ItemStack item = createPluginItem(record);
            inv.setItem(i, item);
            slotPluginMap.put(slotKey(inv, i), record.getPluginName());
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player)) {
            return;
        }

        if (event.getView().title() == null) {
            return;
        }

        if (!event.getView().getTitle().equals(title)) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        String pluginName = slotPluginMap.get(slotKey(event.getInventory(), slot));
        if (pluginName == null) {
            return;
        }

        if (event.isLeftClick()) {
            handleLeftClick(player, pluginName);
        } else if (event.isRightClick()) {
            handleRightClick(player, pluginName);
        }

        Bukkit.getScheduler().runTask(plugin, () -> open(player));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(title)) {
            slotPluginMap.clear();
        }
    }

    /**
     * ЛКМ: если pending/disabled -> load, если loaded -> reload (unload+load).
     */
    private void handleLeftClick(Player player, String pluginName) {
        PluginRegistry.PluginRecord record = registry.find(pluginName).orElse(null);
        if (record == null) {
            player.sendMessage("§cПлагин не найден в реестре: " + pluginName);
            return;
        }

        switch (record.getState()) {
            case PENDING, DISABLED, ERROR, MISSING -> {
                boolean ok = loader.loadByName(pluginName);
                player.sendMessage(ok
                        ? "§aПлагин загружен: §f" + pluginName
                        : "§cНе удалось загрузить: §f" + pluginName);
            }
            case LOADED -> {
                boolean unload = unloader.unload(pluginName);
                boolean load = unload && loader.loadByName(pluginName);
                player.sendMessage(load
                        ? "§aПлагин перезагружен: §f" + pluginName
                        : "§cНе удалось перезагрузить: §f" + pluginName);
            }
        }
    }

    /**
     * ПКМ: попытка выгрузки.
     */
    private void handleRightClick(Player player, String pluginName) {
        boolean ok = unloader.unload(pluginName);
        player.sendMessage(ok
                ? "§eПлагин выгружен: §f" + pluginName
                : "§cНе удалось выгрузить: §f" + pluginName);
    }

    /**
     * Создание item по статусу.
     */
    private ItemStack createPluginItem(PluginRegistry.PluginRecord record) {
        Material material;
        String stateColor;

        if (record.getState() == PluginRegistry.PluginState.LOADED) {
            material = Material.LIME_CONCRETE;
            stateColor = "§aLOADED";
        } else if (record.getState() == PluginRegistry.PluginState.PENDING) {
            material = Material.YELLOW_CONCRETE;
            stateColor = "§ePENDING";
        } else {
            material = Material.RED_CONCRETE;
            stateColor = "§c" + record.getState().name();
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName("§f" + record.getPluginName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Статус: " + stateColor);
        lore.add("§7Причина: §f" + nullSafe(record.getReason()));
        lore.add("§8----------------");
        lore.add("§aЛКМ §7- load/reload");
        lore.add("§cПКМ §7- unload");

        String path = record.getJarPath() != null ? record.getJarPath().toString() : "unknown";
        lore.add("§8" + trimPath(path));

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private String slotKey(Inventory inventory, int slot) {
        return Integer.toHexString(System.identityHashCode(inventory)) + ":" + slot;
    }

    private String nullSafe(String value) {
        return value == null ? "n/a" : value;
    }

    private String trimPath(String path) {
        if (path == null) {
            return "unknown";
        }

        String normalized = path.replace('\\', '/');
        if (normalized.length() <= 40) {
            return normalized;
        }

        return "..." + normalized.substring(normalized.length() - 37).toLowerCase(Locale.ROOT);
    }
}
