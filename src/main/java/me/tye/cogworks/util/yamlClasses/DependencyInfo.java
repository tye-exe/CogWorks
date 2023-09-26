package me.tye.cogworks.util.yamlClasses;

public class DependencyInfo {

    String name;
    String version;

    public DependencyInfo(String name, String version) {
        this.name = name;
        this.version = version;
    }

    public String getName() {
        return name;
    }
    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return " name:" + name + "version:" + version;
    }
}
