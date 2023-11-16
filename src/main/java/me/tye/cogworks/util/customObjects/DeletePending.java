package me.tye.cogworks.util.customObjects;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static me.tye.cogworks.util.Util.*;

public class DeletePending {

private final String filePath;
private final String deleteName;
private final String deleteTime;

/**
 An object used for storing data on deleted files, so they could be restored before full deletion.
 @param filePath The path to the file.
 */
public DeletePending(Path filePath) {
  this.filePath = serverFolder.toPath().relativize(filePath).toString();

  String randName;
  //generates a random file name & makes sure that it doesn't already exist
  Random rand = new Random();
  while (true) {
    int i = rand.nextInt(0, 100000);
    StringBuilder rands = new StringBuilder(String.valueOf(i));

    while (rands.length() > 6) {
      rands.append("0");
    }

    if (!new File(deletePending.getPath()+File.separator+filePath.getFileName()+rands).exists()) {
      randName = rands.toString();
      break;
    }

  }

  this.deleteName = filePath.getFileName()+"-"+randName;
  this.deleteTime = LocalDateTime.now().toString();
}

/**
 An object used for storing data on deleted files, so they could be restored before full deletion.
 * @param filePath The original path the file had before it was deleted, relative to the server folder.
 * @param deleteName The path to the delete folder for the file, relative to the server folder.
 * @param deleteTime The time that the file was deleted.
 */
public DeletePending(String filePath, String deleteName, String deleteTime) {
  this.filePath = filePath;
  this.deleteName = deleteName;
  this.deleteTime = deleteTime;
}

/**
 Appends this object to the deleteData file.<br>
 If the addition causes the file size to go over the limit, older files will be deleted to clear space.<br>
 If the file itself is bigger than the limit then no reserve files will be clear & the file will be deleted outright.*/
public void append() throws IOException {
  if (!deleteData.exists()) {
    return;
  }

  //if the deleted file is bigger than the max size specified by the user then it isn't reserved.
  long maxSize = parseSize(getConfig("keepDeleted.size"));
  if (maxSize <= Files.size(getFilePath())) {
    Files.delete(getFilePath());
    return;
  }

  clear(Files.size(getFilePath()));

  Collection<DeletePending> deletePendings = read();
  deletePendings.add(this);
  write(deletePendings);

  //moves the file, but deletes the copy if it failed to fully move.
  try {
    Files.move(getFilePath(), getDeletePath());

  } catch (IOException e) {
    remove();
    if (getDeletePath().toFile().exists()) {
      deleteFileOrFolder(getDeletePath());
    }

    throw new IOException(e);
  }
}

/**
 Deletes the file restore file & the entry in the deleteData file. */
public void delete() throws IOException {
  deleteFileOrFolder(getDeletePath());
  remove();
}

/**
 Restores the file to the given path.
 @param restorePath The path to restore the file to.
 @throws IOException If there was an error moving the deleted file, or removing the delete data.
 @throws InvalidPathException If the parent directory of the given path does not exist, or in the case where a dir was given, if that doesn't exist*/
public void restore(Path restorePath) throws IOException, InvalidPathException {
  Path newPath = serverFolder.toPath().resolve(restorePath);

  //if no name was given then use the deleted name.
  if (newPath.toFile().isDirectory() || newPath.toFile().exists()) {
    newPath = newPath.resolve(getFilePath().getFileName());
  }

  //if the provided path doesn't exist, throw an error.
  if (!newPath.getParent().toFile().exists()) {
    throw new InvalidPathException(restorePath.toString(), "That path does not exist.");
  }

  Files.move(getDeletePath(), newPath);
  remove();
}

/**
 Removes the given deletePending object from the delete pending file.
 * @throws IOException If there was an error reading or writing to the file.
 */
private void remove() throws IOException {
  List<DeletePending> deletePendings = read();
  for (int i = 0; i < deletePendings.size(); i++) {
    if (!deletePendings.get(i).getDeletePath().getFileName().equals(getDeletePath().getFileName())) {
      continue;
    }

    deletePendings.remove(i);
    break;
  }
  write(deletePendings);
}

/**
 * @return The old path relative to the server folder.
 */
public Path getRelativePath() {
  return Path.of(filePath);
}

/**
 * @return The current file path to the old location.
 */
public Path getFilePath() {
  return serverFolder.toPath().resolve(filePath);
}

/**
 * @return The current file path to the reserved file.
 */
public Path getDeletePath() {
  return deletePending.toPath().resolve(deleteName);
}

/**
 * @return The time that the file was deleted.
 */
public LocalDateTime getDeleteTime() {
  return LocalDateTime.parse(deleteTime);
}



/**
 Reads the data from the deleteData file.
 @return The data of the files that are pending deletion. */
public static Collection<DeletePending> read(@Nullable CommandSender sender) {
  try {
    return read();

  } catch (IOException e) {
    new Log(sender, "delete.readFail").setException(e).log();
    return new ArrayList<>();
  }
}

/**
 Reads the data from the deleteData file.
 @return The data of the files that are pending deletion. */
public static List<DeletePending> read() throws IOException {
  try (JsonReader jsonReader = new JsonReader(new FileReader(deleteData))) {
    JsonElement fileJson = JsonParser.parseReader(jsonReader);
    if (fileJson.isJsonNull()) {
      return new ArrayList<>();
    }

    JsonArray jsonArray = fileJson.getAsJsonArray();

    List<DeletePending> deletePendings = new ArrayList<>();
    for (JsonElement element : jsonArray) {
      JsonObject jsonObject = element.getAsJsonObject();
      deletePendings.add(new DeletePending(jsonObject.get("filePath").getAsString(), jsonObject.get("deleteName").getAsString(), jsonObject.get("deleteTime").getAsString()));
    }
    return deletePendings;
  }
}

/**
 Writes data to the deleteData file.<br><b>Warning! This will overwrite any data currently in the file!</b><br>Use "new DeleteData().append()" to add an entry to the file.
 * @param deletePendings The data to write to the file.
 * @throws IOException If there was an error writing data to the file.
 */
private static void write(Collection<DeletePending> deletePendings) throws IOException {
  try (FileWriter fileWriter = new FileWriter(deleteData)) {
    GsonBuilder gson = new GsonBuilder();
    gson.setPrettyPrinting();
    gson.create().toJson(deletePendings, fileWriter);
  }
}


/**
 Gets the old file paths of all the deleted files relative to the server folder.<br>
 If two files have the same path, then the delete time is appended to the path. This won't change the restore name.
 * @return The unique file paths off all the deleted files relative to the server folder.
 * @throws IOException If there was an error reading the data from the delete data file.
 */
public static List<String> getUniqueOldPaths() throws IOException {
  return uniquePaths().values().stream().toList();
}

/**
 Gets the DeletePending object that corresponds to the given path.
 @param uniqueOldPath The given path.
 @return The DeletePending object or null if no match could be found.
 @throws IOException If there was an error reading the data from the deletePending file. */
public static DeletePending getDelete(String uniqueOldPath) throws IOException {
  HashMap<DeletePending,String> deletes = uniquePaths();
  for (DeletePending pending : deletes.keySet()) {
    if (!deletes.get(pending).equals(uniqueOldPath)) {
      continue;
    }

    return pending;
  }

  return null;
}


/**
 Deletes files until there is enough space for the new one. This does nothing if there is enough space to store the file
 @param fileSize The size of the new file.
 @throws IOException If there was an error getting any file sizes, or reading from the deleteData. */
public static void clear(long fileSize) throws IOException {
  long newSize = Files.size(Path.of(deletePending.getAbsolutePath()))+fileSize;
  if (newSize < parseSize(getConfig("keepDeleted.size"))) {
    return;
  }

  //deletes files until there is enough space for the new one.
  for (DeletePending pending : DeletePending.getOldest()) {
    pending.delete();

    long currentSize = Files.size(Path.of(deletePending.getAbsolutePath()))+fileSize;
    if (currentSize > parseSize(getConfig("keepDeleted.size"))) {
      continue;
    }

    break;
  }

}

/**
 Gets the deleted data sorted by date, with the oldest first.
 @return A list of the deleted data sorted by date.
 @throws IOException If there was an error reading the data. */
private static ArrayList<DeletePending> getOldest() throws IOException {
  ArrayList<DeletePending> sortedPendings = new ArrayList<>();
  for (int i = 0; i < read().size(); i++) {

    DeletePending oldest;

    if (sortedPendings.isEmpty()) {
      oldest = null;
    }
    else {
      oldest = sortedPendings.get(sortedPendings.size()-1);
    }

    for (DeletePending pending : read()) {
      if (oldest == null) {
        oldest = pending;
        break;
      }

      if (!oldest.getDeleteTime().isAfter(pending.getDeleteTime())) {
        continue;
      }

      oldest = pending;
      break;
    }

    sortedPendings.add(oldest);
  }
  return sortedPendings;
}

private static HashMap<DeletePending,String> uniquePaths() throws IOException {
  List<String> deletePaths = new ArrayList<>();

  List<DeletePending> deletePendings = read();
  for (DeletePending pending : deletePendings) {

    //checks if the any other files have this file path.
    if (!deletePaths.contains(pending.filePath)) {
      deletePaths.add(pending.getRelativePath().toString());
      continue;
    }

    for (int j = 0; j < deletePaths.size(); j++) {
      DeletePending deleteObject = deletePendings.get(j);

      //continues if the filePath is unique.
      if (!deletePaths.get(j).equals(deleteObject.getRelativePath().toString())) {
        continue;
      }

      //adds the time of deletion to the filePath
      deletePaths.set(j, deleteObject.getRelativePath()+"-"+deleteObject.getDeleteTime());
    }

    deletePaths.add(pending.getRelativePath()+"-"+pending.getDeleteTime());
  }

  HashMap<DeletePending,String> deletes = new HashMap<>();
  for (int i = 0; i < deletePaths.size(); i++) {
    deletes.put(deletePendings.get(i), deletePaths.get(i));
  }
  return deletes;
}

}
