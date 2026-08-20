package net.dvmn2.chatFix;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Главный класс плагина ChatFix.
 * <p>
 * Отвечает за жизненный цикл плагина: инициализацию {@link ChatDataManager},
 * регистрацию слушателей событий и команды /chatfix, а также корректное
 * сохранение данных при выключении сервера.
 */
public final class ChatFix extends JavaPlugin implements Listener {

    // Создаётся сразу в поле, а не в onEnable(), т.к. ChatDataManager сам
    // грузит данные с диска в конструкторе и не зависит от других частей плагина.
    private final ChatDataManager chatDataManager = new ChatDataManager(this);

    @Override
    public void onEnable() {
        // Сохранение конфигурации по умолчанию (если есть config.yml)
        saveDefaultConfig();

        // Регистрация слушателей событий:
        // - this (ChatFix) слушает PlayerJoinEvent, чтобы регистрировать ник/UUID
        // - ChatListener обрабатывает и форматирует сообщения в чате
        getServer().getPluginManager().registerEvents(this, this);
        ChatFixCommand chatFixCommand = new ChatFixCommand(this, chatDataManager);
        getServer().getPluginManager().registerEvents(new ChatListener(chatDataManager, getConfig()), this);

        // Регистрация Brigadier-команды /chatfix через новый lifecycle API Paper.
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
        // Принудительно и синхронно сбрасываем данные на диск перед выключением,
        // т.к. асинхронная отложенная задача сохранения могла не успеть сработать.
        chatDataManager.forceSaveSync();
        getLogger().info("ChatFix disabled!");
    }

    /**
     * При каждом входе игрока обновляем/создаём связь его ника с UUID,
     * чтобы команда /chatfix могла резолвить офлайн-игроков и чтобы
     * кэш имён не устаревал после смены ника.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        chatDataManager.registerName(p.getUniqueId(), p.getName());
    }
}