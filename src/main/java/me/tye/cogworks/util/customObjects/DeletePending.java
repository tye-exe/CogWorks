package me.tye.cogworks.util.customObjects;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;

import static me.tye.cogworks.util.Util.*;

public class DeletePending implements Serializable {
@Serial
private static final long serialVersionUID = 87120L;

private final Path relativePath;
private final Path filePath;
private final String randName;
private final LocalDateTime deleteTime;

/**
 An object used for storing data on deleted files, so they could be restored before full deletion.
 @param relativePath The path to the file from the server folder.
 @param filePath     The true name of the file.
 @param randName     The random name of the file inside the delete pending folder. */
public DeletePending(Path relativePath, Path filePath, String randName) {
  this.relativePath = relativePath;
  this.filePath = filePath;
  this.randName = randName;
  this.deleteTime = LocalDateTime.now();
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
  if (maxSize < Files.size(filePath)) {
    Files.delete(filePath);
    return;
  }

  clear(Files.size(filePath));

  ArrayList<DeletePending> deletePendings = read();
  deletePendings.add(this);
  write(deletePendings);

  //moves the file, but deletes the copy if it failed to fully move.
  try {
    //TODO: add dir support
    Files.move(filePath, Path.of(deletePending.getPath()+File.separator+randName));
  } catch (IOException e) {
    remove(this);
    if (!new File(deletePending.getPath()+File.separator+randName).exists()) {
      throw new IOException(e);
    }

    Files.delete(Path.of(deletePending.getPath()+File.separator+randName));
  }
}

/**
 Deletes files until there is enough space for the new one.
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

    if (!sortedPendings.isEmpty()) {
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
public void delete() {
  try {
    Files.delete(Path.of(deletePending.getAbsolutePath()+File.separator+getRandName()));

    ArrayList<DeletePending> pendings = read();
    for (int i = 0; i < pendings.size(); i++) {
      if (!pendings.get(i).getRandName().equals(getRandName())) {
        continue;
      }

      pendings.remove(i);
      break;
    }

    write(pendings);

  } catch (IOException e) {
    //TODO: error handling
    throw new RuntimeException(e);
  }
}

public Path getRelativePath() {
  return relativePath;
}

public Path getFilePath() {
  return filePath;
}

public String getRandName() {
  return randName;
}

public LocalDateTime getDeleteTime() {
  return deleteTime;
}


/**
 Reads the data from the deleteData file.
 @return The data of the files that are pending deletion. */
public static ArrayList<DeletePending> read(@Nullable CommandSender sender) {
  if (!deleteData.exists()) {
    return new ArrayList<>();
  }

  try (Stream<String> lines = Files.lines(Path.of(deleteData.getAbsolutePath()))) {
    if (lines.toList().isEmpty()) {
      return new ArrayList<>();
    }

  } catch (IOException e) {
    throw new RuntimeException(e);
  }

  try (FileInputStream fileInputStream = new FileInputStream(deleteData)) {
    ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
    return (ArrayList<DeletePending>) objectInputStream.readObject();

  } catch (ClassNotFoundException | SecurityException | IOException e) {
    new Log(sender, "delete.readFail").setException(e).log();
    return new ArrayList<>();
  }
}

/**
 Reads the data from the deleteData file.
 @return The data of the files that are pending deletion. */
public static ArrayList<DeletePending> read() throws IOException {
  if (!deleteData.exists()) {
    return new ArrayList<>();
  }

  if (Files.size(Path.of(deleteData.getAbsolutePath())) == 0) {
    return new ArrayList<>();
  }

  try (FileInputStream fileInputStream = new FileInputStream(deleteData)) {
    ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
    return (ArrayList<DeletePending>) objectInputStream.readObject();

  } catch (ClassNotFoundException | SecurityException | IOException e) {
    throw new IOException(e);
  }
}

/**
 Writes data to the deleteData file.<br><b>Warning! This will overwrite any data currently in the file!</b><br>Use "new DeleteData().append()" to add an entry to the file.
 * @param deletePendings The data to write to the file.
 * @throws IOException If there was an error writing data to the file.
 */
private static void write(Collection<DeletePending> deletePendings) throws IOException {

  try (FileOutputStream fileOutputStream = new FileOutputStream(deleteData)) {
    ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
    objectOutputStream.writeObject(deletePendings);
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
