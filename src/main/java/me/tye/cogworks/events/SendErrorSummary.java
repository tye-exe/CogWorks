package me.tye.cogworks.events;

import me.tye.cogworks.util.Log;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.logging.Level;

import static me.tye.cogworks.util.Util.getConfig;
import static me.tye.cogworks.CogWorks.log;

public class SendErrorSummary implements Listener {
    public static int severe = 0;

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (player.isOp() && (Boolean) getConfig("showOpErrorSummary")) {
            if (severe > 0) {
                new Log(player, "info", "errorSum").setSevere(severe).log();
            }
        }
    }

}
