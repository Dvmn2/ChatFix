package net.dvmn2.chatFix;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Главный класс плагина: жизненный цикл, регистрация слушателей/команды,
 * сохранение данных при выключении.
 */
public final class ChatFix extends JavaPlugin implements Listener {

    private final ChatDataManager chatDataManager = new ChatDataManager(this);

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ChatSettings settings = new ChatSettings(this);
        Lang.setLanguage(getConfig().getString("settings.language", "auto"));

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new ChatListener(chatDataManager, settings), this);

        ChatFixCommand chatFixCommand = new ChatFixCommand(this, chatDataManager, settings);
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(chatFixCommand.create(), "Меняет фиксы игроков и настройки чата");
        });

        getLogger().info("ChatFix enabled!");
    }

    @Override
    public void onDisable() {
        // Форсируем синхронную запись — отложенная async-задача могла не успеть сработать.
        chatDataManager.forceSaveSync();
        getLogger().info("ChatFix disabled!");
    }

    /**
     * При каждом входе обновляем связь ник <-> UUID, чтобы /chatfix мог
     * резолвить офлайн-игроков и чтобы кэш не устаревал после смены ника.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        chatDataManager.registerName(p.getUniqueId(), p.getName());
    }
}