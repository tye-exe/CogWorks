package me.tye.cogworks.util.customObjects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.tye.cogworks.util.customObjects.exceptions.ModrinthAPIException;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;

import static me.tye.cogworks.util.Plugins.modrinthAPI;
import static me.tye.cogworks.util.Util.*;

public class PluginBrowse implements PluginInstallSelector {

private final CommandSender sender;
private ModrinthSearch returned;
int offset;
int maxChoice = 0;

public PluginBrowse(CommandSender sender, int offset) {
  this.sender = sender;
  this.offset = offset;
}

public void execute() {
  if (!sender.hasPermission("cogworks.plugin.ins.modrinth"))
    return;

  returned = getPlugins();
  ArrayList<JsonObject> validPluginKeys = returned.getValidPluginKeys();
  HashMap<JsonObject,JsonArray> validPlugins = returned.getValidPlugins();

  if (validPluginKeys.isEmpty() || validPlugins.isEmpty()) {
    return;
  }

  maxChoice = validPlugins.size()+1;

  new Log(sender, "pluginBrowse.pluginSelect").log();
  int i = 0;

  if (offset >= 1) {
    sender.sendMessage(ChatColor.GREEN+String.valueOf(i)+": ^");
    i++;
  }

  while (validPluginKeys.size() > i) {
    JsonObject project = validPluginKeys.get(i);
    TextComponent projectName = new TextComponent(i+1+": "+project.get("title").getAsString());
    projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/"+project.get("project_type").getAsString()+"/"+project.get("slug").getAsString())));
    projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
    projectName.setUnderlined(true);
    sender.spigot().sendMessage(projectName);
    i++;
  }

  sender.sendMessage(ChatColor.GREEN+String.valueOf(i+1)+": v");

  setResponse(sender, new ChatParams(sender, "pluginBrowse").setPluginBrowse(this));


  //    //if the user decides to install a plugin
  //    JsonObject plugin = validPluginKeys.get(chosen-1);
  //    JsonArray compatibleFiles = validPlugins.get(validPluginKeys.get(chosen-1));
  //    ArrayList<JsonObject> chooseableFiles = new ArrayList<>();
  //
  //    if (compatibleFiles.isEmpty()) {
  //      new Log(sender, state, "noFiles").log();
  //
  //    } else if (compatibleFiles.size() == 1) {
  //      JsonArray files = compatibleFiles.get(0).getAsJsonObject().get("files").getAsJsonArray();
  //      if (files.isEmpty()) {
  //        new Log(sender, state, "noFiles").log();
  //        return;
  //      }
  //
  //      //if there is only one file to install from that version it installs it
  //      if (files.size() == 1) {
  //        Plugins.installModrinthDependencies(sender, state, compatibleFiles.get(0).getAsJsonObject(), plugin.get("title").getAsString());
  //        if (Plugins.installModrinthPlugin(sender, state, files))
  //          new Log(sender, state, "finish").setPluginName(plugin.get("title").getAsString()).log();
  //
  //        // if there are more than one file for that version you get prompted to choose which one(s) to install
  //      } else {
  //        new Log(sender, state, "versionFiles").log();
  //
  //        int i = 1;
  //        for (JsonElement je : files) {
  //          JsonObject jo = je.getAsJsonObject();
  //          chooseableFiles.add(jo);
  //          TextComponent projectName = new TextComponent(i+": "+(jo.get("primary").getAsBoolean() ? net.md_5.bungee.api.ChatColor.BLUE+getLang("pluginFileSelect.primary")+net.md_5.bungee.api.ChatColor.GREEN+" " : "")+jo.get("filename").getAsString());
  //          projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
  //          sender.spigot().sendMessage(projectName);
  //          i++;
  //        }
  //        params.reset(sender, "pluginFileSelect").setChooseable(chooseableFiles).setPlugin(plugin).setPluginVersion(compatibleFiles.get(0).getAsJsonObject());
  //        if (sender instanceof Player)
  //          response.put(sender.getName(), params);
  //        else
  //          response.put("~", params);
  //        return;
  //      }
  //
  //
  //    } else {
  //      new Log(sender, state, "pluginSelect").log();
  //      int i = 1;
  //      for (JsonElement je : compatibleFiles) {
  //        JsonObject jo = je.getAsJsonObject();
  //        chooseableFiles.add(jo);
  //        TextComponent projectName = new TextComponent(i+": "+jo.get("name").getAsString()+" : "+jo.get("version_number").getAsString());
  //        projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/"+validPluginKeys.get(chosen-1).get("project_type").getAsString()+"/"+validPluginKeys.get(chosen-1).get("slug").getAsString()+"/version/"+jo.get("version_number").getAsString())));
  //        projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
  //        projectName.setUnderlined(true);
  //        sender.spigot().sendMessage(projectName);
  //        i++;
  //      }
  //
  //      params.reset(sender, "pluginVersionSelect").setChooseable(chooseableFiles).setPlugin(plugin);
  //      if (sender instanceof Player)
  //        response.put(sender.getName(), params);
  //      else
  //        response.put("~", params);
  //      return;
  //    }
  //    response.remove(name);

}


public void install(int chosen) {
  new PluginInstall(sender, returned.getValidPluginKeys().get(chosen-1), returned.getValidPlugins().get(returned.getValidPluginKeys().get(chosen-1)));

}

public void execute(int newOffset) {
  offset = newOffset;
  execute();
}

public void resume() {
  execute();
}

public int getOffset() {
  return offset;
}

public int getMaxChoice() {
  return maxChoice;
}

/**
 Gets the most 10 popular plugins from modrinth with the specified offset
 @return The plugins at that offset in a ModrinthSearch object. */
private ModrinthSearch getPlugins() {
  ArrayList<JsonObject> validPluginKeys = new ArrayList<>();
  HashMap<JsonObject,JsonArray> validPlugins = new HashMap<>();

  try {

    JsonElement relevantPlugins = modrinthAPI(sender, "ModrinthAPI", "https://api.modrinth.com/v2/search?query=&facets=[[%22versions:"+mcVersion+"%22],[%22categories:"+serverSoftware+"%22]]&offset="+offset, "GET");
    JsonArray hits = relevantPlugins.getAsJsonObject().get("hits").getAsJsonArray();
    if (hits.isEmpty()) {
      return new ModrinthSearch(validPluginKeys, validPlugins);
    }

    StringBuilder projectUrl = new StringBuilder("https://api.modrinth.com/v2/projects?ids=[");
    for (JsonElement je : hits) {
      JsonObject hit = je.getAsJsonObject();
      projectUrl.append("%22").append(hit.get("project_id").getAsString()).append("%22,");
    }

    JsonArray pluginProjects = modrinthAPI(sender, "ModrinthAPI", projectUrl.substring(0, projectUrl.length()-1)+"]", "GET").getAsJsonArray();
    if (pluginProjects.isEmpty()) {
      return new ModrinthSearch(validPluginKeys, validPlugins);
    }

    ExecutorService executorService = Executors.newCachedThreadPool();
    ArrayList<Future<JsonElement>> futures = new ArrayList<>();

    //gets the compatible versions for the plugins.
    for (JsonElement je : pluginProjects) {
      futures.add(executorService.submit(new VersionGetThread(je.getAsJsonObject().get("id").getAsString())));
    }

    //loops though the compatible versions.
    for (Future<JsonElement> future : futures) {
      JsonArray validVersions = future.get().getAsJsonArray();
      if (validVersions.isEmpty())
        continue;

      //adds the plugin to valid plugins objects if there is a version of the plugin the supports the current server software & version.
      for (JsonElement projectElement : pluginProjects) {
        JsonObject project = projectElement.getAsJsonObject();
        if (project.get("id").equals(validVersions.get(0).getAsJsonObject().get("project_id"))) {
          validPluginKeys.add(project);

          for (JsonElement jel : validVersions) {
            JsonArray array = validPlugins.get(project);
            if (array == null)
              array = new JsonArray();
            array.add(jel.getAsJsonObject());
            validPlugins.put(project, array);
          }
        }
      }
    }

  } catch (MalformedURLException | ModrinthAPIException | ExecutionException | InterruptedException e) {
    new Log(sender, "pluginBrowse", "browsePluginErr").setLevel(Level.WARNING).setException(e).log();
  }

  return new ModrinthSearch(validPluginKeys, validPlugins);
}

}
