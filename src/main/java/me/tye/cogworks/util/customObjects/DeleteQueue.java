package me.tye.cogworks.util.customObjects;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import static me.tye.cogworks.util.Plugins.deletePlugin;
import static me.tye.cogworks.util.Util.plugin;

public class DeleteQueue {

public static HashMap<CommandSender,Boolean[]> completed = new HashMap<>();

CommandSender sender;
String state;

ArrayList<String> pluginNames = new ArrayList<>();
ArrayList<Boolean> deleteConfigs = new ArrayList<>();

/**
 Creates an object that stores plugins that can be deleted in the future.
 @param sender The sender performing the deletion.
 @param state  The state the sender is in. */
public DeleteQueue(CommandSender sender, String state) {
  this.sender = sender;
  this.state = state;
}

/**
 Adds a plugin to the list of plugins to be deleted.
 @param pluginName   The name of the plugin.
 @param deleteConfig Whether or not to delete the configs. */
public void addPlugin(String pluginName, boolean deleteConfig) {
  pluginNames.add(pluginName);
  deleteConfigs.add(deleteConfig);
}

/**
 Checks if a plugin is in the queue to be deleted or not.
 @param pluginName The name of the plugin.
 @return True if the plugin is in the queue. */
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

  if (Arrays.stream(completed.get(sender)).toList().contains(true)) new Log(sender, "deletePlugin.reloadWarn").log();
}

}
