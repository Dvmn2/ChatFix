package net.dvmn2.chatFix.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SelectorTab {

    public static List<String> getPlayers() {
        List<String> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            result.add(p.getName());
        }
        return result;
    }
}
