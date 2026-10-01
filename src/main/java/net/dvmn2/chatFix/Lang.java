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
        INVALID_MODE,
        INVALID_PARAM,
        INVALID_ENABLED_VALUE,
        INVALID_RADIUS,
        NO_RADIUS_FOR_MODE,
        INVALID_PLAYER_NAME,
        INVALID_TOGGLE_STATE,
        FIX_SET,
        PARAM_SET,
        OFFLINE_PENDING,
        RADIUS_SET,
        WORLD_RADIUS_SET,
        LOCAL_CHAT_ENABLED,
        LOCAL_CHAT_DISABLED,
        WORLD_CHAT_ENABLED,
        WORLD_CHAT_DISABLED,
        GLOBAL_CHAT_ENABLED,
        GLOBAL_CHAT_DISABLED,
        FEEDBACK_ENABLED,
        FEEDBACK_DISABLED,
        LOCAL_CHAT_IS_DISABLED,
        WORLD_CHAT_IS_DISABLED,
        GLOBAL_CHAT_IS_DISABLED,
        TARGET_RESOLVE_FAILED,
        EMPTY_HAND,
        INV_LABEL,
        INV_HOVER,
        INV_TITLE
    }

    private static final Map<Key, String> RU = new EnumMap<>(Key.class);
    private static final Map<Key, String> EN = new EnumMap<>(Key.class);

    static {
        RU.put(Key.INVALID_MODE, "§cРежим должен быть local, world или global.");
        RU.put(Key.INVALID_PARAM, "§cПараметр должен быть prefix, postfix, enabled или radius.");
        RU.put(Key.INVALID_ENABLED_VALUE, "§cДля enabled допустимы значения true, false или default.");
        RU.put(Key.INVALID_RADIUS, "§cРадиус должен быть числом не меньше %.1f или default.");
        RU.put(Key.NO_RADIUS_FOR_MODE, "§cУ глобального чата нет радиуса.");
        RU.put(Key.INVALID_PLAYER_NAME, "§cНекорректный ник: допустимы латинские буквы, цифры и _ (до 16 символов).");
        RU.put(Key.INVALID_TOGGLE_STATE, "§cЗначение должно быть on или off.");
        RU.put(Key.FIX_SET, "§aФикс (%s) для %s установлен: %s");
        RU.put(Key.PARAM_SET, "§aПараметр (%s) для %s установлен: %s");
        RU.put(Key.OFFLINE_PENDING, "§7Игрок ещё не заходил на сервер — настройки применятся при его первом входе.");
        RU.put(Key.RADIUS_SET, "§aРадиус локального чата установлен: %.1f");
        RU.put(Key.WORLD_RADIUS_SET, "§aРадиус мирового чата установлен: %.1f (0 — весь мир)");
        RU.put(Key.LOCAL_CHAT_ENABLED, "§aЛокальный чат включён.");
        RU.put(Key.LOCAL_CHAT_DISABLED, "§aЛокальный чат выключен.");
        RU.put(Key.WORLD_CHAT_ENABLED, "§aМировой чат включён.");
        RU.put(Key.WORLD_CHAT_DISABLED, "§aМировой чат выключен.");
        RU.put(Key.GLOBAL_CHAT_ENABLED, "§aГлобальный чат включён.");
        RU.put(Key.GLOBAL_CHAT_DISABLED, "§aГлобальный чат выключен.");
        RU.put(Key.FEEDBACK_ENABLED, "§aУведомления об отключённых режимах чата включены.");
        RU.put(Key.FEEDBACK_DISABLED, "§aУведомления об отключённых режимах чата выключены: такие сообщения уходят в локальный чат.");
        RU.put(Key.LOCAL_CHAT_IS_DISABLED, "§cЛокальный чат сейчас отключён администрацией.");
        RU.put(Key.WORLD_CHAT_IS_DISABLED, "§cМировой чат сейчас отключён администрацией.");
        RU.put(Key.GLOBAL_CHAT_IS_DISABLED, "§cГлобальный чат сейчас отключён администрацией.");
        RU.put(Key.TARGET_RESOLVE_FAILED, "§cНе удалось разрешить указанного игрока.");
        RU.put(Key.EMPTY_HAND, "§7[Пустая рука]");
        RU.put(Key.INV_LABEL, "%s's inv");
        RU.put(Key.INV_HOVER, "§7Нажмите, чтобы посмотреть инвентарь");
        RU.put(Key.INV_TITLE, "Инвентарь %s");

        EN.put(Key.INVALID_MODE, "§cMode must be local, world or global.");
        EN.put(Key.INVALID_PARAM, "§cParameter must be prefix, postfix, enabled or radius.");
        EN.put(Key.INVALID_ENABLED_VALUE, "§cFor enabled the value must be true, false or default.");
        EN.put(Key.INVALID_RADIUS, "§cRadius must be a number not less than %.1f, or default.");
        EN.put(Key.NO_RADIUS_FOR_MODE, "§cGlobal chat has no radius.");
        EN.put(Key.INVALID_PLAYER_NAME, "§cInvalid name: only Latin letters, digits and _ are allowed (up to 16 characters).");
        EN.put(Key.INVALID_TOGGLE_STATE, "§cValue must be on or off.");
        EN.put(Key.FIX_SET, "§aFix (%s) for %s set to: %s");
        EN.put(Key.PARAM_SET, "§aParameter (%s) for %s set to: %s");
        EN.put(Key.OFFLINE_PENDING, "§7The player has not joined the server yet — settings will apply on their first join.");
        EN.put(Key.RADIUS_SET, "§aLocal chat radius set to: %.1f");
        EN.put(Key.WORLD_RADIUS_SET, "§aWorld chat radius set to: %.1f (0 = whole world)");
        EN.put(Key.LOCAL_CHAT_ENABLED, "§aLocal chat enabled.");
        EN.put(Key.LOCAL_CHAT_DISABLED, "§aLocal chat disabled.");
        EN.put(Key.WORLD_CHAT_ENABLED, "§aWorld chat enabled.");
        EN.put(Key.WORLD_CHAT_DISABLED, "§aWorld chat disabled.");
        EN.put(Key.GLOBAL_CHAT_ENABLED, "§aGlobal chat enabled.");
        EN.put(Key.GLOBAL_CHAT_DISABLED, "§aGlobal chat disabled.");
        EN.put(Key.FEEDBACK_ENABLED, "§aNotices about disabled chat modes enabled.");
        EN.put(Key.FEEDBACK_DISABLED, "§aNotices about disabled chat modes disabled: such messages go to local chat.");
        EN.put(Key.LOCAL_CHAT_IS_DISABLED, "§cLocal chat is currently disabled by the administration.");
        EN.put(Key.WORLD_CHAT_IS_DISABLED, "§cWorld chat is currently disabled by the administration.");
        EN.put(Key.GLOBAL_CHAT_IS_DISABLED, "§cGlobal chat is currently disabled by the administration.");
        EN.put(Key.TARGET_RESOLVE_FAILED, "§cCould not resolve the specified player.");
        EN.put(Key.EMPTY_HAND, "§7[Empty hand]");
        EN.put(Key.INV_LABEL, "%s's inv");
        EN.put(Key.INV_HOVER, "§7Click to view the inventory");
        EN.put(Key.INV_TITLE, "%s's inventory");
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