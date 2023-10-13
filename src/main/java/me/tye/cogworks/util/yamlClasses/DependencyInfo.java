package me.tye.cogworks.util.yamlClasses;

public class DependencyInfo {

String name;
String version;

/**
 Stores information about the dependencies of a plugin.
 @param name    The name of the plugin.
 @param version The version of the plugin. */
public DependencyInfo(String name, String version) {
  this.name = name;
  this.version = version;
}

/**
 @return The plugin name of the dependency. */
public String getName() {
  return name;
}

/**
 @return The plugin version of the dependency. */
public String getVersion() {
  return version;
}

@Override
public String toString() {
  return "Name: \""+name+"\". Version: \""+version+"\".";
}
}
