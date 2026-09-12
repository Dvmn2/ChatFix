package net.dvmn2.chatFix;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Хранение и персистентность данных плагина (chatdata.yml): префиксы/постфиксы
 * игроков и кэш "ник -> UUID". Все операции защищены {@link ReentrantLock},
 * т.к. вызываются как из основного потока (команды), так и из async
 * (обработка чата, сохранение на диск, обращение к Mojang API).
 */
public class ChatDataManager {

    private static final long SAVE_DELAY_TICKS = 20L; // debounce: не пишем файл на каждое изменение
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Pattern MOJANG_ID = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
    private static final Pattern MOJANG_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

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
     * Регистрирует/обновляет связь UUID <-> ник (вызывается при заходе игрока
     * и при добавлении нового игрока через Mojang API). На диск пишет только
     * если имя реально изменилось.
     */
    public void registerName(UUID uuid, String name) {
        lock.lock();
        try {
            String existing = config.getString(path(uuid, "name"));
            nameToUuid.put(name.toLowerCase(), uuid);
            if (name.equals(existing)) {
                return;
            }
            config.set(path(uuid, "name"), name);
        } finally {
            lock.unlock();
        }
        scheduleSave();
    }

    /**
     * Поиск UUID только по локальному кэшу (chatdata.yml). Ничего не грузит
     * из сети. {@code null}, если игрок ещё не встречался.
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
     * Обращается к Mojang API за UUID игрока, которого нет в chatdata.yml,
     * и сразу регистрирует найденную пару ник/UUID в файле. Выполняет
     * блокирующий HTTP-запрос — вызывать только из async-задачи.
     *
     * @return UUID найденного игрока или {@code null}, если такого ника
     * никогда не существовало / Mojang недоступен.
     */
    public UUID fetchAndRegisterFromMojang(String name) {
        if (!VALID_NAME.matcher(name).matches()) {
            return null;
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/"
                            + URLEncoder.encode(name, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null; // 204/404 — такого ника не существует
            }

            String body = response.body();
            Matcher idMatcher = MOJANG_ID.matcher(body);
            Matcher nameMatcher = MOJANG_NAME.matcher(body);
            if (!idMatcher.find() || !nameMatcher.find()) {
                plugin.getLogger().warning("Неожиданный ответ Mojang API для '" + name + "': " + body);
                return null;
            }

            UUID uuid = parseUndashedUuid(idMatcher.group(1));
            String correctName = nameMatcher.group(1);
            registerName(uuid, correctName);
            return uuid;
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось обратиться к Mojang API: " + e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static UUID parseUndashedUuid(String raw) {
        String dashed = raw.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                "$1-$2-$3-$4-$5");
        return UUID.fromString(dashed);
    }

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
                nameToUuid.put(name.toLowerCase(), UUID.fromString(uuidKey));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Некорректный UUID в chatdata.yml: " + uuidKey);
            }
        }
    }
}