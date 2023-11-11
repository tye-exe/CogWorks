package me.tye.cogworks.util.customObjects.dataClasses;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import me.tye.cogworks.util.customObjects.Log;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;

import static me.tye.cogworks.util.Util.*;

public class DeletePending implements Serializable {
@Serial
private static final long serialVersionUID = 87120L;

private final String relativePath;
private final String filePath;
private final String randName;
private final String deleteTime;

/**
 An object used for storing data on deleted files, so they could be restored before full deletion.
 @param relativePath The path to the file from the server folder.
 @param filePath     The path to the file.
 @param randName     The random name of the file inside the delete pending folder. */
public DeletePending(Path relativePath, Path filePath, String randName) {
  this.relativePath = relativePath.toString();
  this.filePath = filePath.toString();
  this.randName = randName;
  this.deleteTime = LocalDateTime.now().toString();
}

/**
 An object used for storing data on deleted files, so they could be restored before full deletion.
 @param relativePath The path to the file from the server folder.
 @param filePath     The true name of the file.
 @param randName     The random name of the file inside the delete pending folder. */
public DeletePending(String relativePath, String filePath, String randName, String deleteTime) {
  this.relativePath = relativePath;
  this.filePath = filePath;
  this.randName = randName;
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
  if (maxSize <= Files.size(getOldFilePath())) {
    Files.delete(getOldFilePath());
    return;
  }

  clear(Files.size(getOldFilePath()));

  ArrayList<DeletePending> deletePendings = read();
  deletePendings.add(this);
  write(deletePendings);

  //moves the file, but deletes the copy if it failed to fully move.
  try {
    Files.move(getOldFilePath(), getDeletedFilePath());

  } catch (IOException e) {
    remove(this);
    if (getDeletedFilePath().toFile().exists()) {
      deleteFileOrFolder(getDeletedFilePath());
    }

    throw new IOException(e);
  }
}

/**
 Deletes files until there is enough space for the new one. This does nothing if there is enough space to store the file
 @param fileSize The size of the new file.
 @throws IOException If there was an error getting any file sizes, or reading from the deleteData. */
private void clear(long fileSize) throws IOException {
  long newSize = Files.size(Path.of(deletePending.getAbsolutePath()))+fileSize;
  if (newSize < parseSize(getConfig("keepDeleted.size"))) {
    return;
  }

  //deletes files until there is enough space for the new one.
  for (DeletePending pending : getOldest()) {
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
private ArrayList<DeletePending> getOldest() throws IOException {
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

/**
 Deletes the file restore file & the entry in the deleteData file. */
public void delete() throws IOException {
  deleteFileOrFolder(getDeletedFilePath());

  ArrayList<DeletePending> pendings = read();
  for (int i = 0; i < pendings.size(); i++) {
    if (!pendings.get(i).getRandName().equals(getRandName())) {
      continue;
    }

    pendings.remove(i);
    break;
  }

  write(pendings);
}

public Path getRelativePath() {
  return Path.of(relativePath);
}

public Path getOldFilePath() {
  return Path.of(filePath);
}

public Path getDeletedFilePath() {
  return Path.of(deletePending.getPath()+File.separator+getOldFilePath().getFileName()+randName);
}

public String getRandName() {
  return randName;
}

public LocalDateTime getDeleteTime() {
  return LocalDateTime.parse(deleteTime);
}


/**
 Reads the data from the deleteData file.
 @return The data of the files that are pending deletion. */
public static ArrayList<DeletePending> read(@Nullable CommandSender sender) {
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
public static ArrayList<DeletePending> read() throws IOException {
  try (JsonReader jsonReader = new JsonReader(new FileReader(deleteData))) {
    JsonElement fileJson = JsonParser.parseReader(jsonReader);
    if (fileJson.isJsonNull()) {
      return new ArrayList<>();
    }

    JsonArray jsonArray = fileJson.getAsJsonArray();

    ArrayList<DeletePending> deletePendings = new ArrayList<>();
    for (JsonElement element : jsonArray) {
      JsonObject jsonObject = element.getAsJsonObject();
      deletePendings.add(new DeletePending(jsonObject.get("relativePath").getAsString(), jsonObject.get("filePath").getAsString(), jsonObject.get("randName").getAsString(), jsonObject.get("deleteTime").getAsString()));
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
 Removes the given deletePending object from the delete pending file.
 * @param deletePending The given deletePending object to remove.
 * @throws IOException If there was an error reading or writing to the file.
 */
private static void remove(DeletePending deletePending) throws IOException {
  ArrayList<DeletePending> deletePendings = read();
  for (int i = 0; i < deletePendings.size(); i++) {
    if (!deletePendings.get(i).getRandName().equals(deletePending.getRandName())) {
      continue;
    }

    deletePendings.remove(i);
    break;
  }
  write(deletePendings);
}

}
