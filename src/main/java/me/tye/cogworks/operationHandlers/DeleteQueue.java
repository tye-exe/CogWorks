package me.tye.cogworks.operationHandlers;

import me.tye.cogworks.util.Plugins;
import me.tye.cogworks.util.customObjects.ChatParams;
import me.tye.cogworks.util.customObjects.DependencyInfo;
import me.tye.cogworks.util.customObjects.Log;
import me.tye.cogworks.util.customObjects.PluginData;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static me.tye.cogworks.util.Plugins.deletePlugin;
import static me.tye.cogworks.util.Util.*;

public class DeleteQueue {

private final CommandSender sender;

private final ArrayList<String> queuedPluginNames = new ArrayList<>();
private final ArrayList<Boolean> queuedDeleteConfigs = new ArrayList<>();

private final ArrayList<String> evalPluginNames = new ArrayList<>();
private final ArrayList<Boolean> evalDeleteConfigs = new ArrayList<>();
private final ArrayList<Boolean> evalDeleteDepends = new ArrayList<>();

/**
 Creates a new instance of the DeleteQueue object, which is used for deleting plugins & getting a user response on delete options by using the CogWorks chat system.
 @param sender     The sender performing the deletion.
 @param pluginName The given plugin name. */
public DeleteQueue(@NotNull CommandSender sender, @NotNull String pluginName) {
  this.sender = sender;
  evalPluginNames.add(pluginName);
  evalDeleteConfigs.add(null);
  evalDeleteDepends.add(null);
}

/**
 <b>Warning: This method should only be executed asynchronously!</b><br>
 This method goes though all the plugins that are to be evaluated and adds them to the delete queue. Once all plugins have been evaluated then the plugins are deleted.<br>
 Evaluation involves prompting the user about deleting the plugin config files if present, & prompting the user if plugins that depend on this one to function should be added to the evaluation queue or not.<br>
 When user input is required, an example of this is if plugin config files should be deleted, the method will send the sender a message & set the chat interaction system to wait for a response. */
public void evaluatePlugins() {
  clearResponse(sender);

  while (!evalPluginNames.isEmpty()) {
    String pluginName = evalPluginNames.get(0);
    Boolean deleteConfig = evalDeleteConfigs.get(0);
    Boolean deleteDepends = evalDeleteDepends.get(0);

    //if the pluginData couldn't be gotten then it is skipped
    PluginData pluginData = PluginData.getFromName(pluginName);
    if (pluginData == null) {
      new Log(sender, "deletePlugin.noSuchPlugin").setPluginName(pluginName).log();
      evalPluginNames.remove(0);
      evalDeleteConfigs.remove(0);
      evalDeleteDepends.remove(0);
      continue;
    }

    List<PluginData> whatDependsOn = pluginData.getWhatDependsOn();

    //if the plugin doesn't exist or is already queued then it is skipped.
    if (!PluginData.registered(pluginName) || queuedPluginNames.contains(pluginName)) {
      evalPluginNames.remove(0);
      evalDeleteConfigs.remove(0);
      evalDeleteDepends.remove(0);
      continue;
    }


    //if the plugin doesn't have a config folder then there will be no prompt for a choice on deleting them.
    if (!Plugins.hasConfigFolder(pluginName)) {
      deleteConfig = false;
    }

    //if no other plugins depend on this plugin to function then there will be no prompt for a choice on deleting them.
    if (whatDependsOn.isEmpty()) {
      deleteDepends = false;
    }


    //if no choice has been set for configs yet the user is prompted for a choice.
    if (deleteConfig == null) {
      new Log(sender, "deletePlugin.deleteConfig").setPluginName(pluginName).log();
      ChatParams params = new ChatParams(sender, "deletePluginConfig").setDeleteQueue(this);
      setResponse(sender, params);
      return;
    }

    //if no choice has been set for deleting the plugins that depend on this one then the user is prompted for a choice.
    if (deleteDepends == null) {
      ArrayList<String> names = new ArrayList<>(whatDependsOn.size());
      for (PluginData data : whatDependsOn) {
        names.add(data.getName());
      }

      new Log(sender, "deletePlugin.dependsOn").setPluginNames(names).setPluginName(pluginName).log();
      ChatParams params = new ChatParams(sender, "deletePluginsDepend").setDeleteQueue(this);
      setResponse(sender, params);
      return;
    }


    //if the user chose to delete the dependencies then they are added to the delete eval queue.
    if (deleteDepends) {
      for (PluginData dependsData : whatDependsOn) {
        evalPluginNames.add(dependsData.getName());
        evalDeleteConfigs.add(null);
        evalDeleteDepends.add(null);
      }
    }
    //set the dependencies to not be resolved by ADR.
    else {
      for (PluginData dependsData : whatDependsOn) {
        for (DependencyInfo dependency : dependsData.getDependencies()) {

          dependency.setAttemptADR(false);
          dependsData.modifyDependency(dependency);

          try {
            PluginData.modify(dependsData);
          } catch (IOException e) {
            new Log(sender, "deletePlugin.writeNoADR").setDepName(dependency.getName()).setPluginName(dependsData.getName()).setException(e).log();
          }
        }
      }
    }


    //when all the options have been set then the plugin is added to the delete queue.
    queuedPluginNames.add(pluginName);
    queuedDeleteConfigs.add(deleteConfig);

    evalPluginNames.remove(0);
    evalDeleteConfigs.remove(0);
    evalDeleteDepends.remove(0);
  }

  //when all the plugins have been evaluated then the delete is executed.

  //goes through all plugins & deletes them synchronously.
  for (int i = 0; i < queuedPluginNames.size(); i++) {
    int finalI = i;

    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () ->
        deletePlugin(sender, "deletePlugin", queuedPluginNames.get(finalI), queuedDeleteConfigs.get(finalI))
    );
  }
}

/**
 Sets the delete config for the current plugin being evaluated.
 @param deleteConfig Whether to delete the plugin configs. */
public void setCurrentEvalDeleteConfig(boolean deleteConfig) {
  evalDeleteConfigs.set(0, deleteConfig);
}

/**
 Sets whether to delete the plugins that depend on the current plugin being evaluated to function.
 @param deleteDepends Whether to delete the plugins that depend on the current plugin. */
public void setCurrentEvalDeleteDepends(boolean deleteDepends) {
  evalDeleteDepends.set(0, deleteDepends);
}

/**
 Adds the given plugin to the eval queue.
 @param pluginName The given plugin. */
public void addPluginToEval(String pluginName) {
  evalPluginNames.add(pluginName);
  evalDeleteConfigs.add(null);
  evalDeleteDepends.add(null);
}

}
