package net.dvmn2.chatFix;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.dvmn2.chatFix.ChatDataManager.ChatProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Перехватывает и переформатирует чат игроков. Режим определяется началом сообщения:
 * <ul>
 *   <li>без префикса — локальный чат (игроки того же мира в радиусе);</li>
 *   <li>"!" — мировой чат (игроки мира отправителя; радиус 0 = весь мир);</li>
 *   <li>"!!" — глобальный чат (все онлайн-игроки).</li>
 * </ul>
 * Включён ли режим и какой у него радиус — берётся из индивидуальных настроек
 * отправителя (chatdata.yml), а если их нет — из общих (config.yml).
 * Если выбранный режим отключён: при feedback-enabled игрок получает уведомление,
 * иначе сообщение целиком (вместе с "!"/"!!") уходит в локальный чат.
 * Фрагменты вида {текст} видит только отправитель и игроки с правом
 * chatmanager.seehidden, остальные видят сообщение без них.
 */
public class ChatListener implements Listener {

    private static final Pattern HIDDEN_PATTERN = Pattern.compile("\\{([^{}]*)\\}");

    private static final String PERMISSION_SEE = "chatmanager.seehidden";
    private static final String GLOBAL_TRIGGER = "!!";
    private static final String WORLD_TRIGGER = "!";

    private final ChatDataManager dataManager;
    private final ChatSettings settings;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public ChatListener(ChatDataManager dataManager, ChatSettings settings) {
        this.dataManager = dataManager;
        this.settings = settings;
    }

    // HIGH — чтобы отработать после других плагинов, которые могут менять/отменять сообщение
    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) {
            return;
        }
        event.setCancelled(true); // полностью берём обработку сообщения на себя

        Player sender = event.getPlayer();
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (rawMessage.isEmpty()) {
            return;
        }

        ChatMode mode;
        String text;
        if (rawMessage.startsWith(GLOBAL_TRIGGER)) {
            mode = ChatMode.GLOBAL;
            text = rawMessage.substring(GLOBAL_TRIGGER.length());
        } else if (rawMessage.startsWith(WORLD_TRIGGER)) {
            mode = ChatMode.WORLD;
            text = rawMessage.substring(WORLD_TRIGGER.length());
        } else {
            mode = ChatMode.LOCAL;
            text = rawMessage;
        }
        if (text.isEmpty()) {
            return;
        }

        UUID uuid = sender.getUniqueId();
        ChatProfile profile = dataManager.getProfile(uuid, mode);

        if (!isEnabled(mode, profile)) {
            if (settings.isFeedbackEnabled()) {
                sender.sendMessage(Lang.get(disabledKey(mode), sender));
                return;
            }
            // Без уведомлений: сообщение трактуется как локальное, "!"/"!!" остаются в тексте.
            if (mode == ChatMode.LOCAL) {
                return; // откатываться некуда — молча не отправляем
            }
            mode = ChatMode.LOCAL;
            text = rawMessage;
            profile = dataManager.getProfile(uuid, mode);
            if (!isEnabled(mode, profile)) {
                return;
            }
        }

        deliver(sender, mode, text, profile);
    }

    private boolean isEnabled(ChatMode mode, ChatProfile profile) {
        return profile.enabled() != null ? profile.enabled() : settings.isEnabled(mode);
    }

    private double radiusOf(ChatMode mode, ChatProfile profile) {
        return profile.radius() != null ? profile.radius() : settings.getRadius(mode);
    }

    private static Lang.Key disabledKey(ChatMode mode) {
        return switch (mode) {
            case LOCAL -> Lang.Key.LOCAL_CHAT_IS_DISABLED;
            case WORLD -> Lang.Key.WORLD_CHAT_IS_DISABLED;
            case GLOBAL -> Lang.Key.GLOBAL_CHAT_IS_DISABLED;
        };
    }

    /**
     * Форматирует сообщение и рассылает получателям режима. Отправитель и
     * админы видят версию со скрытыми фрагментами.
     */
    private void deliver(Player sender, ChatMode mode, String text, ChatProfile profile) {
        // Не задано (ключа нет) — стандартный префикс "Ник: ". Пустая строка — осознанное "без префикса".
        String prefix = profile.prefix() != null ? profile.prefix() : sender.getName() + ": ";
        String postfix = profile.postfix() != null ? profile.postfix() : "";

        ParsedMessage parsed = parseHiddenSegments(text);
        Component publicFormatted = buildMessage(prefix, parsed.publicText(), postfix);
        Component adminFormatted = buildMessage(prefix, parsed.adminText(), postfix);

        for (Player viewer : recipients(sender, mode, profile)) {
            if (isAdmin(viewer) || viewer == sender) {
                viewer.sendMessage(adminFormatted);
            } else if (!parsed.publicText().isEmpty()) {
                viewer.sendMessage(publicFormatted);
            }
        }
    }

    /**
     * LOCAL — игроки мира отправителя в радиусе; WORLD — игроки мира отправителя
     * (при радиусе > 0 — только в радиусе); GLOBAL — все онлайн-игроки.
     */
    private Collection<? extends Player> recipients(Player sender, ChatMode mode, ChatProfile profile) {
        if (mode == ChatMode.GLOBAL) {
            return Bukkit.getOnlinePlayers();
        }

        List<Player> worldPlayers = sender.getWorld().getPlayers();
        double radius = radiusOf(mode, profile);
        if (mode == ChatMode.WORLD && radius <= 0) {
            return worldPlayers; // радиус 0 — на весь мир
        }

        double radiusSquared = radius * radius;
        Location origin = sender.getLocation();
        return worldPlayers.stream()
                .filter(p -> p.getLocation().distanceSquared(origin) <= radiusSquared)
                .toList();
    }

    private boolean isAdmin(CommandSender viewer) {
        if (viewer instanceof Player player) {
            return player.hasPermission(PERMISSION_SEE);
        }
        return true; // консоль и т.п. — считаем админом
    }

    /**
     * "Публичная" версия — {скрытые} фрагменты вырезаны целиком, "админская" —
     * остаются видимыми как есть.
     */
    private ParsedMessage parseHiddenSegments(String text) {
        Matcher matcher = HIDDEN_PATTERN.matcher(text);

        StringBuilder publicSb = new StringBuilder();
        StringBuilder adminSb = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            String before = text.substring(lastEnd, matcher.start());
            publicSb.append(before);
            adminSb.append(before);
            adminSb.append('{').append(matcher.group(1)).append('}');
            lastEnd = matcher.end();
        }
        publicSb.append(text.substring(lastEnd));
        adminSb.append(text.substring(lastEnd));

        String publicText = publicSb.toString().replaceAll(" {2,}", " ").trim();
        return new ParsedMessage(publicText, adminSb.toString());
    }

    private record ParsedMessage(String publicText, String adminText) {
    }

    private Component buildMessage(String prefix, String text, String postfix) {
        StringBuilder sb = new StringBuilder();
        if (!prefix.isEmpty()) sb.append(prefix);
        sb.append(text);
        if (!postfix.isEmpty()) sb.append(postfix);
        return legacy.deserialize(sb.toString());
    }
}