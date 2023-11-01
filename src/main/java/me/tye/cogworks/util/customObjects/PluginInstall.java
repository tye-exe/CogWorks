package me.tye.cogworks.util.customObjects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.tye.cogworks.util.Plugins;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;

import static me.tye.cogworks.util.Util.*;

public class PluginInstall {

private final CommandSender sender;
private final JsonObject plugin;
private final JsonArray pluginVersions;

private Integer chosenVersion = null;
private int chosenFile;

public PluginInstall(CommandSender sender, JsonObject plugin, JsonArray pluginVersions) {
  this.sender = sender;
  this.plugin = plugin;
  this.pluginVersions = pluginVersions;
}
//
//public void search() {
//  clearResponse(sender);
//
//  HashMap<JsonObject,JsonArray> validPlugins = search.getValidPlugins();
//  ArrayList<JsonObject> validPluginKeys = search.getValidPluginKeys();
//
//
//  //the error logging is handled within the modrinthSearch method
//  if (validPlugins.isEmpty() || validPluginKeys.isEmpty()) {
//    return;
//  }
//
//  //lets the user select the plugin to search
//  new Log(sender, "pluginInstall.pluginSelect").log();
//  for (int i = 0; validPluginKeys.size() > i; i++) {
//    JsonObject project = validPluginKeys.get(i);
//    TextComponent projectName = new TextComponent(i+1+": "+project.get("title").getAsString());
//    projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/"+project.get("project_type").getAsString()+"/"+project.get("slug").getAsString())));
//    projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
//    projectName.setUnderlined(true);
//    sender.spigot().sendMessage(projectName);
//  }
//
//  setResponse(sender, new ChatParams(sender, "pluginSelect").setValidPlugins(validPlugins).setValidPluginKeys(validPluginKeys));
//}

public void execute() {

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


  JsonObject chosenPlugin = validPluginKeys.get(chosen-1);
  JsonArray compatibleFiles = validPlugins.get(validPluginKeys.get(chosen-1));
  ArrayList<JsonObject> filesToChoose = new ArrayList<>();

  if (compatibleFiles.isEmpty()) {
    new Log(sender, stateOld, "noFiles").log();
    return;
  }

  if (compatibleFiles.size() == 1) {
    String title = chosenPlugin.get("title").getAsString();
    new Log(sender, stateOld, "start").setPluginName(title).log();

    JsonArray files = compatibleFiles.get(0).getAsJsonObject().get("files").getAsJsonArray();
    if (files.isEmpty()) {
      new Log(sender, stateOld, "noFiles").log();
      return;
    }

    if (files.size() == 1) {
      Plugins.installModrinthDependencies(sender, stateOld, compatibleFiles.get(0).getAsJsonObject(), title);
      if (Plugins.installModrinthPlugin(sender, stateOld, files))
        new Log(sender, stateOld, "finish").setPluginName(title).log();

    }

    // if there are more than one file for that version you get prompted to choose which one(s) to install
    else {
      new Log(sender, stateOld, "versionFiles").log();

      int i = 1;
      for (JsonElement je : files) {
        JsonObject jo = je.getAsJsonObject();
        filesToChoose.add(jo);
        TextComponent projectName = new TextComponent(i+": "+(jo.get("primary").getAsBoolean() ? net.md_5.bungee.api.ChatColor.BLUE+getLang("pluginFileSelect.primary")+net.md_5.bungee.api.ChatColor.GREEN+" " : "")+jo.get("filename").getAsString());
        projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        sender.spigot().sendMessage(projectName);
        i++;
      }
      setResponse(sender, new ChatParams(sender, "pluginFileSelect").setChooseable(filesToChoose).setPlugin(chosenPlugin).setPluginVersion(compatibleFiles.get(0).getAsJsonObject()));
      return;
    }


  }
  else {
    new Log(sender, stateOld, "pluginSelect").log();
    int i = 1;
    for (JsonElement je : compatibleFiles) {
      JsonObject jo = je.getAsJsonObject();
      filesToChoose.add(jo);
      TextComponent projectName = new TextComponent(i+": "+jo.get("name").getAsString()+" : "+jo.get("version_number").getAsString());
      projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/"+validPluginKeys.get(chosen-1).get("project_type").getAsString()+"/"+validPluginKeys.get(chosen-1).get("slug").getAsString()+"/version/"+jo.get("version_number").getAsString())));
      projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
      projectName.setUnderlined(true);
      sender.spigot().sendMessage(projectName);
      i++;
    }
    setResponse(sender, new ChatParams(sender, "pluginVersionSelect").setChooseable(filesToChoose).setPlugin(chosenPlugin));
    return;
  }


  int chosenVersion = parseNumInput(sender, stateOld, message, name, filesToChoose.size(), 1);
  if (chosenVersion == -1)
    return;

  JsonObject chosen = filesToChoose.get(chosenVersion).getAsJsonObject();
  String title = chosenPlugin.get("title").getAsString();
  new Log(sender, stateOld, "start").setPluginName(title).log();


  JsonArray files = chosen.get("files").getAsJsonArray();
  if (files.isEmpty()) {
    new Log(sender, stateOld, "noFiles").log();
    return;
  }

  if (files.size() == 1) {
    Plugins.installModrinthDependencies(sender, stateOld, chosen, title);
    if (Plugins.installModrinthPlugin(sender, stateOld, files))
      new Log(sender, stateOld, "finish").setPluginName(title).log();

    // if there are more than one file for that version you get prompted to choose which one(s) to install
  }
  else {
    new Log(sender, stateOld, "versionFiles").log();

    int i = 1;
    for (JsonElement je : files) {
      JsonObject jo = je.getAsJsonObject();
      filesToChoose.add(jo);
      TextComponent projectName = new TextComponent(i+": "+(jo.get("primary").getAsBoolean() ? net.md_5.bungee.api.ChatColor.BLUE+getLang("pluginFileSelect.primary")+net.md_5.bungee.api.ChatColor.GREEN+" " : "")+jo.get("filename").getAsString());
      projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
      sender.spigot().sendMessage(projectName);
      i++;
    }
    params.reset(sender, "pluginFileSelect").setChooseable(filesToChoose).setPlugin(chosenPlugin).setPluginVersion(chosen);
    if (sender instanceof Player)
      response.put(sender.getName(), params);
    else
      response.put("~", params);
    return;
  }

  response.remove(name);
}

public int getVersionSize() {
  return pluginVersions.size();
}

public void setChosen(int chosen) {
  this.chosen = chosen;
}
}
