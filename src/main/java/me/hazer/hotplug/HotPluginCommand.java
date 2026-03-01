package me.hazer.hotplug;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Обработчик /hotplugins.
 */
public final class HotPluginCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final PluginWatcher watcher;
    private final PluginLoader loader;
    private final PluginUnloader unloader;
    private final PluginRegistry registry;
    private final GUIManager guiManager;

    public HotPluginCommand(Main plugin,
                            PluginWatcher watcher,
                            PluginLoader loader,
                            PluginUnloader unloader,
                            PluginRegistry registry,
                            GUIManager guiManager) {
        this.plugin = plugin;
        this.watcher = watcher;
        this.loader = loader;
        this.unloader = unloader;
        this.registry = registry;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("hazer.hotplug.use")) {
            sender.sendMessage("§cНет прав.");
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                boolean opened = guiManager.open(player);
                if (!opened) {
                    sender.sendMessage("§eGUI отключен в config.yml.");
                }
            } else {
                sender.sendMessage("§eИспользование: /hotplugins <reload|load|unload|list|scan>");
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "reload" -> handleReload(sender);
            case "load" -> handleLoad(sender, args);
            case "unload" -> handleUnload(sender, args);
            case "list" -> handleList(sender);
            case "scan" -> handleScan(sender);
            default -> {
                sender.sendMessage("§cНеизвестная подкоманда.");
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        registry.syncLoadedFromServer();
        int scanned = watcher.scanNow();
        int loaded = loader.loadPendingPlugins();
        sender.sendMessage("§aReload завершен. §7Найдено: §f" + scanned + "§7, загружено pending: §f" + loaded);
        return true;
    }

    private boolean handleLoad(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eИспользование: /hotplugins load <plugin>");
            return true;
        }

        String name = args[1];
        boolean ok = loader.loadByName(name);
        sender.sendMessage(ok ? "§aЗагружено: §f" + name : "§cОшибка загрузки: §f" + name);
        return true;
    }

    private boolean handleUnload(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eИспользование: /hotplugins unload <plugin>");
            return true;
        }

        String name = args[1];
        boolean ok = unloader.unload(name);
        sender.sendMessage(ok ? "§eВыгружено: §f" + name : "§cОшибка выгрузки: §f" + name);
        return true;
    }

    private boolean handleList(CommandSender sender) {
        registry.syncLoadedFromServer();

        List<String> loaded = registry.namesByState(PluginRegistry.PluginState.LOADED);
        List<String> pending = registry.namesByState(PluginRegistry.PluginState.PENDING);
        List<String> disabled = new ArrayList<>();
        disabled.addAll(registry.namesByState(PluginRegistry.PluginState.DISABLED));
        disabled.addAll(registry.namesByState(PluginRegistry.PluginState.ERROR));
        disabled.addAll(registry.namesByState(PluginRegistry.PluginState.MISSING));

        sender.sendMessage("§8========== §6HazerHotPlug §8==========");
        sender.sendMessage("§aLoaded (§f" + loaded.size() + "§a): §f" + join(loaded));
        sender.sendMessage("§ePending (§f" + pending.size() + "§e): §f" + join(pending));
        sender.sendMessage("§cDisabled/Error/Missing (§f" + disabled.size() + "§c): §f" + join(disabled));
        return true;
    }

    private boolean handleScan(CommandSender sender) {
        int scanned = watcher.scanNow();
        sender.sendMessage("§aСканирование завершено. Добавлено pending: §f" + scanned);
        return true;
    }

    private String join(List<String> names) {
        if (names.isEmpty()) {
            return "-";
        }
        return String.join(", ", names);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("hazer.hotplug.use")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return partial(args[0], Arrays.asList("reload", "load", "unload", "list", "scan"));
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("load") || args[0].equalsIgnoreCase("unload"))) {
            List<String> all = new ArrayList<>();
            Arrays.stream(Bukkit.getPluginManager().getPlugins()).forEach(p -> all.add(p.getName()));
            registry.snapshot().forEach(rec -> {
                if (!all.contains(rec.getPluginName())) {
                    all.add(rec.getPluginName());
                }
            });
            return partial(args[1], all);
        }

        return Collections.emptyList();
    }

    private List<String> partial(String token, List<String> source) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : source) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(value);
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }
}
