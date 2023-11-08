package me.tye.cogworks.util.customObjects;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static me.tye.cogworks.util.Util.deleteData;

public class DeletePending implements Serializable {
@Serial
private static final long serialVersionUID = 87120L;

private final Path relativePath;
private final Path fileName;
private final String randName;
private final LocalDateTime deleteTime;

/**
 An object used for storing data on deleted files, so they could be restored before full deletion.
 @param relativePath The path to the file from the server folder.
 @param fileName     The true name of the file.
 @param randName     The random name of the file inside the delete pending folder. */
public DeletePending(Path relativePath, Path fileName, String randName) {
  this.relativePath = relativePath;
  this.fileName = fileName;
  this.randName = randName;
  this.deleteTime = LocalDateTime.now();
}

/**
 Appends this object to the deleteData file. */
public void append() throws IOException {
  if (!deleteData.exists()) {
    return;
  }

  ArrayList<DeletePending> deletePendings = read(null);
  deletePendings.add(this);

  try (FileOutputStream fileOutputStream = new FileOutputStream(deleteData)) {
    ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
    objectOutputStream.writeObject(deletePendings);

  }
}

/**
 Reads the data from the deleteData file.
 @return The data of the files that are pending deletion. */
public static ArrayList<DeletePending> read(@Nullable CommandSender sender) {
  if (!deleteData.exists()) {
    return new ArrayList<>();
  }

  try (FileInputStream fileInputStream = new FileInputStream(deleteData)) {
    ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
    return (ArrayList<DeletePending>) objectInputStream.readObject();

  } catch (ClassNotFoundException | SecurityException | IOException e) {
    new Log(sender, "delete.readFail").log();
    return new ArrayList<>();
  }
}


public Path getRelativePath() {
  return relativePath;
}

public Path getFileName() {
  return fileName;
}

public String getRandName() {
  return randName;
}

public LocalDateTime getDeleteTime() {
  return deleteTime;
}
}
