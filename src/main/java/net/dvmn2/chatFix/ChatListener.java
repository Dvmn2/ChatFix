package net.dvmn2.chatFix;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Перехватывает и переформатирует чат игроков.
 * Сообщение без "!" — локальный чат (только игрокам в радиусе), с "!" —
 * глобальный чат (все онлайн-игроки, если он не выключен через /chatfix).
 * Фрагменты вида {текст} видит только отправитель и игроки с правом
 * chatmanager.seehidden, остальные видят сообщение без них.
 */
public class ChatListener implements Listener {

    private static final Pattern HIDDEN_PATTERN = Pattern.compile("\\{([^{}]*)\\}");

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

        boolean isGlobal = rawMessage.charAt(0) == '!';
        String text = isGlobal ? rawMessage.substring(1) : rawMessage;
        if (text.isEmpty()) {
            return;
        }

        if (isGlobal) {
            if (!settings.isGlobalChatEnabled()) {
                sender.sendMessage(Lang.get(Lang.Key.GLOBAL_CHAT_IS_DISABLED, sender));
                return;
            }
            sendGlobalMessage(sender, text);
        } else {
            sendLocalMessage(sender, text);
        }
    }

    /**
     * Только игрокам в радиусе {@link ChatSettings#getLocalRadiusSquared()}
     * в том же мире. Отправитель и админы видят версию со скрытыми фрагментами.
     */
    private void sendLocalMessage(Player sender, String text) {
        String prefix = dataManager.getLocalPrefix(sender.getUniqueId());
        String postfix = dataManager.getLocalPostfix(sender.getUniqueId());

        ParsedMessage parsed = parseHiddenSegments(text);
        Component publicFormatted = buildMessage(prefix, parsed.publicText(), postfix);
        Component adminFormatted = buildMessage(prefix, parsed.adminText(), postfix);

        World world = sender.getWorld();
        double radiusSquared = settings.getLocalRadiusSquared();
        for (Player viewer : world.getPlayers()) {
            if (viewer.getLocation().distanceSquared(sender.getLocation()) <= radiusSquared) {
                if (isAdmin(viewer) || viewer == sender) {
                    viewer.sendMessage(adminFormatted);
                } else if (!parsed.publicText().isEmpty()) {
                    viewer.sendMessage(publicFormatted);
                }
            }
        }
    }

    /**
     * Всем онлайн-игрокам, вне зависимости от мира и расстояния.
     */
    private void sendGlobalMessage(Player sender, String text) {
        String prefix = dataManager.getGlobalPrefix(sender.getUniqueId());
        String postfix = dataManager.getGlobalPostfix(sender.getUniqueId());

        ParsedMessage parsed = parseHiddenSegments(text);
        Component publicFormatted = buildMessage(prefix, parsed.publicText(), postfix);
        Component adminFormatted = buildMessage(prefix, parsed.adminText(), postfix);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (isAdmin(online) || online == sender) {
                online.sendMessage(adminFormatted);
            } else if (!parsed.publicText().isEmpty()) {
                online.sendMessage(publicFormatted);
            }
        }
    }

    private boolean isAdmin(CommandSender viewer) {
        if (viewer instanceof Player player) {
            return player.hasPermission("chatmanager.seehidden");
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