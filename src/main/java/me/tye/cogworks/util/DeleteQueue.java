package me.tye.cogworks.util;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.HashMap;

import static me.tye.cogworks.commands.PluginCommand.deletePlugin;
import static me.tye.cogworks.util.Util.plugin;

public class DeleteQueue {

public static HashMap<CommandSender,Boolean[]> completed = new HashMap<>();

CommandSender sender;
String state;

ArrayList<String> pluginNames = new ArrayList<>();
ArrayList<Boolean> deleteConfigs = new ArrayList<>();

public DeleteQueue(CommandSender sender, String state) {
  this.sender = sender;
  this.state = state;
}

public void addPlugin(String pluginName, boolean deleteConfig) {
  pluginNames.add(pluginName);
  deleteConfigs.add(deleteConfig);
}

public boolean isQueued(String pluginName) {
  return pluginNames.contains(pluginName);
}

/**
 WARNING: This method should only be executed asynchronously! */
public void executeDelete() {
  completed.put(sender, new Boolean[pluginNames.size()]);

  for (int i = 0; i < pluginNames.size(); i++) {

    int finalI = i;
    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
      deletePlugin(sender, state, pluginNames.get(finalI), deleteConfigs.get(finalI));
      Boolean[] progress = completed.get(sender);
      progress[finalI] = true;
      completed.put(sender, progress);
    });
  }

  //blocks until all plugins are deleted.
  boolean contains = false;
  while (!contains) {

    try {
      Thread.sleep(100);
    } catch (InterruptedException ignore) {
    }

    Boolean[] progress = completed.get(sender);
    for (Boolean bool : progress) {
      if (bool == null || !bool) {
        contains = false;
        break;
      } else contains = true;
    }

  }

  new Log(sender, "deletePlugin.reloadWarn").log();
}

}
