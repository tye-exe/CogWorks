package me.tye.cogworks.util;

import com.google.gson.JsonElement;
import org.bukkit.Bukkit;

import java.util.concurrent.Callable;

import static me.tye.cogworks.commands.PluginCommand.modrinthAPI;

public class VersionGetThread implements Callable<JsonElement> {

String slug;

public VersionGetThread(String slug) {
  this.slug = slug;
}

@Override
public JsonElement call() throws Exception {
  String mcVersion = Bukkit.getVersion().split(": ")[1];
  mcVersion = mcVersion.substring(0, mcVersion.length()-1);
  String serverSoftware = Bukkit.getServer().getVersion().split("-")[1].toLowerCase();
  return modrinthAPI(null, "", "https://api.modrinth.com/v2/project/"+slug+"/version?loaders=[%22"+serverSoftware+"%22]&game_versions=[%22"+mcVersion+"%22]", "GET");
}
}
