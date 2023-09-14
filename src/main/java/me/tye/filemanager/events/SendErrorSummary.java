package me.tye.filemanager.events;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.logging.Level;

import static me.tye.filemanager.FileManager.configs;
import static me.tye.filemanager.FileManager.log;

public class SendErrorSummary implements Listener {
    public static int severe = 0;

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (player.isOp() && (Boolean) configs.get("showOpErrorSummary")) {
            if (severe > 0)
                log(null, Level.WARNING, "There have been "+severe+" severe error(s) since last reload/restart, this could cause unpredictable behaviour. Please report these issues.");
        }
    }

}
