package net.dvmn2.chatFix;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Индивидуальные настройки игрока:
 * <pre>
 * /chatfix set <targets> <local|world|global> prefix|postfix "текст"
 * /chatfix set <targets> <local|world|global> enabled <true|false|default>
 * /chatfix set <targets> <local|world> radius <число|default>
 * /chatfix offline <ник> ... (то же самое, но по нику; игрок может ни разу не заходить)
 * </pre>
 * Общие настройки (config.yml):
 * <pre>
 * /chatfix radius <значение>          — радиус локального чата
 * /chatfix worldradius <значение>     — радиус мирового чата (0 = весь мир)
 * /chatfix localchat|worldchat|globalchat <on|off>
 * /chatfix feedback <on|off>
 * </pre>
 * "set" — targets через стандартный селектор игроков (только онлайн, зато с
 * автодополнением ников). "offline" — target по строке: если игрок известен
 * (есть в chatdata.yml или онлайн), данные пишутся ему; иначе — в секцию
 * offline-players и переносятся под UUID при первом входе игрока.
 * "текст" в кавычках, если содержит пробелы.
 */
public class ChatFixCommand {

    private static final String PERMISSION_ADMIN = "chatmanager.admin";
    private static final String[] MODES = {"local", "world", "global"};
    private static final String[] PARAMS = {"prefix", "postfix", "enabled", "radius"};
    private static final String[] TOGGLE_STATES = {"on", "off"};
    private static final String DEFAULT_VALUE = "default";

    private static final double MIN_LOCAL_RADIUS = 1.0;
    private static final double MIN_WORLD_RADIUS = 0.0; // 0 — на весь мир

    private final ChatDataManager dataManager;
    private final ChatSettings settings;

    public ChatFixCommand(ChatDataManager dataManager, ChatSettings settings) {
        this.dataManager = dataManager;
        this.settings = settings;
    }

    public LiteralCommandNode<CommandSourceStack> create() {
        return Commands.literal("chatfix")
                .requires(source -> source.getSender().hasPermission(PERMISSION_ADMIN))
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", ArgumentTypes.player())
                                .then(settingArguments(this::runSet))))
                .then(Commands.literal("offline")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .then(settingArguments(this::runOffline))))
                .then(radiusNode("localradius", ChatMode.LOCAL, MIN_LOCAL_RADIUS, Lang.Key.RADIUS_SET))
                .then(radiusNode("worldradius", ChatMode.WORLD, MIN_WORLD_RADIUS, Lang.Key.WORLD_RADIUS_SET))
                .then(toggleNode("localchat",
                        enabled -> settings.setEnabled(ChatMode.LOCAL, enabled),
                        Lang.Key.LOCAL_CHAT_ENABLED, Lang.Key.LOCAL_CHAT_DISABLED))
                .then(toggleNode("worldchat",
                        enabled -> settings.setEnabled(ChatMode.WORLD, enabled),
                        Lang.Key.WORLD_CHAT_ENABLED, Lang.Key.WORLD_CHAT_DISABLED))
                .then(toggleNode("globalchat",
                        enabled -> settings.setEnabled(ChatMode.GLOBAL, enabled),
                        Lang.Key.GLOBAL_CHAT_ENABLED, Lang.Key.GLOBAL_CHAT_DISABLED))
                .then(toggleNode("feedback",
                        settings::setFeedbackEnabled,
                        Lang.Key.FEEDBACK_ENABLED, Lang.Key.FEEDBACK_DISABLED))
                .build();
    }

    /**
     * Общая часть "set" и "offline": {@code <mode> <param> <value>}.
     * Различаются только исполнитель и то, как получен игрок.
     */
    private RequiredArgumentBuilder<CommandSourceStack, String> settingArguments(Command<CommandSourceStack> executor) {
        return Commands.argument("mode", StringArgumentType.word())
                .suggests((ctx, builder) -> suggest(builder, MODES))
                .then(Commands.argument("param", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggest(builder, PARAMS))
                        .then(Commands.argument("value", StringArgumentType.string())
                                .suggests(this::suggestValues)
                                .executes(executor)));
    }

    private CompletableFuture<Suggestions> suggestValues(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String param = StringArgumentType.getString(ctx, "param").toLowerCase(Locale.ROOT);
        return switch (param) {
            case "enabled" -> suggest(builder, "true", "false", DEFAULT_VALUE);
            case "radius" -> suggest(builder, DEFAULT_VALUE);
            default -> builder.buildFuture();
        };
    }

    /**
     * Резолвит селектор "targets" в список игроков. Имя аргумента здесь ДОЛЖНО
     * совпадать с тем, что передано в Commands.argument("targets", ...) выше —
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

    /**
     * Результат разбора {@code <mode> <param> <value>}: набор ключей для записи
     * в данные игрока + то, что нужно для сообщения об успехе.
     */
    private record ParsedSetting(String label, boolean fix, String value, Map<String, Object> changes) {
    }

    /**
     * Валидирует и переводит {@code <mode> <param> <value>} в набор ключей chatdata.yml.
     * При ошибке сам отправляет сообщение отправителю команды и возвращает {@code null}.
     */
    private ParsedSetting parseSetting(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        ChatMode mode = ChatMode.fromKey(StringArgumentType.getString(ctx, "mode"));
        String param = StringArgumentType.getString(ctx, "param").toLowerCase(Locale.ROOT);
        String value = StringArgumentType.getString(ctx, "value");

        if (mode == null) {
            sender.sendMessage(Lang.get(Lang.Key.INVALID_MODE, sender));
            return null;
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        boolean fix = false;

        switch (param) {
            case "prefix" -> {
                fix = true;
                changes.put(mode.prefixKey(), value);
            }
            case "postfix" -> {
                fix = true;
                changes.put(mode.postfixKey(), value);
            }
            case "enabled" -> {
                if (value.equalsIgnoreCase(DEFAULT_VALUE)) {
                    // Значение в файле остаётся, просто перестаёт использоваться.
                    changes.put(mode.customModeKey(), false);
                } else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                    changes.put(mode.customModeKey(), true);
                    changes.put(mode.enabledKey(), Boolean.parseBoolean(value));
                } else {
                    sender.sendMessage(Lang.get(Lang.Key.INVALID_ENABLED_VALUE, sender));
                    return null;
                }
            }
            case "radius" -> {
                if (!mode.hasRadius()) {
                    sender.sendMessage(Lang.get(Lang.Key.NO_RADIUS_FOR_MODE, sender));
                    return null;
                }
                if (value.equalsIgnoreCase(DEFAULT_VALUE)) {
                    changes.put(mode.customRadiusKey(), false);
                } else {
                    double min = mode == ChatMode.LOCAL ? MIN_LOCAL_RADIUS : MIN_WORLD_RADIUS;
                    Double radius = parseRadius(value);
                    if (radius == null || radius < min) {
                        sender.sendMessage(Lang.get(Lang.Key.INVALID_RADIUS, sender, min));
                        return null;
                    }
                    changes.put(mode.customRadiusKey(), true);
                    changes.put(mode.radiusKey(), radius);
                }
            }
            default -> {
                sender.sendMessage(Lang.get(Lang.Key.INVALID_PARAM, sender));
                return null;
            }
        }

        return new ParsedSetting(mode.key() + " " + param, fix, value, changes);
    }

    private static Double parseRadius(String raw) {
        try {
            double parsed = Double.parseDouble(raw);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int runSet(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        ParsedSetting setting = parseSetting(ctx);
        if (setting == null) {
            return 0;
        }

        for (Player player : resolvePlayers(ctx)) {
            dataManager.registerName(player.getUniqueId(), player.getName());
            dataManager.applyChanges(player.getUniqueId(), setting.changes());
            reportSuccess(sender, player.getName(), setting);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runOffline(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String targetName = StringArgumentType.getString(ctx, "target");

        ParsedSetting setting = parseSetting(ctx);
        if (setting == null) {
            return 0;
        }
        if (!ChatDataManager.isValidName(targetName)) {
            sender.sendMessage(Lang.get(Lang.Key.INVALID_PLAYER_NAME, sender));
            return 0;
        }

        // 1) известен по chatdata.yml, 2) сейчас онлайн, 3) иначе — ждёт первого входа.
        UUID targetUuid = dataManager.resolveUuid(targetName);
        if (targetUuid == null) {
            Player online = Bukkit.getPlayerExact(targetName);
            if (online != null) {
                dataManager.registerName(online.getUniqueId(), online.getName());
                targetUuid = online.getUniqueId();
            }
        }

        if (targetUuid != null) {
            dataManager.applyChanges(targetUuid, setting.changes());
            String knownName = dataManager.getName(targetUuid);
            reportSuccess(sender, knownName.isEmpty() ? targetName : knownName, setting);
        } else {
            dataManager.applyOfflineChanges(targetName, setting.changes());
            reportSuccess(sender, targetName, setting);
            sender.sendMessage(Lang.get(Lang.Key.OFFLINE_PENDING, sender));
        }
        return Command.SINGLE_SUCCESS;
    }

    private void reportSuccess(CommandSender sender, String playerName, ParsedSetting setting) {
        if (setting.fix()) {
            sender.sendMessage(Lang.get(Lang.Key.FIX_SET, sender, setting.label(), playerName,
                    ChatColor.translateAlternateColorCodes('&', setting.value())));
        } else {
            sender.sendMessage(Lang.get(Lang.Key.PARAM_SET, sender, setting.label(), playerName, setting.value()));
        }
    }

    private LiteralArgumentBuilder<CommandSourceStack> radiusNode(String name, ChatMode mode, double min, Lang.Key messageKey) {
        return Commands.literal(name)
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(min))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            double value = DoubleArgumentType.getDouble(ctx, "value");
                            settings.setRadius(mode, value);
                            sender.sendMessage(Lang.get(messageKey, sender, value));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> toggleNode(String name, Consumer<Boolean> setter,
                                                                  Lang.Key onKey, Lang.Key offKey) {
        return Commands.literal(name)
                .then(Commands.argument("state", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggest(builder, TOGGLE_STATES))
                        .executes(ctx -> runToggle(ctx, setter, onKey, offKey)));
    }

    private int runToggle(CommandContext<CommandSourceStack> ctx, Consumer<Boolean> setter,
                          Lang.Key onKey, Lang.Key offKey) {
        CommandSender sender = ctx.getSource().getSender();
        String state = StringArgumentType.getString(ctx, "state").toLowerCase(Locale.ROOT);

        if (!state.equals("on") && !state.equals("off")) {
            sender.sendMessage(Lang.get(Lang.Key.INVALID_TOGGLE_STATE, sender));
            return 0;
        }

        boolean enabled = state.equals("on");
        setter.accept(enabled);
        sender.sendMessage(Lang.get(enabled ? onKey : offKey, sender));
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, String... options) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.startsWith(remaining)) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }
}