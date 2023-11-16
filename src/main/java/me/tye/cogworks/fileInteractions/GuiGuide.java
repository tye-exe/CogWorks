package me.tye.cogworks.fileInteractions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;

import static me.tye.cogworks.util.Util.plugin;

public class GuiGuide implements Listener {

private static int index = 0;

@EventHandler
public static void clickEvent(InventoryClickEvent e) {
  if (!(e.getWhoClicked() instanceof Player)) {
    return;
  }

  ItemStack currentItem = e.getCurrentItem();
  if (currentItem == null) {
    return;
  }

  ItemMeta itemMeta = currentItem.getItemMeta();
  if (itemMeta == null) {
    return;
  }

  //checks if the item is one in a CogWorks menu. All CogWorks items have this.
  String identifier = itemMeta.getPersistentDataContainer().get(new NamespacedKey(plugin, "identifier"), PersistentDataType.STRING);
  if (identifier == null) {
    return;
  }
  e.setCancelled(true);

  Player player = ((Player) e.getWhoClicked()).getPlayer();


}


public static void display(Player player) {
  Inventory gui = Bukkit.createInventory(player, 54, ChatColor.BLUE.toString()+index);
  ItemStack[] items;

  switch (index) {
  case 0 -> {

  }
  }
}

private static ItemStack[] intro() {

  ArrayList<ItemStack> content = defaultBackground();

}

private static ArrayList<ItemStack> defaultBackground() {

}

}
