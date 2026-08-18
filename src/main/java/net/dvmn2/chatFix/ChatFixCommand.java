package net.dvmn2.chatFix;

import net.dvmn2.chatFix.managers.SelectorTab;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * /chatfix <local|global> <prefix|postfix|reset> <ник> [текст...]
 * <p>
 * Примеры:
 * /chatfix local prefix Steve &7[Локальный]
 * /chatfix local postfix Steve &7[/Локальный]
 * /chatfix global prefix Steve &c[Глобал]
 * /chatfix global reset Steve
 */
public class ChatFixCommand implements CommandExecutor, TabCompleter {

    private final ChatDataManager dataManager;

    public ChatFixCommand(ChatDataManager dataManager) {
        this.dataManager = dataManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chatmanager.admin")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав на использование этой команды.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Использование: /chatfix <local|global> <prefix|postfix|reset> <ник> [текст...]");
            return true;
        }

        String scope = args[0].toLowerCase();
        if (!scope.equals("local") && !scope.equals("global")) {
            sender.sendMessage(ChatColor.RED + "Первый аргумент должен быть local или global.");
            return true;
        }

        String action = args[1].toLowerCase();
        String targetName = args[2];
        UUID targetUuid = dataManager.resolveUuid(targetName);

        if (targetUuid == null) {
            sender.sendMessage(ChatColor.RED + "Игрок " + targetName + " никогда не заходил на сервер.");
            return true;
        }

        switch (action) {
            case "prefix" -> {
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Укажите текст префикса.");
                    return true;
                }
                String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                if (scope.equals("local")) {
                    dataManager.setLocalPrefix(targetUuid, value);
                } else {
                    dataManager.setGlobalPrefix(targetUuid, value);
                }
                sender.sendMessage(ChatColor.GREEN + "Префикс (" + scope + ") для " + targetName + " установлен: "
                        + ChatColor.translateAlternateColorCodes('&', value));
            }
            case "postfix" -> {
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Укажите текст постфикса.");
                    return true;
                }
                String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                if (scope.equals("local")) {
                    dataManager.setLocalPostfix(targetUuid, value);
                } else {
                    dataManager.setGlobalPostfix(targetUuid, value);
                }
                sender.sendMessage(ChatColor.GREEN + "Постфикс (" + scope + ") для " + targetName + " установлен: "
                        + ChatColor.translateAlternateColorCodes('&', value));
            }
            case "reset" -> {
                if (scope.equals("local")) {
                    dataManager.setLocalPrefix(targetUuid, targetName + ":");
                    dataManager.setLocalPostfix(targetUuid, "");
                } else {
                    dataManager.setGlobalPrefix(targetUuid, targetName + ":");
                    dataManager.setGlobalPostfix(targetUuid, "");
                }
                sender.sendMessage(ChatColor.GREEN + "Фиксы (" + scope + ") для " + targetName + " сброшены.");
            }
            default -> sender.sendMessage(ChatColor.RED + "Действие должно быть prefix, postfix или reset.");
        }

        return true;
    }

    // <local|global> <prefix|postfix|reset> <ник> [текст...]
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("local", "global");
        }
        if (args.length == 2) {
            return Arrays.asList("prefix", "postfix", "reset");
        }
        if (args.length == 3) {
            return SelectorTab.getPlayers();
        }
        return Collections.emptyList();
    }
}