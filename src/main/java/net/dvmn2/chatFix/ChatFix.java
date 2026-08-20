package net.dvmn2.chatFix;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChatFix extends JavaPlugin implements Listener {
    private final ChatDataManager chatDataManager = new ChatDataManager(this);

    @Override
    public void onEnable() {
        // Сохранение конфигурации по умолчанию (если есть config.yml)
        saveDefaultConfig();

        // Регистрация слушателей событий
        getServer().getPluginManager().registerEvents(this, this);
        ChatFixCommand chatFixCommand = new ChatFixCommand(this, chatDataManager);
        getServer().getPluginManager().registerEvents(new ChatListener(chatDataManager, getConfig()), this);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(
                    chatFixCommand.create(),
                    "Меняет фиксы игроков"
            );
        });

        getLogger().info("ChatFix enabled!");
    }

    @Override
    public void onDisable() {
        chatDataManager.forceSaveSync();
        getLogger().info("ChatFix disabled!");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        chatDataManager.registerName(p.getUniqueId(), p.getName());
    }
}