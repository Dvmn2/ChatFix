package net.dvmn2.chatFix;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /chatfix set <targets> <local|global> <prefix|postfix> "текст"
 * /chatfix offline <target> <local|global> <prefix|postfix> "текст"
 * /chatfix radius <значение>
 * /chatfix globalchat <on|off>
 * <p>
 * "set" — targets через стандартный селектор игроков (только для тех, кто
 * сейчас онлайн, зато с автодополнением ников). "offline" — target по
 * строке, ищется в chatdata.yml, а если там нет — через Mojang API.
 * "текст" в кавычках, если содержит пробелы.
 */
public class ChatFixCommand {

    private static final String PERMISSION_ADMIN = "chatmanager.admin";
    private static final String[] MODES = {"local", "global"};
    private static final String[] FIX_TYPES = {"prefix", "postfix"};
    private static final String[] TOGGLE_STATES = {"on", "off"};

    private final JavaPlugin plugin;
    private final ChatDataManager dataManager;
    private final ChatSettings settings;

    public ChatFixCommand(JavaPlugin plugin, ChatDataManager dataManager, ChatSettings settings) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.settings = settings;
    }

    public LiteralCommandNode<CommandSourceStack> create() {
        return Commands.literal("chatfix")
                .requires(source -> source.getSender().hasPermission(PERMISSION_ADMIN))
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", ArgumentTypes.player())
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggest(builder, MODES))
                                        .then(Commands.argument("fix", StringArgumentType.word())
                                                .suggests((ctx, builder) -> suggest(builder, FIX_TYPES))
                                                .then(Commands.argument("text", StringArgumentType.string())
                                                        .executes(ctx -> runFix(ctx,
                                                                resolvePlayers(ctx),
                                                                StringArgumentType.getString(ctx, "mode"),
                                                                StringArgumentType.getString(ctx, "fix"),
                                                                StringArgumentType.getString(ctx, "text")
                                                        ))
                                                )
                                        )
                                )
                        ))
                .then(Commands.literal("offline")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggest(builder, MODES))
                                        .then(Commands.argument("fix", StringArgumentType.word())
                                                .suggests((ctx, builder) -> suggest(builder, FIX_TYPES))
                                                .then(Commands.argument("text", StringArgumentType.string())
                                                        .executes(ctx -> runOfflineFix(ctx,
                                                                StringArgumentType.getString(ctx, "target"),
                                                                StringArgumentType.getString(ctx, "mode"),
                                                                StringArgumentType.getString(ctx, "fix"),
                                                                StringArgumentType.getString(ctx, "text")
                                                        ))
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("radius")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0))
                                .executes(this::runRadius)
                        )
                )
                .then(Commands.literal("globalchat")
                        .then(Commands.argument("state", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggest(builder, TOGGLE_STATES))
                                .executes(this::runGlobalChat)
                        )
                )
                .build();
    }

    /**
     * Резолвит селектор "target" в список игроков. Имя аргумента здесь ДОЛЖНО
     * совпадать с тем, что передано в Commands.argument("target", ...) выше —
     * иначе getArgument() кидает IllegalArgumentException, который не ловится.
     */
    private static List<Player> resolvePlayers(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerSelectorArgumentResolver resolver =
                    ctx.getArgument("targets", PlayerSelectorArgumentResolver.class);
            return resolver.resolve(ctx.getSource());
        } catch (CommandSyntaxException | IndexOutOfBoundsException e) {
            ctx.getSource().getSender().sendMessage(
                    Lang.get(Lang.Key.TARGET_RESOLVE_FAILED, ctx.getSource().getSender()));
            return Collections.emptyList();
        }
    }

    private int runOfflineFix(CommandContext<CommandSourceStack> ctx, String targetName, String mode, String fix, String text) {
        CommandSender sender = ctx.getSource().getSender();

        if (!mode.equals("local") && !mode.equals("global")) {
            sender.sendMessage(Lang.get(Lang.Key.INVALID_MODE, sender));
            return 0;
        }
        if (!fix.equals("prefix") && !fix.equals("postfix")) {
            sender.sendMessage(Lang.get(Lang.Key.INVALID_FIX, sender));
            return 0;
        }

        findPlayer(sender, targetName, mode, fix, text);
        return Command.SINGLE_SUCCESS;
    }

    private int runFix(CommandContext<CommandSourceStack> ctx, List<Player> players, String mode, String fix, String text) {
        CommandSender sender = ctx.getSource().getSender();

        if (!mode.equals("local") && !mode.equals("global")) {
            sender.sendMessage(Lang.get(Lang.Key.INVALID_MODE, sender));
            return 0;
        }
        if (!fix.equals("prefix") && !fix.equals("postfix")) {
            sender.sendMessage(Lang.get(Lang.Key.INVALID_FIX, sender));
            return 0;
        }

        for (Player player : players) {
            dataManager.registerName(player.getUniqueId(), player.getName());
            applyFix(player.getUniqueId(), mode, fix, text);
            reportSuccess(sender, player.getUniqueId(), player.getName(), fix, text);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Общая логика поиска офлайн-игрока: кэш chatdata.yml -> Mojang API.
     * Используется только для "offline"-ветки; для "set" UUID уже известен
     * из селектора и findPlayer не нужен (см. runFix).
     */
    private void findPlayer(CommandSender sender, String targetName, String mode, String fix, String text) {
        UUID targetUuid = dataManager.resolveUuid(targetName);
        if (targetUuid == null) {
            Player online = Bukkit.getPlayerExact(targetName);
            if (online != null) {
                dataManager.registerName(online.getUniqueId(), online.getName());
                targetUuid = online.getUniqueId();
            }
        }

        if (targetUuid != null) {
            applyFix(targetUuid, mode, fix, text);
            reportSuccess(sender, targetUuid, targetName, fix, text);
            return;
        }

        // Ника нет ни онлайн, ни в файле — идём в Mojang API асинхронно (сетевой запрос).
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID fetched = dataManager.fetchAndRegisterFromMojang(targetName);
            if (fetched == null) {
                sender.sendMessage(Lang.get(Lang.Key.PLAYER_NOT_FOUND, sender, targetName));
                return;
            }
            applyFix(fetched, mode, fix, text);
            reportSuccess(sender, fetched, targetName, fix, text);
        });
    }

    private void applyFix(UUID targetUuid, String mode, String fix, String text) {
        switch (mode) {
            case "local" -> {
                if (fix.equals("prefix")) {
                    dataManager.setLocalPrefix(targetUuid, text);
                } else {
                    dataManager.setLocalPostfix(targetUuid, text);
                }
            }
            case "global" -> {
                if (fix.equals("prefix")) {
                    dataManager.setGlobalPrefix(targetUuid, text);
                } else {
                    dataManager.setGlobalPostfix(targetUuid, text);
                }
            }
        }
    }

    private void reportSuccess(CommandSender sender, UUID targetUuid, String fallbackName, String fix, String text) {
        String resolvedName = dataManager.getName(targetUuid);
        String displayName = resolvedName.isEmpty() ? fallbackName : resolvedName;
        sender.sendMessage(Lang.get(Lang.Key.FIX_SET, sender, fix, displayName,
                ChatColor.translateAlternateColorCodes('&', text)));
    }

    private int runRadius(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        double value = DoubleArgumentType.getDouble(ctx, "value");
        settings.setLocalRadius(value);
        sender.sendMessage(Lang.get(Lang.Key.RADIUS_SET, sender, value));
        return Command.SINGLE_SUCCESS;
    }

    private int runGlobalChat(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String state = StringArgumentType.getString(ctx, "state").toLowerCase();

        if (!state.equals("on") && !state.equals("off")) {
            sender.sendMessage(Lang.get(Lang.Key.INVALID_TOGGLE_STATE, sender));
            return 0;
        }

        boolean enabled = state.equals("on");
        settings.setGlobalChatEnabled(enabled);
        sender.sendMessage(Lang.get(enabled ? Lang.Key.GLOBAL_CHAT_ENABLED : Lang.Key.GLOBAL_CHAT_DISABLED, sender));
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, String... options) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String option : options) {
            if (option.startsWith(remaining)) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }
}