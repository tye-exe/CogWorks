package me.tye.filemanager;

import me.tye.filemanager.util.PathHolder;
import me.tye.filemanager.util.FileData;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static me.tye.filemanager.FileManager.itemProperties;
import static org.bukkit.plugin.java.JavaPlugin.getPlugin;

public class FileGui implements Listener {

    //TODO: fix scroll and search not going to the correct lines.
    //TODO: opening persmission file has the line number to -1. Myabe check it when setting it?
    public static HashMap<UUID, FileData> fileData = new HashMap<>();
    public static HashMap<String, PathHolder> position = new HashMap<>();

    public static void openFolder(Player player) {
        if (!FileGui.fileData.containsKey(player.getUniqueId())) FileGui.fileData.put(player.getUniqueId(), new FileData(1, 1, "", 1));

        Inventory gui = Bukkit.createInventory(player, 54, ChatColor.BLUE+"~"+position.get(player.getName()).getRelativePath()+ChatColor.GOLD+" $");
        List<Path> paths;
        try {
            paths = Files.list(Path.of(position.get(player.getName()).getCurrentPath())).toList();
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED+"There was an error trying to get the files in that folder.\nPlease see the console for error message and report this.");
            e.printStackTrace();
            return;
        }

        //creates file and folder objects, then sorts them
        ArrayList<ItemStack> files = new ArrayList<>();
        ArrayList<ItemStack> folders = new ArrayList<>();
        for (Path path : paths) {
            if (Files.isDirectory(path)) {
                ItemStack item = itemProperties(new ItemStack(Material.YELLOW_WOOL), path.getFileName().toString(), List.of("Folder"));
                folders.add(item);
            }
            else {
                ItemStack item = itemProperties(new ItemStack(Material.WHITE_WOOL), path.getFileName().toString(), List.of("File"));
                files.add(item);
            }
        }
        folders.addAll(files);

        ArrayList<ItemStack> content = new ArrayList<>();
        for (int i = 0; i <= 53; i++) {
            if (i == 0) {
                content.add(itemProperties(new ItemStack(Material.ARROW), "Up", List.of("Goes up a file.")));
            } else if (i == 4) {
                content.add(itemProperties(new ItemStack(Material.BARRIER), "Exit", List.of("Closes the gui.")));
            } else if (i > 8 && folders.size() > i-9) {
                content.add(folders.get(i-9));
            } else {
                content.add(new ItemStack(Material.AIR));
            }
        }
        gui.setContents(content.toArray(new ItemStack[0]));
        player.openInventory(gui);
    }

    public static void openFile(Player player) {
        FileData data =  fileData.get(player.getUniqueId());

        int lineNumber = data.getLineNumber();
        String searchPhrase = data.getSearchPhrase();

        Inventory gui = Bukkit.createInventory(player, 54, ChatColor.BLUE+"~"+position.get(player.getName()).getRelativePath().substring(0, position.get(player.getName()).getRelativePath().length()-1)+ChatColor.GOLD+" $");

        File file = new File(position.get(player.getName()).getCurrentPath());
        if (!file.isFile()) return;

        ArrayList<String> lines = new ArrayList<>();
        try {
            BufferedReader fileReader = new BufferedReader(new FileReader(file));

            String text;
            while ((text = fileReader.readLine()) != null)
                lines.add(text);

        } catch (IOException e) {
            player.sendMessage(ChatColor.RED+"There was an error trying to open that file.\nPlease see the console for error message and report this.");
            e.printStackTrace();
            return;
        }

        if (lineNumber > lines.size()) lineNumber = lines.size();
        if (lineNumber < 0) lineNumber = 1;

        ArrayList<ItemStack> content = new ArrayList<>();
        for (int i = 0; i <= 8; i++) {
            if (i == 0) {
                ItemStack down = itemProperties(new ItemStack(Material.TIPPED_ARROW), "Scroll Down", List.of("Scrolls down in the file."));
                ItemMeta downMeta = down.getItemMeta();
                downMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "type"), PersistentDataType.STRING, "down");
                down.setItemMeta(downMeta);
                content.add(down);
            }
            else if (i == 1) {
                ItemStack up = itemProperties(new ItemStack(Material.TIPPED_ARROW), "Scroll Up", List.of("Scrolls up in the file."));
                ItemMeta upMeta = up.getItemMeta();
                upMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "type"), PersistentDataType.STRING, "down");
                up.setItemMeta(upMeta);
                content.add(up);
            }
            else if (i == 2) {
                content.add(itemProperties(new ItemStack(Material.OAK_SIGN), "Line number: "+lineNumber, List.of("The line number of the first visible line.")));
            }
            else if (i == 3) {
                String name;
                if (searchPhrase.isEmpty()) name = "Search";
                else name = "Search: "+searchPhrase;
                content.add(itemProperties(new ItemStack(Material.WRITABLE_BOOK), name, List.of("Finds instances of certain words.","Left click: select search word.","Right click: moves to searched words.")));
            }
            else if (i == 4) {
                ItemStack goTo = itemProperties(new ItemStack(Material.SPECTRAL_ARROW), "Go to", List.of("Go to a certain line by number."));
                ItemMeta goToMeta = goTo.getItemMeta();
                goToMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "type"), PersistentDataType.STRING, "goto");
                goTo.setItemMeta(goToMeta);
                content.add(goTo);
            }
            else if (i == 7) {
                content.add(itemProperties(new ItemStack(Material.ARROW), "Back", List.of("Exit the file.")));
            }
            else if (i == 8) {
                content.add(itemProperties(new ItemStack(Material.BARRIER), "Exit", List.of("Closes the gui.")));
            }
            else {
                content.add(itemProperties(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " ", null));
            }
        }

        for (int i = 0; i <= 4; i++) {
            if (lines.size()-1 >= i+lineNumber-1) {
                //max 50 chars per item
                String line = lines.get(i+lineNumber-1);
                for (int ii = 0; ii <= 8; ii++) {
                    String paperName = line.substring(0, Math.min(35, line.length()));
                    line = line.substring(Math.min(35, line.length()));
                    if (!paperName.equals("")) {
                        ItemStack paper = itemProperties(new ItemStack(Material.PAPER), paperName, null);
                        if (!searchPhrase.isEmpty() && paperName.contains(searchPhrase)) {
                            paper = itemProperties(new ItemStack(Material.FILLED_MAP), paperName.replace(searchPhrase, ChatColor.YELLOW + searchPhrase + ChatColor.WHITE), null);
                        }

                        ItemMeta paperMeta = paper.getItemMeta();
                        paperMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "line"), PersistentDataType.INTEGER, i+lineNumber);
                        paperMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "offset"), PersistentDataType.INTEGER, ii);
                        paperMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "edited"), PersistentDataType.BOOLEAN, false);
                        paperMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "type"), PersistentDataType.STRING, "text");
                        paper.setItemMeta(paperMeta);
                        content.add(paper);
                    }
                    else content.add(itemProperties(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE), " ", null));
                }
            } else {
                for (int ii = 0; ii <= 8; ii++) {
                    content.add(itemProperties(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE), " ", null));
                }
            }
        }

        gui.setContents(content.toArray(new ItemStack[0]));
        player.openInventory(gui);
    }

    public static void editFile(Player player, ItemStack editedText) {
        if (editedText.getItemMeta() == null) return;
        PathHolder pathHolder = position.get(player.getName());
        Integer line = editedText.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "line"), PersistentDataType.INTEGER);
        Integer offset = editedText.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "offset"), PersistentDataType.INTEGER);
        if (line == null|| offset == null) return;

        try {
            BufferedReader br = new BufferedReader(new FileReader(pathHolder.getCurrentPath()));
            StringBuilder content = new StringBuilder();

            String text;
            int i = 1;
            while ((text = br.readLine()) != null) {
                if (line == i) {
                    int startOfReplace = 35*offset;
                    String start = text.substring(0, startOfReplace);
                    String end = text.substring(Math.min(startOfReplace+35, text.length()));
                    text = start+editedText.getItemMeta().getDisplayName()+end;
                }
                i++;
                content.append(text).append("\n");
            };
            br.close();
            Files.writeString(Path.of(pathHolder.getCurrentPath()), content.toString());
        } catch (Exception ex) {
            player.sendMessage(ChatColor.RED+"There was an error trying to edit that file.\nPlease see the console for error message and report this.");
            ex.printStackTrace();
        }
    }

    @EventHandler
    public void stopStealing(InventoryClickEvent e) {
        if (position.containsKey(e.getWhoClicked().getName())) e.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() != Material.BARRIER || !position.containsKey(player.getName())) return;
            player.closeInventory();
            position.remove(player.getName());
            fileData.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void exit(InventoryCloseEvent e) {
        //position.remove(e.getPlayer().getName());
        //fileData.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void useFolder(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null || e.getCurrentItem().getItemMeta().getLore() == null || !position.containsKey(player.getName())) return;
            if (e.getCurrentItem().getItemMeta().getLore().get(0).equals("Folder")) {
                PathHolder pathHolder = position.get(player.getName());
                pathHolder.setCurrentPath(pathHolder.getCurrentPath() + File.separator + e.getCurrentItem().getItemMeta().getDisplayName());
                openFolder(player);
            }
        }
    }

    @EventHandler
    public void upFolder(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null || e.getCurrentItem().getType() != Material.ARROW || !position.containsKey(player.getName())) return;
            if (!e.getCurrentItem().getItemMeta().getDisplayName().equals("Back") && !e.getCurrentItem().getItemMeta().getDisplayName().equals("Up")) return;
            PathHolder pathHolder = position.get(player.getName());
            pathHolder.setCurrentPath(Path.of(pathHolder.getCurrentPath()).getParent().toString());
            openFolder(player);
        }
    }

    @EventHandler
    public void useFile(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null || e.getCurrentItem().getItemMeta().getLore() == null || !position.containsKey(player.getName())) return;
            if (e.getCurrentItem().getItemMeta().getLore().get(0).equals("File")) {
                PathHolder pathHolder = position.get(player.getName());
                pathHolder.setCurrentPath(pathHolder.getCurrentPath() + File.separator + e.getCurrentItem().getItemMeta().getDisplayName());
                fileData.put(player.getUniqueId(), fileData.get(player.getUniqueId()).setLineNumber(1));
                openFile(player);
            }
        }
    }

    @EventHandler
    public void fileScroll(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null || e.getCurrentItem().getType() != Material.TIPPED_ARROW || !position.containsKey(player.getName())) return;
            String type = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "type"), PersistentDataType.STRING);
            if (type == null) return;
            FileData data = fileData.get(player.getUniqueId());
            if (type.equals("down")) {
                fileData.put(player.getUniqueId(), data.setLineNumber(Math.min(data.getMaxLine(), data.getLineNumber()+5)));
            }
            if (type.equals("up")) {
                fileData.put(player.getUniqueId(), data.setLineNumber(Math.min(1, data.getLineNumber()-5)));
            }
            openFile(player);
        }
    }

    @EventHandler
    public void fileGoTo(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null || e.getCurrentItem().getType() != Material.SPECTRAL_ARROW || !position.containsKey(player.getName())) return;
            if (checkType(e.getCurrentItem(), "goto")) return;
            FileData data = fileData.get(player.getUniqueId());

            ItemStack paper = itemProperties(new ItemStack(Material.PAPER), String.valueOf(data.getLineNumber()), null);
            ItemMeta meta = paper.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "type"), PersistentDataType.STRING, "goto");
            paper.setItemMeta(meta);

            new AnvilGUI.Builder()
                    .plugin(JavaPlugin.getPlugin(FileManager.class))
                    .preventClose()
                    .title("Min: 1, Max: "+data.getMaxLine())
                    .itemLeft(paper)
                    .onClick((slot, stateSnapshot) -> {
                        if (slot == AnvilGUI.Slot.OUTPUT) {
                            return List.of(AnvilGUI.ResponseAction.close());
                        }
                        return Collections.emptyList();
                    })
                    .onClose(stateSnapshot -> {
                        if (stateSnapshot.getOutputItem().getItemMeta() != null) {
                            try {
                                int line = Integer.parseInt(stateSnapshot.getOutputItem().getItemMeta().getDisplayName().trim());
                                fileData.put(stateSnapshot.getPlayer().getUniqueId(), fileData.get(stateSnapshot.getPlayer().getUniqueId()).setLineNumber(line));
                            } catch (Exception ignore) {}
                        }
                        openFile(stateSnapshot.getPlayer());
                    })
                    .open(player);
        }
    }

    @EventHandler
    public void search(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null || e.getCurrentItem().getType() != Material.WRITABLE_BOOK || !position.containsKey(player.getName())) return;

            if (e.isLeftClick()) {
                ItemStack paper = itemProperties(new ItemStack(Material.PAPER), "\uFFFF", null);

                new AnvilGUI.Builder()
                        .plugin(JavaPlugin.getPlugin(FileManager.class))
                        .preventClose()
                        .title("Search:")
                        .itemLeft(paper)
                        .itemOutput(paper)
                        .onClick((slot, stateSnapshot) -> {
                            if (slot == AnvilGUI.Slot.OUTPUT) {
                                return List.of(AnvilGUI.ResponseAction.close());
                            }
                            return Collections.emptyList();
                        })
                        .onClose(stateSnapshot -> {
                            fileData.put(stateSnapshot.getPlayer().getUniqueId(), fileData.get(stateSnapshot.getPlayer().getUniqueId()).setSearchPhrase(stateSnapshot.getOutputItem().getItemMeta().getDisplayName()));
                            openFile(stateSnapshot.getPlayer());
                        })
                        .open(player);
            }
            else {
                try {
                    String searchPhrase = FileGui.fileData.get(player.getUniqueId()).getSearchPhrase();
                    BufferedReader fileReader = new BufferedReader(new FileReader(position.get(player.getName()).getCurrentPath()));

                    if (searchPhrase.isEmpty())  {
                        player.playNote(player.getLocation(), Instrument.PLING, Note.sharp(2, Note.Tone.F));
                        return;
                    }

                    String text;
                    int i = 1;
                    int instance = FileGui.fileData.get(player.getUniqueId()).getSearchInstance();
                    int instances = 1;
                    int firstInstanceLine = 1;

                    while ((text = fileReader.readLine()) != null) {
                        if (text.contains(searchPhrase)) {
                            if (firstInstanceLine == 1) firstInstanceLine = i;
                            if (instances == instance) {
                                fileData.put(player.getUniqueId(), fileData.get(player.getUniqueId()).setSearchInstance(fileData.get(player.getUniqueId()).getSearchInstance()+1));
                                openFile(player);
                                return;
                            }
                            instances++;
                        }
                        i++;
                    }

                    if (instances == 1) {
                        player.playNote(player.getLocation(), Instrument.PLING, Note.sharp(2, Note.Tone.F));
                        return;
                    }

                    fileData.put(player.getUniqueId(), fileData.get(player.getUniqueId()).setSearchInstance(firstInstanceLine));
                    openFile(player);

                } catch (IOException ex) {
                    player.sendMessage(ChatColor.RED + "There was an error trying to open that file.\nPlease see the console for error message and report this.");
                    ex.printStackTrace();
                }
            }

        }
    }

    @EventHandler
    public void edit(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null || e.getCurrentItem().getType() != Material.PAPER || !position.containsKey(player.getName())) return;
            if (checkType(e.getCurrentItem(), "text")) return;
            if (Boolean.TRUE.equals(e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "edited"), PersistentDataType.BOOLEAN))) return;

            ItemStack paper = e.getCurrentItem();
            ItemMeta meta = paper.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "edited"), PersistentDataType.BOOLEAN, true);
            meta.getPersistentDataContainer().set(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "type"), PersistentDataType.STRING, "edit");
            paper.setItemMeta(meta);

            new AnvilGUI.Builder()
                    .plugin(JavaPlugin.getPlugin(FileManager.class))
                    .preventClose()
                    .title("File editor:")
                    .itemLeft(e.getCurrentItem())
                    .itemOutput(paper)
                    .onClick((slot, stateSnapshot) -> {
                        if (slot == AnvilGUI.Slot.OUTPUT) {
                            return List.of(AnvilGUI.ResponseAction.close());
                        }
                        return Collections.emptyList();
                    })
                    .onClose(stateSnapshot -> {
                        editFile(stateSnapshot.getPlayer(), stateSnapshot.getOutputItem());
                        openFile(stateSnapshot.getPlayer());
                    })
                    .open(player);
        }
    }

    public static boolean checkType(ItemStack item, String type) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return true;
        return !type.equals(meta.getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "type"), PersistentDataType.STRING));
    }
}
