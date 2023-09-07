package me.tye.filemanager.util;

import org.checkerframework.checker.nullness.qual.Nullable;

public class FileData {

    int lineNumber;
    int maxLine;
    String searchPhrase;
    int searchInstance;

    public FileData(int lineNumber, int maxLine, @Nullable String searchPhrase, int searchInstance) {
        setLineNumber(lineNumber);
        setMaxLine(maxLine);
        setSearchPhrase(searchPhrase);
        this.searchInstance = searchInstance;
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
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * @return The maximum line of the current file the player is viewing.
     */
    public int getMaxLine() {
        return maxLine;
    }


    /**
     * @param lineNumber Sets the current line the player is viewing on the top row.
     *                   Default: 1
     * @return Modified FileData object.
     */
    public FileData setLineNumber(int lineNumber) {
        if (lineNumber < 1) lineNumber = 1;
        this.lineNumber = lineNumber;
        return this;
    }

    /**
     * @param maxLine Sets the maximum line of the current file the player is viewing.
     *                default: 1
     * @return Modified FileData object.
     */
    public FileData setMaxLine(int maxLine) {
        if (maxLine < 1) maxLine = 1;
        this.maxLine = maxLine;
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
        if (searchPhrase.startsWith("\uFFFF")) searchPhrase = searchPhrase.substring(1);
        this.searchPhrase = searchPhrase;
        return this;
    }
}
