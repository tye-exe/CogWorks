package me.tye.cogworks.operationHandlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.tye.cogworks.util.Plugins;
import me.tye.cogworks.util.customObjects.ChatParams;
import me.tye.cogworks.util.customObjects.Log;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static me.tye.cogworks.util.Util.*;

public class PluginInstall {

private final CommandSender sender;
private final PluginInstallSelector installSelector;
private final JsonObject plugin;
private final JsonArray pluginVersions;

private JsonObject chosenVersion = null;
private final JsonArray chosenFiles = new JsonArray();

/**
 Creates a PluginInstall object that will guide a user through installing a plugin.
 Note that the creation of this object will invoke the "execute()" method.
 @param sender          The sender performing the installation.
 @param installSelector The previous instance the user used to select the plugin. This is for allowing the user to go back out of the installation.
 @param plugin          The plugin the user wants to install.
 @param pluginVersions  The version of the plugin the user wants to install. */
public PluginInstall(@NotNull CommandSender sender, @NotNull PluginInstallSelector installSelector, @NotNull JsonObject plugin, @NotNull JsonArray pluginVersions) {
  this.sender = sender;
  this.installSelector = installSelector;
  this.plugin = plugin;
  this.pluginVersions = pluginVersions;
  execute();
}

/**
 Enters the user into the plugin install sequence, using the CogWorks interaction system for user input when required. */
public void execute() {
  clearResponse(sender);

  if (pluginVersions.isEmpty()) {
    new Log(sender, "pluginInstall.noVersions").log();
    installSelector.resume();
    return;
  }

  if (pluginVersions.size() == 1) {
    chosenVersion = pluginVersions.get(0).getAsJsonObject();
  }

  //prompts a user to choose a version of the plugin to download if there are multiple
  if (chosenVersion == null) {

    //loops though plugin versions that are compatible.
    int i = 1;
    for (JsonElement je : pluginVersions) {
      JsonObject jo = je.getAsJsonObject();
      TextComponent projectName = new TextComponent(i+": "+jo.get("name").getAsString()+" : "+jo.get("version_number").getAsString());
      //creates the url that point towards this particular project version & allows the user to click the message to open it.
      projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/"+plugin.get("project_type").getAsString()+"/"+plugin.get("slug").getAsString()+"/version/"+jo.get("version_number").getAsString())));
      projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
      projectName.setUnderlined(true);
      sender.spigot().sendMessage(projectName);
      i++;
    }
    setResponse(sender, new ChatParams(sender, "pluginVersionSelect").setPluginInstall(this));
    return;
  }


  JsonArray files = chosenVersion.get("files").getAsJsonArray();

  //if the plugin has no files to install for this version
  if (files.isEmpty()) {
    chosenVersion = null;

    if (pluginVersions.size() == 1) {
      new Log(sender, "pluginInstall.oneVersionNoFiles").log();
      installSelector.resume();
      return;
    }

    new Log(sender, "pluginInstall.noFiles").log();
    execute();
    return;
  }


  //if there is more than one file then the user can select which ones to install
  if (chosenFiles.isEmpty()) {
    if (files.size() == 1) {
      chosenFiles.add(files.get(0));
    }

    else {
      int i = 1;
      for (JsonElement je : files) {
        JsonObject jo = je.getAsJsonObject();
        TextComponent projectName = new TextComponent(i+": "+(jo.get("primary").getAsBoolean() ? net.md_5.bungee.api.ChatColor.BLUE+getLang("pluginFileSelect.primary")+net.md_5.bungee.api.ChatColor.GREEN+" " : "")+jo.get("filename").getAsString());
        projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        sender.spigot().sendMessage(projectName);
        i++;
      }
      setResponse(sender, new ChatParams(sender, "pluginFileSelect").setPluginInstall(this));
      return;
    }
  }


  if (!Plugins.installModrinthDependencies(sender, "pluginInstall", chosenVersion, chosenVersion.get("name").getAsString())) {
    new Log(sender, "pluginInstall.badDeps").log();
    //TODO: make deps uninstall
    return;
  }

  Plugins.installModrinthPlugin(sender, "pluginInstall", chosenFiles);
}

/**
 * @return How many valid version their are for this plugin.
 */
public int getVersionSize() {
  return pluginVersions.size();
}

/**
 * @return The amount of files the select version has, or 0 if the selected version is null.
 */
public int getFilesAmount() {
  if (chosenVersion == null)
    return 0;
  return chosenVersion.get("files").getAsJsonArray().size();
}

/**
 Sets the chosen version.
 * @param chosenVersion The index of the chosen version.
 */
public void setChosenVersion(int chosenVersion) {
  this.chosenVersion = pluginVersions.get(chosenVersion).getAsJsonObject();
}

/**
 Sets the chosen files.
 * @param chosenFiles A collection of the indexes of the chosen files.
 */
public void setChosenFiles(Collection<Integer> chosenFiles) {
  for (Integer chosen : chosenFiles) {
    this.chosenFiles.add(chosenVersion.get("files").getAsJsonArray().get(chosen).getAsJsonObject());
  }
}

}
