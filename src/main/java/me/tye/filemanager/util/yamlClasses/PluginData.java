package me.tye.filemanager.util.yamlClasses;

import java.util.ArrayList;
import java.util.Map;

public class PluginData {

    String fileName;
    String name;
    String version;
    ArrayList<DependencyInfo> dependencies = new ArrayList<>();
    ArrayList<DependencyInfo> softDependencies = new ArrayList<>();

    public PluginData(String fileName, Map<String, Object> data) {
        this.fileName = fileName;
        this.name = data.get("name").toString();
        this.version = data.get("version").toString();

        if (data.get("depend") != null) {
            for (String dependency : (ArrayList<String>) data.get("depend")) {
                this.dependencies.add(new DependencyInfo(dependency, null));
            }
        }
        if (data.get("softdepend") != null) {
            for (String dependency : (ArrayList<String>) data.get("softdepend")) {
                this.softDependencies.add(new DependencyInfo(dependency, null));
            }
        }
    }

    public String getFileName() {
        return fileName;
    }
    public String getName() {
        return name;
    }
    public String getVersion() {
        return version;
    }
    public ArrayList<DependencyInfo> getDependencies() {
        return dependencies;
    }
    public ArrayList<DependencyInfo> getSoftDependencies() {
        return softDependencies;
    }

    @Override
    public String toString() {
        return "fileName:" + fileName + " name:" + name + " version:" + version + " dependencies:" + dependencies + " softDependencies:" + softDependencies;
    }
}
