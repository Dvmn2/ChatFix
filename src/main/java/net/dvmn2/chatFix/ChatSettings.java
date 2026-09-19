package net.dvmn2.chatFix;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Изменяемые во время работы общие настройки чата (включены ли режимы,
 * радиусы, уведомления об отключённых режимах). Хранятся в config.yml,
 * чтобы переживать перезапуск сервера; меняются командами /chatfix.
 * Индивидуальные настройки игроков (chatdata.yml) имеют приоритет над этими.
 */
public class ChatSettings {

    private static final String ROOT = "settings.";
    private static final String FEEDBACK_KEY = "feedback-enabled";

    private final JavaPlugin plugin;

    private volatile boolean localChatEnabled;
    private volatile boolean worldChatEnabled;
    private volatile boolean globalChatEnabled;
    private volatile boolean feedbackEnabled;

    private volatile double localRadius;
    private volatile double worldRadius;

    public ChatSettings(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        localChatEnabled = config.getBoolean(ROOT + ChatMode.LOCAL.enabledKey(), true);
        worldChatEnabled = config.getBoolean(ROOT + ChatMode.WORLD.enabledKey(), true);
        globalChatEnabled = config.getBoolean(ROOT + ChatMode.GLOBAL.enabledKey(), true);
        feedbackEnabled = config.getBoolean(ROOT + FEEDBACK_KEY, true);

        localRadius = config.getDouble(ROOT + ChatMode.LOCAL.radiusKey(), 15.0);
        worldRadius = config.getDouble(ROOT + ChatMode.WORLD.radiusKey(), 0.0);
    }

    public boolean isEnabled(ChatMode mode) {
        return switch (mode) {
            case LOCAL -> localChatEnabled;
            case WORLD -> worldChatEnabled;
            case GLOBAL -> globalChatEnabled;
        };
    }

    public void setEnabled(ChatMode mode, boolean enabled) {
        switch (mode) {
            case LOCAL -> localChatEnabled = enabled;
            case WORLD -> worldChatEnabled = enabled;
            case GLOBAL -> globalChatEnabled = enabled;
        }
        save(ROOT + mode.enabledKey(), enabled);
    }

    /**
     * Радиус режима в блоках. Для WORLD значение {@code <= 0} означает
     * "на весь мир". У GLOBAL радиуса нет (возвращается 0).
     */
    public double getRadius(ChatMode mode) {
        return switch (mode) {
            case LOCAL -> localRadius;
            case WORLD -> worldRadius;
            case GLOBAL -> 0.0;
        };
    }

    public void setRadius(ChatMode mode, double radius) {
        switch (mode) {
            case LOCAL -> localRadius = radius;
            case WORLD -> worldRadius = radius;
            case GLOBAL -> {
                return;
            }
        }
        save(ROOT + mode.radiusKey(), radius);
    }

    /**
     * Если включено — игрок, пишущий в отключённый режим, получает уведомление.
     * Если выключено — такое сообщение молча уходит в локальный чат.
     */
    public boolean isFeedbackEnabled() {
        return feedbackEnabled;
    }

    public void setFeedbackEnabled(boolean enabled) {
        feedbackEnabled = enabled;
        save(ROOT + FEEDBACK_KEY, enabled);
    }

    private void save(String path, Object value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
    }
}