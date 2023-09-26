package me.tye.cogworks.util;

import org.checkerframework.checker.nullness.qual.Nullable;

public class FileData {

    int currentLine;
    String searchPhrase;
    int searchInstance;
    boolean deleteMode;

    public FileData(int currentLine, @Nullable String searchPhrase, int searchInstance, boolean deleteMode) {
        setCurrentLine(currentLine);
        setSearchPhrase(searchPhrase);
        this.searchInstance = searchInstance;
        this.deleteMode = deleteMode;
    }

    /**
     * @return The current phrase that the player is searching for.
     */
    public String getSearchPhrase() {
        return searchPhrase;
    }

    /**
     * @return The instance of the phrase that the player is searching for.
     */
    public int getSearchInstance() {
        return searchInstance;
    }

    /**
     * @return Current line the player is viewing on the top row.
     */
    public int getCurrentLine() {
        return currentLine;
    }

    /**
     * @return Whether the user is in delete mode.
     */
    public boolean getDeleteMode() {
        return deleteMode;
    }

    /**
     * @param lineNumber Sets the current line the player is viewing on the top row.
     *                   Default: 1
     * @return Modified FileData object.
     */
    public FileData setCurrentLine(int lineNumber) {
        if (lineNumber < 1) lineNumber = 1;
        this.currentLine = lineNumber;
        return this;
    }

    /**
     * @param searchInstance Sets the instance of the phrase that the player is searching for.
     *                     default: 1
     * @return Modified FileData object.
     */
    public FileData setSearchInstance(int searchInstance) {
        this.searchInstance = searchInstance;
        return this;
    }

    /**
     * @param searchPhrase Sets the current phrase that the player is searching for.
     * @return Modified FileData object.
     */
    public FileData setSearchPhrase(String searchPhrase) {
        if (searchPhrase == null) searchPhrase = "";
        if (searchPhrase.startsWith("\u200B")) searchPhrase = searchPhrase.substring(1);
        this.searchPhrase = searchPhrase;
        return this;
    }

    /**
     * @param deleteMode Sets whether the player is in delete mode.
     * @return Modified FileData object.
     */
    public FileData setDeleteMode(boolean deleteMode) {
        this.deleteMode = deleteMode;
        return this;
    }
}
