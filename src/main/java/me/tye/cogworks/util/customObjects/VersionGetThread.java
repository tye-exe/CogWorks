package me.tye.cogworks.util.customObjects;

import com.google.gson.JsonElement;

import java.util.concurrent.Callable;

import static me.tye.cogworks.util.Plugins.modrinthAPI;
import static me.tye.cogworks.util.Util.mcVersion;
import static me.tye.cogworks.util.Util.serverSoftware;

public class VersionGetThread implements Callable<JsonElement> {

String slug;

/**
 Gets the plugin version information from Modrinth.
 @param slug The slug of the plugin project. */
public VersionGetThread(String slug) {
  this.slug = slug;
}

@Override
public JsonElement call() throws Exception {
  return modrinthAPI(null, "", "https://api.modrinth.com/v2/project/"+slug+"/version?loaders=[%22"+serverSoftware+"%22]&game_versions=[%22"+mcVersion+"%22]", "GET");
}
}
