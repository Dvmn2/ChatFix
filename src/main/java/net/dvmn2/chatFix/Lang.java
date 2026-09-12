package net.dvmn2.chatFix;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * RU/EN локализация плагина. Язык задаётся в config.yml (settings.language):
 * "ru" / "en" — фиксированный язык; "auto" (по умолчанию) — берётся из
 * клиентской локали игрока (Player#locale()), для не-игроков — английский.
 */
public final class Lang {

    public enum Key {
        PLAYER_NOT_FOUND,
        INVALID_MODE,
        INVALID_FIX,
        INVALID_TOGGLE_STATE,
        FIX_SET,
        RADIUS_SET,
        GLOBAL_CHAT_ENABLED,
        GLOBAL_CHAT_DISABLED,
        GLOBAL_CHAT_IS_DISABLED,
        TARGET_RESOLVE_FAILED
    }

    private static final Map<Key, String> RU = new EnumMap<>(Key.class);
    private static final Map<Key, String> EN = new EnumMap<>(Key.class);

    static {
        RU.put(Key.PLAYER_NOT_FOUND, "§cИгрок «%s» не найден.");
        RU.put(Key.INVALID_MODE, "§cРежим должен быть local или global.");
        RU.put(Key.INVALID_FIX, "§cТип фикса должен быть prefix или postfix.");
        RU.put(Key.INVALID_TOGGLE_STATE, "§cЗначение должно быть on или off.");
        RU.put(Key.FIX_SET, "§aФикс (%s) для %s установлен: %s");
        RU.put(Key.RADIUS_SET, "§aРадиус локального чата установлен: %.1f");
        RU.put(Key.GLOBAL_CHAT_ENABLED, "§aГлобальный чат включён.");
        RU.put(Key.GLOBAL_CHAT_DISABLED, "§aГлобальный чат выключен.");
        RU.put(Key.GLOBAL_CHAT_IS_DISABLED, "§cГлобальный чат сейчас отключён администрацией.");
        RU.put(Key.TARGET_RESOLVE_FAILED, "§cНе удалось разрешить указанного игрока.");

        EN.put(Key.PLAYER_NOT_FOUND, "§cPlayer \"%s\" not found).");
        EN.put(Key.INVALID_MODE, "§cMode must be local or global.");
        EN.put(Key.INVALID_FIX, "§cFix type must be prefix or postfix.");
        EN.put(Key.INVALID_TOGGLE_STATE, "§cValue must be on or off.");
        EN.put(Key.FIX_SET, "§aFix (%s) for %s set to: %s");
        EN.put(Key.RADIUS_SET, "§aLocal chat radius set to: %.1f");
        EN.put(Key.GLOBAL_CHAT_ENABLED, "§aGlobal chat enabled.");
        EN.put(Key.GLOBAL_CHAT_DISABLED, "§aGlobal chat disabled.");
        EN.put(Key.GLOBAL_CHAT_IS_DISABLED, "§cGlobal chat is currently disabled by the administration.");
        EN.put(Key.TARGET_RESOLVE_FAILED, "§cCould not resolve the specified player.");
    }

    private static volatile String configuredLanguage = "auto";

    private Lang() {
    }

    public static void setLanguage(String language) {
        configuredLanguage = (language == null || language.isBlank())
                ? "auto"
                : language.toLowerCase(Locale.ROOT);
    }

    public static String get(Key key, CommandSender sender, Object... args) {
        Map<Key, String> table = resolveTable(sender);
        String template = table.getOrDefault(key, RU.get(key));
        return args.length == 0 ? template : String.format(Locale.US, template, args);
    }

    private static Map<Key, String> resolveTable(CommandSender sender) {
        return switch (configuredLanguage) {
            case "ru" -> RU;
            case "en" -> EN;
            default -> autoResolve(sender);
        };
    }

    private static Map<Key, String> autoResolve(CommandSender sender) {
        if (sender instanceof Player player) {
            return "ru".equalsIgnoreCase(player.locale().getLanguage()) ? RU : EN;
        }
        return EN;
    }
}