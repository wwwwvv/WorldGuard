package dev.lujanabril.magmaItems.GUI;

import dev.lujanabril.magmaItems.Main;
import dev.lujanabril.magmaItems.Managers.ItemTrackingManager;
import dev.lujanabril.magmaItems.Managers.NonIdItemTrackingManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class TrackingGUI {
    private final Main plugin;
    private final ItemTrackingManager trackingManager;
    private static final int ITEMS_PER_PAGE = 27; // Changed from 45 to match the new layout
    private static final String GUI_TITLE = "§8MagmaItems Tracking";

    // Filter states
    public enum FilterState {
        ALL,
        WITH_ID,
        WITHOUT_ID
    }

    // Sort states
    public enum SortState {
        NEWEST,
        OLDEST,
        ALPHABETICAL
    }

    public TrackingGUI(Main plugin) {
        this.plugin = plugin;
        this.trackingManager = plugin.getItemTrackingManager();
    }

    /**
     * Opens the tracking GUI for a player
     * @param player The player to open the GUI for
     * @param page The page number to display
     * @param filterState The current filter state
     * @param sortState The current sort state
     */
    public void openTrackingGUI(Player player, int page, FilterState filterState, SortState sortState) {
        // Create inventory with 54 slots (6 rows)
        Inventory inventory = Bukkit.createInventory(null, 54, GUI_TITLE + " - Page " + (page + 1));

        // Fill inventory with gray glass panes as background
        ItemStack backgroundItem = createGuiButton(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, backgroundItem);
        }

        List<ItemTrackingManager.ItemInfo> allItems = trackingManager.getAllTrackedItems();

        // Apply filters
        List<ItemTrackingManager.ItemInfo> filteredItems;
        switch (filterState) {
            case WITH_ID:
                filteredItems = allItems.stream()
                        .filter(item -> item.getItemId() != null && !item.getItemId().isEmpty() && !item.getItemId().startsWith("noID_"))
                        .collect(Collectors.toList());
                break;
            case WITHOUT_ID:
                filteredItems = allItems.stream()
                        .filter(item -> item.getItemId() == null || item.getItemId().isEmpty() || item.getItemId().startsWith("noID_"))
                        .collect(Collectors.toList());
                break;
            case ALL:
            default:
                filteredItems = allItems;
                break;
        }

        // Apply sorting
        switch (sortState) {
            case NEWEST:
                filteredItems.sort((a, b) -> {
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    try {
                        Date dateA = format.parse(a.getCreationTime());
                        Date dateB = format.parse(b.getCreationTime());
                        return dateB.compareTo(dateA); // Newest first
                    } catch (ParseException e) {
                        return 0;
                    }
                });
                break;
            case OLDEST:
                filteredItems.sort((a, b) -> {
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    try {
                        Date dateA = format.parse(a.getCreationTime());
                        Date dateB = format.parse(b.getCreationTime());
                        return dateA.compareTo(dateB); // Oldest first
                    } catch (ParseException e) {
                        return 0;
                    }
                });
                break;
            case ALPHABETICAL:
                filteredItems.sort(Comparator.comparing(ItemTrackingManager.ItemInfo::getItemName));
                break;
        }

        // Calculate total pages
        int totalPages = (int) Math.ceil((double) filteredItems.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        // Ensure page is within bounds
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        // Calculate item range for current page
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredItems.size());

        // The item slots for the 3x9 item grid (based on the image)
        int[] itemSlots = new int[] {
                // First row (9 slots)
                0, 1, 2, 3, 4, 5, 6, 7, 8,
                // Second row (9 slots)
                9, 10, 11, 12, 13, 14, 15, 16, 17,
                // Third row (9 slots)
                18, 19, 20, 21, 22, 23, 24, 25, 26
        };

        // Add items for current page
        int slotIndex = 0;
        for (int i = startIndex; i < endIndex; i++) {
            if (slotIndex >= itemSlots.length) break;

            ItemTrackingManager.ItemInfo itemInfo = filteredItems.get(i);
            inventory.setItem(itemSlots[slotIndex++], createInfoItem(itemInfo));
        }

        // Add navigation and filter controls in the bottom rows

        // Row 4: Control items (First half)
        if (page > 0) {
            // Previous page button (row 4, slot 3)
            ItemStack prevBtn = createGuiButton(Material.ARROW, "§ePrevious Page",
                    Collections.singletonList("§7Click to go to previous page"));
            inventory.setItem(30, prevBtn);
        }

        // Filter button (row 4, slot 5)
        List<String> filterLore = new ArrayList<>();
        filterLore.add("§7Current: §e" + filterState.name());
        filterLore.add("§7Click to cycle filters");
        ItemStack filterBtn = createGuiButton(Material.HOPPER, "§eFilter Items", filterLore);
        inventory.setItem(32, filterBtn);

        // Row 5: Control items (Second half)

        // Sort button (row 5, slot 3)
        List<String> sortLore = new ArrayList<>();
        sortLore.add("§7Current: §e" + sortState.name());
        sortLore.add("§7Click to cycle sort method");
        ItemStack sortBtn = createGuiButton(Material.COMPARATOR, "§eSort Items", sortLore);
        inventory.setItem(39, sortBtn);

        // Next page button (row 5, slot 5)
        if (page < totalPages - 1) {
            ItemStack nextBtn = createGuiButton(Material.ARROW, "§eNext Page",
                    Collections.singletonList("§7Click to go to next page"));
            inventory.setItem(41, nextBtn);
        }

        // Row 6: Center items

        // Refresh button (row 6, slot 4)
        ItemStack refreshBtn = createGuiButton(Material.CLOCK, "§eRefresh",
                Collections.singletonList("§7Click to refresh item data"));
        inventory.setItem(49, refreshBtn);

        // Display page info (row 6, slot 0)
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Total Items: §e" + filteredItems.size());
        infoLore.add("§7Page: §e" + (page + 1) + "/" + totalPages);
        ItemStack infoItem = createGuiButton(Material.PAPER, "§eInfo", infoLore);
        inventory.setItem(45, infoItem);

        // Open inventory for player
        player.openInventory(inventory);

        // Store player's current GUI state (page, filter, sort) in metadata or elsewhere
        plugin.getTrackingGuiManager().setPlayerGuiState(player.getUniqueId(), page, filterState, sortState);
    }

    /**
     * Creates an ItemStack that represents a tracked item for the GUI
     */
    private ItemStack createInfoItem(ItemTrackingManager.ItemInfo itemInfo) {
        Material material = itemInfo.getItemMaterial() != null ? itemInfo.getItemMaterial() : Material.PAPER;
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();

        // Set display name - Handle items without ID differently
        boolean isNonIdItem = itemInfo.getItemId() != null && itemInfo.getItemId().startsWith("noID_");

        if (itemInfo.getItemId() != null && !itemInfo.getItemId().isEmpty() && !isNonIdItem) {
            meta.setDisplayName("§e" + itemInfo.getItemName() + " §7[ID: " + itemInfo.getItemId() + "]");
        } else {
            meta.setDisplayName("§e" + itemInfo.getItemName() + " §7[No ID]");
        }

        // Add lore with detailed information
        List<String> lore = new ArrayList<>();

        // Add original owner info
        lore.add("§7Original Owner: §e" + itemInfo.getOriginalOwnerName());

        // Add current owner info
        String currentOwnerName = itemInfo.getCurrentOwnerName();
        UUID currentOwnerUUID = null;
        try {
            currentOwnerUUID = UUID.fromString(itemInfo.getCurrentOwnerUUID());
        } catch (IllegalArgumentException e) {
            // Invalid UUID, ignore
        }

        OfflinePlayer currentOwner = currentOwnerUUID != null ? Bukkit.getOfflinePlayer(currentOwnerUUID) : null;
        boolean isOnline = currentOwner != null && currentOwner.isOnline();

        lore.add("§7Current Owner: " + (isOnline ? "§a" : "§c") + currentOwnerName +
                (isOnline ? " §7(Online)" : " §7(Offline)"));

        // Add location if player is online
        if (isOnline) {
            Player onlinePlayer = currentOwner.getPlayer();
            String location = String.format("§7Location: §e%s §7(§e%d§7, §e%d§7, §e%d§7)",
                    onlinePlayer.getWorld().getName(),
                    onlinePlayer.getLocation().getBlockX(),
                    onlinePlayer.getLocation().getBlockY(),
                    onlinePlayer.getLocation().getBlockZ());
            lore.add(location);
        }

        // Add creation time
        lore.add("§7Created: §e" + itemInfo.getCreationTime());

        // Add item type information
        if (isNonIdItem) {
            lore.add("§7Type: §cRegular MagmaItem (No ID)");
        } else if (itemInfo.getItemId() == null || itemInfo.getItemId().isEmpty()) {
            lore.add("§7Type: §cRegular MagmaItem (No ID)");
        } else {
            lore.add("§7Type: §aMagmaItem with ID");
        }

        // Add action hint
        lore.add("");
        lore.add("§7Left-click: §eTeleport to player (if online)");
        lore.add("§7Right-click: §eShow item history");

        meta.setLore(lore);
        itemStack.setItemMeta(meta);

        return itemStack;
    }

    /**
     * Creates a button for the GUI
     */
    private ItemStack createGuiButton(Material material, String name, List<String> lore) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) {
            meta.setLore(lore);
        }
        button.setItemMeta(meta);
        return button;
    }

    /**
     * Creates a player head item
     */
    private ItemStack createPlayerHead(OfflinePlayer player, String displayName, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.setDisplayName(displayName);
        meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }
}