package net.dvmn2.chatFix;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Отвечает за хранение и персистентность данных плагина (chatdata.yml):
 * префиксы/постфиксы игроков (локальные и глобальные) и кэш "имя -> UUID"
 * для быстрого разрешения ников в командах.
 * <p>
 * Все операции чтения/записи конфигурации защищены {@link ReentrantLock},
 * т.к. plugin работает в многопоточной среде Bukkit/Paper (события чата
 * могут прилетать асинхронно, а команды и сохранение на диск — из разных
 * потоков планировщика).
 */
public class ChatDataManager {

    // debounce: если правки идут пачкой (например, несколько setPrefix подряд),
    // не пишем файл на каждый вызов, а откладываем на N тиков
    private static final long SAVE_DELAY_TICKS = 20L; // 1 секунда

    /**
     * Кэш "ник в нижнем регистре -> UUID" для быстрого resolve без похода в конфиг.
     */
    private final Map<String, UUID> nameToUuid = new HashMap<>();

    /**
     * Общая блокировка на все операции с {@link #config} и {@link #nameToUuid}.
     */
    private final ReentrantLock lock = new ReentrantLock();

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration config;

    /**
     * true, если в конфиге есть несохранённые изменения.
     */
    private volatile boolean dirty = false;

    /**
     * Задача отложенного сохранения, если она уже запланирована (иначе null).
     */
    private BukkitTask pendingSaveTask;

    public ChatDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chatdata.yml");
        load();
    }

    /**
     * Загружает chatdata.yml с диска (создавая файл/папку плагина при
     * необходимости) и перестраивает кэш имён.
     */
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
     * Планирует отложенное асинхронное сохранение. Если уже запланировано —
     * не создаёт новую задачу, просто выставляет dirty (следующее сохранение
     * заберёт актуальные данные, т.к. пишем снапшот конфига в момент запуска задачи).
     */
    private void scheduleSave() {
        dirty = true;
        if (pendingSaveTask != null) {
            return; // сохранение уже запланировано, ждём его
        }
        pendingSaveTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            flush();
        }, SAVE_DELAY_TICKS);
    }

    /**
     * Физически пишет config на диск. Можно вызывать и синхронно (например, в onDisable),
     * и из async-задачи.
     */
    public void flush() {
        YamlConfiguration snapshot;
        lock.lock();
        try {
            pendingSaveTask = null;
            if (!dirty) {
                return;
            }
            dirty = false;
            // сохраняем снапшот, чтобы writeToFile не держал lock во время I/O
            snapshot = YamlConfiguration.loadConfiguration(
                    new java.io.StringReader(config.saveToString()));
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
     * Синхронный принудительный сброс — использовать в onDisable().
     * Форсирует dirty=true, чтобы flush() гарантированно записал файл,
     * даже если формально ничего не менялось с последнего сохранения.
     */
    public void forceSaveSync() {
        dirty = true;
        flush();
    }

    /**
     * Строит путь в YAML вида players.<uuid>.<key>.
     */
    private String path(UUID uuid, String key) {
        return "players." + uuid + "." + key;
    }

    public String getLocalPrefix(UUID uuid) {
        lock.lock();
        try {
            return config.getString(path(uuid, "local-prefix"), "");
        } finally {
            lock.unlock();
        }
    }

    public String getLocalPostfix(UUID uuid) {
        lock.lock();
        try {
            return config.getString(path(uuid, "local-postfix"), "");
        } finally {
            lock.unlock();
        }
    }

    public String getGlobalPrefix(UUID uuid) {
        lock.lock();
        try {
            return config.getString(path(uuid, "global-prefix"), "");
        } finally {
            lock.unlock();
        }
    }

    public String getGlobalPostfix(UUID uuid) {
        lock.lock();
        try {
            return config.getString(path(uuid, "global-postfix"), "");
        } finally {
            lock.unlock();
        }
    }

    public String getName(UUID uuid) {
        lock.lock();
        try {
            return config.getString(path(uuid, "name"), "");
        } finally {
            lock.unlock();
        }
    }

    public void setLocalPrefix(UUID uuid, String value) {
        lock.lock();
        try {
            config.set(path(uuid, "local-prefix"), value);
        } finally {
            lock.unlock();
        }
        scheduleSave();
    }

    public void setLocalPostfix(UUID uuid, String value) {
        lock.lock();
        try {
            config.set(path(uuid, "local-postfix"), value);
        } finally {
            lock.unlock();
        }
        scheduleSave();
    }

    public void setGlobalPrefix(UUID uuid, String value) {
        lock.lock();
        try {
            config.set(path(uuid, "global-prefix"), value);
        } finally {
            lock.unlock();
        }
        scheduleSave();
    }

    public void setGlobalPostfix(UUID uuid, String value) {
        lock.lock();
        try {
            config.set(path(uuid, "global-postfix"), value);
        } finally {
            lock.unlock();
        }
        scheduleSave();
    }

    /**
     * Регистрирует/обновляет связь UUID <-> ник (вызывается при заходе игрока).
     * Обновляет кэш всегда (на случай, если сервер перезапущен и ник у того же
     * UUID сменился), но пишет на диск только если имя реально изменилось —
     * чтобы не дёргать сохранение на каждый вход одного и того же игрока.
     */
    public void registerName(UUID uuid, String name) {
        lock.lock();
        try {
            String existing = config.getString(path(uuid, "name"));
            if (name.equals(existing)) {
                // ничего не поменялось — не дёргаем сохранение зря
                nameToUuid.put(name.toLowerCase(), uuid);
                return;
            }
            nameToUuid.put(name.toLowerCase(), uuid);
            config.set(path(uuid, "name"), name);
        } finally {
            lock.unlock();
        }
        scheduleSave();
    }

    /**
     * Пытается разрешить ник в UUID по кэшу (регистр не важен).
     *
     * @return UUID игрока или {@code null}, если ник не встречался
     * (например, игрок ни разу не заходил на сервер с момента
     * последней перезагрузки кэша).
     */
    public UUID resolveUuid(String name) {
        lock.lock();
        try {
            return nameToUuid.get(name.toLowerCase());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Перестраивает {@link #nameToUuid} из секции players конфига.
     */
    private void loadNameCache() {
        nameToUuid.clear();

        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }

        for (String uuidKey : playersSection.getKeys(false)) {
            String name = playersSection.getString(uuidKey + ".name");
            if (name == null) {
                continue;
            }
            try {
                UUID uuid = UUID.fromString(uuidKey);
                nameToUuid.put(name.toLowerCase(), uuid);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Некорректный UUID в chatdata.yml: " + uuidKey);
            }
        }
    }
}