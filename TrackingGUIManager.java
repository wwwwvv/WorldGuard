package dev.lujanabril.magmaItems.Managers;

import dev.lujanabril.magmaItems.GUI.TrackingGUI;
import dev.lujanabril.magmaItems.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TrackingGUIManager implements Listener {
    private final Main plugin;
    private final TrackingGUI trackingGUI;
    private final Map<UUID, GuiState> playerGuiStates = new HashMap<>();

    public TrackingGUIManager(Main plugin) {
        this.plugin = plugin;
        this.trackingGUI = new TrackingGUI(plugin);

        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Opens the tracking GUI for a player
     */
    public void openTrackingGUI(Player player) {
        // Scan for non-ID items before opening GUI
        plugin.getNonIdItemTrackingManager().updateNonIdItemTracking();

        // Get player's state or use default
        GuiState state = playerGuiStates.getOrDefault(player.getUniqueId(),
                new GuiState(0, TrackingGUI.FilterState.ALL, TrackingGUI.SortState.NEWEST));

        // Open the GUI
        trackingGUI.openTrackingGUI(player, state.page, state.filterState, state.sortState);
    }

    /**
     * Sets the player's GUI state
     */
    public void setPlayerGuiState(UUID playerUuid, int page, TrackingGUI.FilterState filterState, TrackingGUI.SortState sortState) {
        playerGuiStates.put(playerUuid, new GuiState(page, filterState, sortState));
    }

    /**
     * Gets the player's GUI state
     */
    public GuiState getPlayerGuiState(UUID playerUuid) {
        return playerGuiStates.getOrDefault(playerUuid,
                new GuiState(0, TrackingGUI.FilterState.ALL, TrackingGUI.SortState.NEWEST));
    }

    /**
     * Removes the player's GUI state when they close the inventory
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().startsWith("§8MagmaItems Tracking")) {
            // Optional: if you want to clean up states when inventory closes
            // playerGuiStates.remove(event.getPlayer().getUniqueId());
        }
    }

    /**
     * Handles clicks in the tracking GUI
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith("§8MagmaItems Tracking")) {
            return;
        }

        // Cancel the event to prevent item movement
        event.setCancelled(true);

        // Process inventory click
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR ||
                clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        GuiState state = getPlayerGuiState(player.getUniqueId());
        int slot = event.getSlot();

        // Updated slots for the new GUI layout
        if (slot == 30 && clickedItem.getType() == Material.ARROW) {
            // Previous page button
            state.page--;
            trackingGUI.openTrackingGUI(player, state.page, state.filterState, state.sortState);
        }
        else if (slot == 41 && clickedItem.getType() == Material.ARROW) {
            // Next page button
            state.page++;
            trackingGUI.openTrackingGUI(player, state.page, state.filterState, state.sortState);
        }
        else if (slot == 32 && clickedItem.getType() == Material.HOPPER) {
            // Filter button - cycle through filter states
            state.filterState = TrackingGUI.FilterState.values()[(state.filterState.ordinal() + 1) % TrackingGUI.FilterState.values().length];
            // Reset to page 0 when changing filter
            state.page = 0;
            trackingGUI.openTrackingGUI(player, state.page, state.filterState, state.sortState);
        }
        else if (slot == 39 && clickedItem.getType() == Material.COMPARATOR) {
            // Sort button - cycle through sort states
            state.sortState = TrackingGUI.SortState.values()[(state.sortState.ordinal() + 1) % TrackingGUI.SortState.values().length];
            // Reset to page 0 when changing sort
            state.page = 0;
            trackingGUI.openTrackingGUI(player, state.page, state.filterState, state.sortState);
        }
        else if (slot == 49 && clickedItem.getType() == Material.CLOCK) {
            // Refresh button
            plugin.getItemTrackingManager().checkForDuplicates(); // Check for duplicates
            plugin.getNonIdItemTrackingManager().updateNonIdItemTracking(); // Update non-ID items
            trackingGUI.openTrackingGUI(player, state.page, state.filterState, state.sortState);
        }
        else if (slot < 27) { // Only consider the first 3 rows as item slots
            // Clicked on an item - handle item click
            handleItemClick(player, clickedItem, event.isRightClick());
        }
    }

    /**
     * Handles click on an item in the GUI
     */
    private void handleItemClick(Player player, ItemStack clickedItem, boolean isRightClick) {
        if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        String displayName = meta.getDisplayName();

        // Extract the item ID from the display name
        String itemId = null;
        int idIndex = displayName.lastIndexOf("[ID: ");
        if (idIndex != -1) {
            itemId = displayName.substring(idIndex + 5, displayName.length() - 1);
        } else if (displayName.contains("[No ID]")) {
            // For non-ID items, we need to find them by other means
            for (ItemTrackingManager.ItemInfo info : plugin.getItemTrackingManager().getAllTrackedItems()) {
                if ((info.getItemId() == null || info.getItemId().startsWith("noID_")) &&
                        displayName.contains(info.getItemName())) {
                    itemId = info.getItemId();
                    break;
                }
            }
        }

        if (itemId == null) {
            player.sendMessage("§c[MagmaItems] §cCouldn't identify this item.");
            return;
        }

        // Get item info
        ItemTrackingManager.ItemInfo itemInfo = plugin.getItemTrackingManager().getItemInfo(itemId);
        if (itemInfo == null) {
            player.sendMessage("§c[MagmaItems] §cCouldn't find information for this item.");
            return;
        }

        if (isRightClick) {
            // Show item history
            player.sendMessage("§e[MagmaItems] §7Item history for: §e" + itemInfo.getItemName());
            player.sendMessage("§7Original Owner: §e" + itemInfo.getOriginalOwnerName());
            player.sendMessage("§7Current Owner: §e" + itemInfo.getCurrentOwnerName());
            player.sendMessage("§7Created: §e" + itemInfo.getCreationTime());

            if (itemId.startsWith("noID_")) {
                player.sendMessage("§7Type: §cRegular MagmaItem (No ID)");
            } else {
                player.sendMessage("§7Type: §aMagmaItem with ID: §e" + itemId);
            }
        } else {
            // Left click - try to teleport to current owner
            try {
                UUID ownerUuid = UUID.fromString(itemInfo.getCurrentOwnerUUID());
                Player target = Bukkit.getPlayer(ownerUuid);

                if (target != null && target.isOnline()) {
                    // Teleport to the player
                    player.teleport(target.getLocation());
                    player.sendMessage("§e[MagmaItems] §7Teleported to §e" + target.getName());
                } else {
                    player.sendMessage("§e[MagmaItems] §cThe current owner is not online.");
                }
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c[MagmaItems] §cInvalid owner UUID for this item.");
            }
        }
    }

    /**
     * Class to store player's GUI state
     */
    public static class GuiState {
        public int page;
        public TrackingGUI.FilterState filterState;
        public TrackingGUI.SortState sortState;

        public GuiState(int page, TrackingGUI.FilterState filterState, TrackingGUI.SortState sortState) {
            this.page = page;
            this.filterState = filterState;
            this.sortState = sortState;
        }
    }
}