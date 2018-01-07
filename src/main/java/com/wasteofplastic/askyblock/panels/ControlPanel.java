package com.wasteofplastic.askyblock.panels;

import com.wasteofplastic.askyblock.ASkyBlock;
import com.wasteofplastic.askyblock.Settings;
import com.wasteofplastic.askyblock.util.Util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author tastybento
 * Provides a handy control panel and minishop
 */
public class ControlPanel implements Listener {

    private static final boolean DEBUG = false;
    /**
     * Map of CP inventories by name
     */
    public static Map<String, Inventory> controlPanel = new HashMap<>();
    private static String defaultPanelName;
    /**
     * Map of panel contents by name
     */
    private static Map<String, Map<Integer, CPItem>> panels = new HashMap<>();
    private ASkyBlock plugin;

    public ControlPanel(ASkyBlock plugin) {
        this.plugin = plugin;
        loadControlPanel();
    }

    // The first parameter, is the inventory owner. I make it null to let
    // everyone use it.
    // The second parameter, is the slots in a inventory. Must be a multiple of
    // 9. Can be up to 54.
    // The third parameter, is the inventory name. This will accept chat colors.

    /**
     * This loads the control panel from the controlpanel.yml file
     */
    public static void loadControlPanel() {
        ASkyBlock plugin = ASkyBlock.getPlugin();
        panels.clear();
        controlPanel.clear();

        YamlConfiguration cpFile = Util.loadYamlFile("controlpanel.yml");
        ConfigurationSection controlPanels = cpFile.getRoot();
        if (controlPanels == null) {
            plugin.getLogger().severe("Controlpanel.yml is corrupted! Delete so it can be regenerated or fix!");
            return;
        }

        for (String panel : controlPanels.getKeys(false)) {
            ConfigurationSection panelConf = cpFile.getConfigurationSection(panel);
            if (panelConf != null) {
                Map<Integer, CPItem> cp = new HashMap<>();
                String panelName = ChatColor.translateAlternateColorCodes('&', panelConf.getString("panelname", "Commands"));
                if (panel.equalsIgnoreCase("default")) {
                    defaultPanelName = panelName;
                }

                ConfigurationSection buttons = cpFile.getConfigurationSection(panel + ".buttons");
                if (buttons != null) {
                    // Get how many buttons can be in the CP
                    int size = buttons.getKeys(false).size() + 8;
                    size -= (size % 9);
                    // Add inventory to map of inventories
                    controlPanel.put(panelName, Bukkit.createInventory(null, size, panelName));
                    // Run through buttons
                    int slot = 0;
                    for (String item : buttons.getKeys(false)) {
                        String m = buttons.getString(item + ".material", "BOOK");
                        // Split off damage
                        String[] icon = m.split(":");
                        // plugin.getLogger().info("Material = " + m);
                        Material material = Material.matchMaterial(icon[0]);
                        if (material == null) {
                            material = Material.PAPER;
                            plugin.getLogger()
                                    .severe("Error in controlpanel.yml " + icon[0] + " is an unknown material, using paper.");
                        }
                        String description = ChatColor.translateAlternateColorCodes('&',
                                buttons.getString(item + ".description", ""));
                        String command = buttons.getString(item + ".command", "").replace("[island]", Settings.ISLANDCOMMAND);
                        String nextSection = buttons.getString(item + ".nextsection", "");
                        ItemStack i = new ItemStack(material);
                        if (icon.length == 2) {
                            i.setDurability(Short.parseShort(icon[1]));
                        }
                        CPItem cpItem = new CPItem(i, description, command, nextSection);
                        cp.put(slot, cpItem);
                        controlPanel.get(panelName).setItem(slot, cpItem.getItem());
                        slot++;
                    }
                    // Add overall control panel
                    panels.put(panelName, cp);
                }
            }
        }
    }

    /**
     * @return the defaultPanelName
     */
    public static String getDefaultPanelName() {
        return defaultPanelName;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (DEBUG) {
            plugin.getLogger().info("DEBUG:" + event.getEventName());
        }
        Player player = (Player) event.getWhoClicked(); // The player that
        // clicked the item
        ItemStack clicked = event.getCurrentItem(); // The item that was clicked
        Inventory inventory = event.getInventory(); // The inventory that was clicked in
        if (inventory.getName() == null) {
            if (DEBUG) {
                plugin.getLogger().info("DEBUG: inventory name is null");
            }
            return;
        }

        int slot = event.getRawSlot();
        if (inventory.getName().equals(plugin.myLocale(player.getUniqueId()).challengesguiTitle)) {
            event.setCancelled(true);
            if (event.getClick().equals(ClickType.SHIFT_RIGHT)) {
                if (DEBUG) {
                    plugin.getLogger().info("DEBUG: click type shift Right");
                }
                inventory.clear();
                player.closeInventory();
                player.updateInventory();
                return;
            }
            if (event.getSlotType() == SlotType.OUTSIDE) {
                if (DEBUG) {
                    plugin.getLogger().info("DEBUG: slot type outside");
                }
                inventory.clear();
                player.closeInventory();
                return;
            }

            List<CPItem> challenges = plugin.getChallenges().getCP(player);
            if (challenges == null) {
                plugin.getLogger().warning("Player was accessing Challenge Inventory, but it had lost state - was server restarted?");
                inventory.clear();
                player.closeInventory();
                Util.runCommand(player, Settings.CHALLENGECOMMAND);
                return;
            }

            if (slot >= 0 && slot < challenges.size()) {
                if (DEBUG) {
                    plugin.getLogger().info("DEBUG: slot within challenges");
                }
                CPItem item = challenges.get(slot);
                if (DEBUG) {
                    plugin.getLogger().info("DEBUG: CP Item is " + item.getItem().toString());
                    plugin.getLogger().info("DEBUG: Clicked is " + clicked.toString());
                }

                if (clicked.equals(item.getItem())) {
                    if (DEBUG) {
                        plugin.getLogger().info("DEBUG: You clicked on a challenge item");
                    }

                    if (item.getNextSection() != null) {
                        inventory.clear();
                        player.closeInventory();
                        player.openInventory(plugin.getChallenges().challengePanel(player, item.getNextSection()));
                    } else if (item.getCommand() != null) {
                        Util.runCommand(player, item.getCommand());
                        inventory.clear();
                        player.closeInventory();
                        player.openInventory(plugin.getChallenges().challengePanel(player));
                    }
                }
            }
            return;
        }

        for (String panelName : controlPanel.keySet()) {
            if (inventory.getName().equals(panelName)) {
                event.setCancelled(true);
                if (slot == -999) {
                    player.closeInventory();
                    return;
                }
                if (event.getClick().equals(ClickType.SHIFT_RIGHT)) {
                    player.closeInventory();
                    player.updateInventory();
                    return;
                }
                Map<Integer, CPItem> thisPanel = panels.get(panelName);
                if (slot >= 0 && slot < thisPanel.size()) {
                    String command = thisPanel.get(slot).getCommand();
                    String nextSection = ChatColor.translateAlternateColorCodes('&', thisPanel.get(slot).getNextSection());
                    if (!command.isEmpty()) {
                        player.closeInventory();
                        event.setCancelled(true);
                        Util.runCommand(player, command);
                        return;
                    }
                    if (!nextSection.isEmpty()) {
                        player.closeInventory();
                        Inventory next = controlPanel.get(nextSection);
                        player.openInventory(next);
                        event.setCancelled(true);
                        return;
                    }
                    player.closeInventory();
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

}