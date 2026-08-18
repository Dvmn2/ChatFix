package net.dvmn2.chatFix;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChatFix extends JavaPlugin implements Listener {
    private final ChatDataManager chatDataManager = new ChatDataManager(this);

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Регистрация слушателей событий
        getServer().getPluginManager().registerEvents(this, this);

        getServer().getPluginManager().registerEvents(new ChatListener(chatDataManager, getConfig()), this);
        getCommand("chatfix").setExecutor(new ChatFixCommand(chatDataManager));

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
