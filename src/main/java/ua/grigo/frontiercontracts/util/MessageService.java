package ua.grigo.frontiercontracts.util;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ua.grigo.frontiercontracts.FrontierContractsPlugin;

public final class MessageService {
    private final FrontierContractsPlugin plugin;
    private FileConfiguration configuration;

    public MessageService(FrontierContractsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String lang = plugin.getConfig().getString("language", "en");
        File langFile = new File(plugin.getDataFolder(), "messages_" + lang + ".yml");
        if (!"en".equals(lang) && langFile.exists()) {
            this.configuration = YamlConfiguration.loadConfiguration(langFile);
        } else {
            File file = new File(plugin.getDataFolder(), "messages.yml");
            this.configuration = YamlConfiguration.loadConfiguration(file);
        }
    }

    public String raw(String path) {
        return configuration.getString(path, path);
    }

    public List<String> rawList(String path) {
        if (configuration.isList(path)) {
            return List.copyOf(configuration.getStringList(path));
        }

        String single = configuration.getString(path);
        if (single == null || single.isBlank()) {
            return List.of();
        }
        return List.of(single);
    }

    public String message(String path) {
        return TextUtil.colorize(prefix() + raw(path));
    }

    public String message(String path, Map<String, String> placeholders) {
        return TextUtil.colorize(prefix() + TextUtil.applyPlaceholders(raw(path), placeholders));
    }

    public String plain(String path, Map<String, String> placeholders) {
        return TextUtil.colorize(TextUtil.applyPlaceholders(raw(path), placeholders));
    }

    public List<String> list(String path) {
        if (configuration.isList(path)) {
            return TextUtil.colorize(configuration.getStringList(path));
        }

        String single = configuration.getString(path);
        if (single == null || single.isBlank()) {
            return List.of();
        }
        return List.of(TextUtil.colorize(single));
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(message(path));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(message(path, placeholders));
    }

    private String prefix() {
        return configuration.getString("prefix", "");
    }
}
