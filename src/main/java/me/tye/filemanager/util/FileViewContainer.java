package me.tye.filemanager.util;

public class FileViewContainer {

    String searchPhrase;
    int searchInstance;

    public FileViewContainer(String searchPhrase, int searchInstance) {
        if (searchPhrase == null ) searchPhrase = "";
        if (searchPhrase.startsWith("\uFFFF")) searchPhrase = searchPhrase.substring(1);

        this.searchPhrase = searchPhrase;
        this.searchInstance = searchInstance;
    }

    public String getSearchPhrase() {
        return searchPhrase;
    }

    public int getSearchInstance() {
        return searchInstance;
    }
}
