package me.tye.filemanager.util;

public class UrlFilename {

    String url;
    String filename;

    public UrlFilename(String url, String filename) {
        this.url = url;
        this.filename = filename;
    }

    public String getUrl() {
        return url;
    }

    public String getFilename() {
        return filename;
    }
}
