package net.dvmn2.chatFix;

import com.mojang.brigadier.Command;
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
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /chatfix <ник> <local|global> <prefix|postfix> <текст...>
 * <p>
 * Примеры:
 * /chatfix local prefix Steve &7[Локальный]
 * /chatfix local postfix Steve &7[/Локальный]
 * /chatfix global prefix Steve &c[Глобал]
 */
public class ChatFixCommand {

    private final JavaPlugin plugin;
    private final ChatDataManager dataManager;

    private static final String[] MODES = {"local", "global"};
    private static final String[] FIX_TYPES = {"prefix", "postfix"};

    public ChatFixCommand(JavaPlugin plugin, ChatDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    /**
     * Собирает дерево Brigadier-команды. Регистрируется в Scanner#onEnable через
     * getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, ...).
     */
    public LiteralCommandNode<CommandSourceStack> create() {
        return Commands.literal("chatfix")
                .requires(source -> source.getSender().hasPermission("chatmanager.admin"))
                .then(Commands.argument("targets", ArgumentTypes.players())
                        .then(Commands.argument("mode", StringArgumentType.string())
                                .suggests((ctx, builder) -> suggest(builder, MODES))
                                .then(Commands.argument("fix", StringArgumentType.string())
                                        .suggests((ctx, builder) -> suggest(builder, FIX_TYPES))
                                        .then(Commands.argument("text", StringArgumentType.string())
                                                .executes(ctx -> run(ctx, plugin))
                                        )
                                )
                        )
                )
                .build();
    }

    private int run(CommandContext<CommandSourceStack> ctx, JavaPlugin plugin) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();

        PlayerSelectorArgumentResolver resolver = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class);
        List<Player> players = resolver.resolve(ctx.getSource());

        String mode = StringArgumentType.getString(ctx, "mode");
        String fix = StringArgumentType.getString(ctx, "fix");
        String text = StringArgumentType.getString(ctx, "text");

        if (!mode.equals("local") && !mode.equals("global")) {
            sender.sendMessage(ChatColor.RED + "Мод должен быть либо local, либо global.");
            return 0;
        }

        if (!fix.equals("prefix") && !fix.equals("postfix")) {
            sender.sendMessage(ChatColor.RED + "Фикс должен быть либо prefix, либо postfix.");
            return 0;
        }

        for (Player player : players) {
            String targetName = player.getName();
            UUID targetUuid = dataManager.resolveUuid(targetName);

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
            sender.sendMessage(ChatColor.GREEN + "Фикс (" + fix + ") для " + targetName + " установлен: "
                    + ChatColor.translateAlternateColorCodes('&', text));
        }

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