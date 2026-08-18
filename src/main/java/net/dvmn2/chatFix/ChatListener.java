package net.dvmn2.chatFix;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatListener implements Listener {

    // Ищем фрагменты вида {текст}, без поддержки вложенных скобок
    private static final Pattern HIDDEN_PATTERN = Pattern.compile("\\{([^{}]*)\\}");

    private final ChatDataManager dataManager;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private final double localRadiusSquared;

    public ChatListener(ChatDataManager dataManager, FileConfiguration config) {
        this.dataManager = dataManager;
        double radius = config.getDouble("local-chat-radius", 15.0);
        this.localRadiusSquared = radius * radius;
    }

    // priority HIGH — чтобы отработать после других плагинов, которые могут менять/отменять сообщение
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
            return; // сообщение состояло из одного "!"
        }

        if (isGlobal) {
            sendGlobalMessage(sender, text);
        } else {
            sendLocalMessage(sender, text);
        }
    }

    private void sendLocalMessage(Player sender, String text) {
        String prefix = dataManager.getLocalPrefix(sender.getUniqueId());
        String postfix = dataManager.getLocalPostfix(sender.getUniqueId());

        ParsedMessage parsed = parseHiddenSegments(text);
        Component publicFormatted = buildMessage(prefix, parsed.publicText(), postfix);
        Component adminFormatted = buildMessage(prefix, parsed.adminText(), postfix);

        World world = sender.getWorld();
        for (Player viewer : world.getPlayers()) {
            if (viewer.getLocation().distanceSquared(sender.getLocation()) <= localRadiusSquared) {
                if (isAdmin(viewer) || viewer == sender) {
                    viewer.sendMessage(adminFormatted);
                } else if (!parsed.publicText().isEmpty()) {
                    viewer.sendMessage(publicFormatted);
                }
            }
        }
    }

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

    // Считаем получателя "админом" (видящим скрытые фрагменты), если у него есть доступ.
    private boolean isAdmin(CommandSender viewer) {
        if (viewer instanceof Player player) {
            return viewer.hasPermission("chatmanager.seehidden");
        }
        return true; // Считаем кб админом
    }

    // Разбирает сообщение на "публичную" версию (без содержимого {}) и "админскую".
    private ParsedMessage parseHiddenSegments(String text) {
        Matcher matcher = HIDDEN_PATTERN.matcher(text);

        StringBuilder publicSb = new StringBuilder();
        StringBuilder adminSb = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            String before = text.substring(lastEnd, matcher.start());
            publicSb.append(before);
            adminSb.append(before);

            String hidden = matcher.group(1);
            adminSb.append('{').append(hidden).append('}');

            lastEnd = matcher.end();
        }
        publicSb.append(text.substring(lastEnd));
        adminSb.append(text.substring(lastEnd));

        // убираем лишние пробелы, оставшиеся после вырезания {}
        String publicText = publicSb.toString().replaceAll(" {2,}", " ").trim();
        String adminText = adminSb.toString();

        return new ParsedMessage(publicText, adminText);
    }

    private record ParsedMessage(String publicText, String adminText) {
    }

    // Формат: [prefix] сообщение [postfix]
    private Component buildMessage(String prefix, String text, String postfix) {
        StringBuilder sb = new StringBuilder();
        if (!prefix.isEmpty()) sb.append(prefix).append(' ');
        sb.append(text);
        if (!postfix.isEmpty()) sb.append(' ').append(postfix);
        return legacy.deserialize(sb.toString());
    }
}
