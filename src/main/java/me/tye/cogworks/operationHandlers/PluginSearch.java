package me.tye.cogworks.operationHandlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.tye.cogworks.util.Plugins;
import me.tye.cogworks.util.customObjects.ChatParams;
import me.tye.cogworks.util.customObjects.Log;
import me.tye.cogworks.util.customObjects.ModrinthSearch;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.HashMap;

import static me.tye.cogworks.util.Util.clearResponse;
import static me.tye.cogworks.util.Util.setResponse;

public class PluginSearch implements PluginInstallSelector {

private final CommandSender sender;
private final HashMap<JsonObject,JsonArray> validPlugins;
private final ArrayList<JsonObject> validPluginKeys;

/**
 Searches up plugins on Modrinth using the given query. Then entering the user into the CogWorks chat interaction system for plugin selection.
 @param sender The user.
 @param query  The query to search on Modrinth. */
public PluginSearch(CommandSender sender, String query) {
  this.sender = sender;

  ModrinthSearch search = Plugins.modrinthSearch(sender, "pluginInstall", query);
  this.validPlugins = search.getValidPlugins();
  this.validPluginKeys = search.getValidPluginKeys();

  execute();
}

/**
 Enters the sender given on object creation into the CogWorks chat interaction system for selecting a plugin from Modrinth based on the given query. */
@Override
public void execute() {
  clearResponse(sender);

  //the error logging is handled within the modrinthSearch method
  if (validPlugins.isEmpty() || validPluginKeys.isEmpty()) {
    return;
  }

  //lets the user select the plugin to search
  new Log(sender, "pluginInstall.pluginSelect").log();
  for (int i = 0; validPluginKeys.size() > i; i++) {
    JsonObject project = validPluginKeys.get(i);
    TextComponent projectName = new TextComponent(i+1+": "+project.get("title").getAsString());
    projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/"+project.get("project_type").getAsString()+"/"+project.get("slug").getAsString())));
    projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
    projectName.setUnderlined(true);
    sender.spigot().sendMessage(projectName);
  }

  setResponse(sender, new ChatParams(sender, "pluginSelect").setPluginSearch(this));
}

@Override
public void resume() {

}

/**
 @return The size of the valid plugins returned from Modrinth */
public int getKeysSize() {
  return validPluginKeys.size();
}

/**
 Initiates the pluginInstall sequence for the plugin at the given index.
 @param pluginKeyIndex The given index. */
public void selectPlugin(int pluginKeyIndex) {
  JsonObject chosenPlugin = validPluginKeys.get(0);
  new PluginInstall(sender, this, chosenPlugin, validPlugins.get(chosenPlugin));
}
}
