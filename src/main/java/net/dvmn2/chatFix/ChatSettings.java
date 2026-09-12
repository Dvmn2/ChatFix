package net.dvmn2.chatFix;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Изменяемые во время работы настройки чата (радиус локального чата,
 * включён ли глобальный чат). Хранятся в config.yml, чтобы переживать
 * перезапуск сервера; меняются командой /chatfix radius|globalchat.
 */
public class ChatSettings {

    private final JavaPlugin plugin;

    private volatile double localRadiusSquared;
    private volatile boolean globalChatEnabled;

    public ChatSettings(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        double radius = config.getDouble("settings.local-chat-radius", 15.0);
        localRadiusSquared = radius * radius;
        globalChatEnabled = config.getBoolean("settings.global-chat-enabled", true);
    }

    /**
     * Квадрат радиуса — сравнивается с distanceSquared() без лишнего sqrt().
     */
    public double getLocalRadiusSquared() {
        return localRadiusSquared;
    }

    public void setLocalRadius(double radius) {
        localRadiusSquared = radius * radius;
        plugin.getConfig().set("settings.local-chat-radius", radius);
        plugin.saveConfig();
    }

    public boolean isGlobalChatEnabled() {
        return globalChatEnabled;
    }

    public void setGlobalChatEnabled(boolean enabled) {
        globalChatEnabled = enabled;
        plugin.getConfig().set("settings.global-chat-enabled", enabled);
        plugin.saveConfig();
    }
}