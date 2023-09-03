package me.tye.filemanager.commands.fileCommand;

import me.tye.filemanager.FileManager;
import me.tye.filemanager.util.PathHolder;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static me.tye.filemanager.FileManager.itemProperties;
import static me.tye.filemanager.commands.fileCommand.FileCommand.position;
import static org.bukkit.plugin.java.JavaPlugin.getPlugin;

public class FileGui implements Listener {
    public static void openFolder(Player player) {
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

    public static void openFile(Player player, int lineNumber) {
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
                content.add(itemProperties(new ItemStack(Material.ARROW), "Back", List.of("Exit the file.")));
            }
            else if (i == 2) {
                content.add(itemProperties(new ItemStack(Material.OAK_SIGN), "Line number: "+lineNumber, List.of("The line number of the first visible line.")));
            }
            else if (i == 3) {
                //content.add(itemProperties(new ItemStack(Material.WRITABLE_BOOK), "Search (WIP)", List.of("Finds instances of certain words.")));
                content.add(itemProperties(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), "", null));
            }
            else if (i == 4) {
                content.add(itemProperties(new ItemStack(Material.BARRIER), "Exit", List.of("Closes the gui.")));
            }
            else if (i == 5) {
                ItemStack goTo = itemProperties(new ItemStack(Material.SPECTRAL_ARROW), "Go to", List.of("Go to a certain line by number."));
                ItemMeta goToMeta = goTo.getItemMeta();
                goToMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "max"), PersistentDataType.INTEGER, lines.size());
                goToMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "currentLine"), PersistentDataType.INTEGER, lineNumber);
                goToMeta.getPersistentDataContainer().set(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "type"), PersistentDataType.STRING, "goto");
                goTo.setItemMeta(goToMeta);
                content.add(goTo);
            }
            else if(i == 7) {
                ItemStack up = itemProperties(new ItemStack(Material.TIPPED_ARROW), "Scroll Up", List.of("Scrolls up in the file."));
                ItemMeta upMeta = up.getItemMeta();
                upMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "line"), PersistentDataType.INTEGER, Math.max(1, lineNumber-5));
                up.setItemMeta(upMeta);
                content.add(up);
            }
            else if (i == 8) {
                ItemStack down = itemProperties(new ItemStack(Material.TIPPED_ARROW), "Scroll Down", List.of("Scrolls down in the file."));
                ItemMeta downMeta = down.getItemMeta();
                downMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "line"), PersistentDataType.INTEGER, Math.min(lines.size()-4, lineNumber+5));
                down.setItemMeta(downMeta);
                content.add(down);
            }
            else {
                content.add(itemProperties(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), "", null));
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
                        ItemMeta paperMeta = paper.getItemMeta();
                        paperMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "line"), PersistentDataType.INTEGER, i+lineNumber);
                        paperMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "offset"), PersistentDataType.INTEGER, ii);
                        paperMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "edited"), PersistentDataType.BOOLEAN, false);
                        paperMeta.getPersistentDataContainer().set(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "type"), PersistentDataType.STRING, "text");
                        paper.setItemMeta(paperMeta);
                        content.add(paper);
                    }
                    else content.add(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE));
                }
            } else {
                for (int ii = 0; ii <= 8; ii++) {
                    content.add(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE));
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
        }
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
                openFile(player, 1);
            }
        }
    }

    @EventHandler
    public void fileScroll(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null || e.getCurrentItem().getType() != Material.TIPPED_ARROW || !position.containsKey(player.getName())) return;
            int line = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "line"), PersistentDataType.INTEGER);
            openFile(player, line);
        }
    }

    @EventHandler
    public void fileGoTo(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null || e.getCurrentItem().getType() != Material.SPECTRAL_ARROW || !position.containsKey(player.getName())) return;
            if (checkType(e.getCurrentItem(), "goto")) return;
            int max = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "max"), PersistentDataType.INTEGER);
            int currentLine = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "currentLine"), PersistentDataType.INTEGER);

            ItemStack paper = itemProperties(new ItemStack(Material.PAPER), String.valueOf(currentLine), null);
            ItemMeta meta = paper.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "max"), PersistentDataType.INTEGER, max);
            meta.getPersistentDataContainer().set(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "type"), PersistentDataType.STRING, "goto");
            paper.setItemMeta(meta);

            new AnvilGUI.Builder()
                    .plugin(JavaPlugin.getPlugin(FileManager.class))
                    .preventClose()
                    .title("Min: 1, Max: "+max)
                    .itemLeft(paper)
                    .onClick((slot, stateSnapshot) -> {
                        if (slot == AnvilGUI.Slot.OUTPUT) {
                            return List.of(AnvilGUI.ResponseAction.close());
                        }
                        return Collections.emptyList();
                    })
                    .onClose(stateSnapshot -> {
                        int line;
                        if (stateSnapshot.getOutputItem().getItemMeta() != null) {
                            try {
                                line = Integer.parseInt(stateSnapshot.getOutputItem().getItemMeta().getDisplayName().trim());
                            } catch (NumberFormatException nfe) {
                                line = 1;
                            }
                        }
                        else line = 1;
                        openFile(stateSnapshot.getPlayer(), line);
                    })
                    .open(player);
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
                        Integer line;
                        if (stateSnapshot.getOutputItem().getItemMeta() != null)
                            line = stateSnapshot.getOutputItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "line"), PersistentDataType.INTEGER);
                        else  line = 1;
                        if (line == null || line < 1) line = 1;
                        openFile(stateSnapshot.getPlayer(), line);
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
