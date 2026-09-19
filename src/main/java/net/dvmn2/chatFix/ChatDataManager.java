package net.dvmn2.chatFix;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Хранение и персистентность данных плагина (chatdata.yml).
 * <ul>
 *   <li>{@code players.<uuid>} — данные игроков, заходивших на сервер:
 *       ник, префиксы/постфиксы, индивидуальные настройки чата;</li>
 *   <li>{@code offline-players.<ник>} — данные игроков, которым фиксы выдали
 *       через /chatfix offline, но которые ещё ни разу не заходили. При первом
 *       входе запись переносится в {@code players.<uuid>} (см. {@link #registerName}).</li>
 * </ul>
 * Все операции защищены {@link ReentrantLock}, т.к. вызываются как из основного
 * потока (команды), так и из async (обработка чата, сохранение на диск).
 */
public class ChatDataManager {

    private static final long SAVE_DELAY_TICKS = 20L; // debounce: не пишем файл на каждое изменение
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final String PLAYERS_ROOT = "players";
    private static final String OFFLINE_ROOT = "offline-players";

    /**
     * Снимок данных игрока для одного режима чата. {@code null} в поле значит
     * "не задано":
     * <ul>
     *   <li>prefix/postfix — не задан (для префикса применяется стандартный "Ник: ").
     *       Пустая строка — заданное значение "без префикса";</li>
     *   <li>enabled/radius — нет индивидуального значения, берётся общая настройка.</li>
     * </ul>
     */
    public record ChatProfile(String prefix, String postfix, Boolean enabled, Double radius) {
        public static final ChatProfile EMPTY = new ChatProfile(null, null, null, null);
    }

    private final Map<String, UUID> nameToUuid = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration config;

    private volatile boolean dirty = false;
    private BukkitTask pendingSaveTask;

    public ChatDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chatdata.yml");
        load();
    }

    public static boolean isValidName(String name) {
        return VALID_NAME.matcher(name).matches();
    }

    public void load() {
        lock.lock();
        try {
            if (!file.exists()) {
                plugin.getDataFolder().mkdirs();
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().severe("Не удалось создать chatdata.yml: " + e.getMessage());
                }
            }
            config = YamlConfiguration.loadConfiguration(file);
            loadNameCache();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Планирует отложенное сохранение. Проверка/установка pendingSaveTask
     * идёт под тем же lock'ом, что и flush() — иначе два потока (async-чат
     * и join-событие) могут одновременно запланировать по задаче сохранения.
     */
    private void scheduleSave() {
        lock.lock();
        try {
            dirty = true;
            if (pendingSaveTask != null) {
                return;
            }
            pendingSaveTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::flush, SAVE_DELAY_TICKS);
        } finally {
            lock.unlock();
        }
    }

    public void flush() {
        YamlConfiguration snapshot;
        lock.lock();
        try {
            pendingSaveTask = null;
            if (!dirty) {
                return;
            }
            dirty = false;
            snapshot = YamlConfiguration.loadConfiguration(
                    new StringReader(config.saveToString()));
        } finally {
            lock.unlock();
        }

        try {
            snapshot.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить chatdata.yml: " + e.getMessage());
        }
    }

    /**
     * Используется в onDisable() — форсирует запись, даже если формально
     * ничего не поменялось с последнего сохранения.
     */
    public void forceSaveSync() {
        lock.lock();
        try {
            dirty = true;
        } finally {
            lock.unlock();
        }
        flush();
    }

    private String path(UUID uuid, String key) {
        return PLAYERS_ROOT + "." + uuid + "." + key;
    }

    public String getName(UUID uuid) {
        lock.lock();
        try {
            return config.getString(path(uuid, "name"), "");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Читает все нужные для одного сообщения значения за один захват lock'а.
     */
    public ChatProfile getProfile(UUID uuid, ChatMode mode) {
        lock.lock();
        try {
            ConfigurationSection section = config.getConfigurationSection(PLAYERS_ROOT + "." + uuid);
            if (section == null) {
                return ChatProfile.EMPTY;
            }

            String prefix = section.getString(mode.prefixKey());   // null, если ключа нет
            String postfix = section.getString(mode.postfixKey());

            Boolean enabled = null;
            if (section.getBoolean(mode.customModeKey(), false) && section.contains(mode.enabledKey())) {
                enabled = section.getBoolean(mode.enabledKey());
            }

            Double radius = null;
            if (mode.hasRadius()
                    && section.getBoolean(mode.customRadiusKey(), false)
                    && section.contains(mode.radiusKey())) {
                radius = section.getDouble(mode.radiusKey());
            }

            return new ChatProfile(prefix, postfix, enabled, radius);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Записывает набор ключей в {@code players.<uuid>}.
     */
    public void applyChanges(UUID uuid, Map<String, Object> changes) {
        applyAt(PLAYERS_ROOT + "." + uuid, changes);
    }

    /**
     * Записывает набор ключей в {@code offline-players.<ник>} — для игрока,
     * который ещё ни разу не заходил. Ник сопоставляется без учёта регистра.
     */
    public void applyOfflineChanges(String name, Map<String, Object> changes) {
        lock.lock();
        try {
            String existing = findOfflineKey(name);
            applyAt(OFFLINE_ROOT + "." + (existing != null ? existing : name), changes);
        } finally {
            lock.unlock();
        }
    }

    private void applyAt(String base, Map<String, Object> changes) {
        lock.lock();
        try {
            for (Map.Entry<String, Object> change : changes.entrySet()) {
                config.set(base + "." + change.getKey(), change.getValue());
            }
        } finally {
            lock.unlock();
        }
        scheduleSave();
    }

    /**
     * Регистрирует/обновляет связь UUID <-> ник (вызывается при заходе игрока
     * и при выдаче фикса онлайн-игроку). Если для этого ника есть запись в
     * {@code offline-players} — переносит её в {@code players.<uuid>}.
     * На диск пишет только если что-то реально изменилось.
     */
    public void registerName(UUID uuid, String name) {
        boolean changed;
        lock.lock();
        try {
            changed = migrateOfflineEntry(uuid, name);

            // Убираем устаревшие ники этого UUID (после смены ника), чтобы старый
            // ник не резолвился в игрока и не перекрывал нового владельца ника.
            String lower = name.toLowerCase(Locale.ROOT);
            nameToUuid.entrySet().removeIf(e -> e.getValue().equals(uuid) && !e.getKey().equals(lower));
            nameToUuid.put(lower, uuid);

            if (!name.equals(config.getString(path(uuid, "name")))) {
                config.set(path(uuid, "name"), name);
                changed = true;
            }
        } finally {
            lock.unlock();
        }
        if (changed) {
            scheduleSave();
        }
    }

    /**
     * Переносит {@code offline-players.<ник>} в {@code players.<uuid>}.
     * Значения из offline-записи перезаписывают одноимённые ключи игрока — они
     * были заданы администратором явно. Вызывать под lock'ом.
     *
     * @return {@code true}, если запись была найдена и перенесена
     */
    private boolean migrateOfflineEntry(UUID uuid, String name) {
        ConfigurationSection offline = config.getConfigurationSection(OFFLINE_ROOT);
        if (offline == null) {
            return false;
        }
        String key = findKeyIgnoreCase(offline, name);
        if (key == null) {
            return false;
        }

        ConfigurationSection entry = offline.getConfigurationSection(key);
        if (entry != null) {
            for (Map.Entry<String, Object> value : entry.getValues(false).entrySet()) {
                config.set(path(uuid, value.getKey()), value.getValue());
            }
        }

        offline.set(key, null);
        if (offline.getKeys(false).isEmpty()) {
            config.set(OFFLINE_ROOT, null); // не оставляем пустую секцию в файле
        }

        plugin.getLogger().info("Данные игрока " + name + " перенесены из offline-players в players." + uuid);
        return true;
    }

    private String findOfflineKey(String name) {
        ConfigurationSection offline = config.getConfigurationSection(OFFLINE_ROOT);
        return offline == null ? null : findKeyIgnoreCase(offline, name);
    }

    private static String findKeyIgnoreCase(ConfigurationSection section, String name) {
        for (String key : section.getKeys(false)) {
            if (key.equalsIgnoreCase(name)) {
                return key;
            }
        }
        return null;
    }

    /**
     * Поиск UUID только по локальному кэшу (players в chatdata.yml).
     * {@code null}, если игрок ещё не заходил на сервер.
     */
    public UUID resolveUuid(String name) {
        lock.lock();
        try {
            return nameToUuid.get(name.toLowerCase(Locale.ROOT));
        } finally {
            lock.unlock();
        }
    }

    private void loadNameCache() {
        nameToUuid.clear();

        ConfigurationSection playersSection = config.getConfigurationSection(PLAYERS_ROOT);
        if (playersSection == null) {
            return;
        }

        for (String uuidKey : playersSection.getKeys(false)) {
            String name = playersSection.getString(uuidKey + ".name");
            if (name == null) {
                continue;
            }
            try {
                nameToUuid.put(name.toLowerCase(Locale.ROOT), UUID.fromString(uuidKey));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Некорректный UUID в chatdata.yml: " + uuidKey);
            }
        }
    }
}