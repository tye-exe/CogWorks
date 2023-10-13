package me.tye.cogworks;

import me.tye.cogworks.util.customObjects.Log;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import static me.tye.cogworks.util.Util.getConfig;

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
