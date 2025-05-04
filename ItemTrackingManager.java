package dev.lujanabril.magmaItems.Managers;

import dev.lujanabril.magmaItems.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;

public class ItemTrackingManager {
    private final Main plugin;
    private final File trackingFile;
    private FileConfiguration trackingConfig;
    private final NamespacedKey itemIdKey;
    private Map<String, String> itemTrackingCache = new HashMap<>();

    public ItemTrackingManager(Main plugin) {
        this.plugin = plugin;
        this.itemIdKey = new NamespacedKey(plugin, "magma_item_id");
        this.trackingFile = new File(plugin.getDataFolder(), "item-tracking.yml");
        loadTracking();
    }

    /**
     * Carga la configuración de seguimiento desde el archivo
     */
    public void loadTracking() {
        if (!trackingFile.exists()) {
            try {
                trackingFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create item tracking file", e);
            }
        }

        trackingConfig = YamlConfiguration.loadConfiguration(trackingFile);
        cacheTrackingData();
    }

    /**
     * Recarga la configuración de seguimiento
     */
    public void reloadTracking() {
        loadTracking();
    }

    /**
     * Guarda los datos de seguimiento en el archivo
     */
    public void saveTracking() {
        try {
            trackingConfig.save(trackingFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save item tracking file", e);
        }
    }

    /**
     * Carga los datos de seguimiento en caché para acceso rápido
     */
    private void cacheTrackingData() {
        itemTrackingCache.clear();

        if (trackingConfig.contains("ID-Items")) {
            for (String id : trackingConfig.getConfigurationSection("ID-Items").getKeys(false)) {
                itemTrackingCache.put(id, trackingConfig.getString("ID-Items." + id));
            }
        }
    }

    /**
     * Registra un nuevo ítem con ID único en el sistema de seguimiento
     */
    public void registerItem(String uniqueId, String playerUuid, String playerName, String itemName) {
        // Get current timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = sdf.format(new Date());

        // Format: originalOwnerName;itemName;currentOwnerName;originalOwnerUUID;currentOwnerUUID;timestamp;material
        String trackingData = playerName + ";" + itemName + ";" + playerName + ";" +
                playerUuid + ";" + playerUuid + ";" + timestamp + ";UNKNOWN";

        // Guardar en configuración
        trackingConfig.set("ID-Items." + uniqueId, trackingData);

        // Actualizar caché
        itemTrackingCache.put(uniqueId, trackingData);

        // Guardar archivo
        saveTracking();
    }

    /**
     * Actualiza el último poseedor de un ítem
     */
    public void updateItemOwner(String uniqueId, String playerName, String playerUuid) {
        String currentData = itemTrackingCache.get(uniqueId);
        if (currentData != null) {
            String[] parts = currentData.split(";");
            if (parts.length >= 6) {
                // Keep original data, update current owner
                String newData = parts[0] + ";" + parts[1] + ";" + playerName + ";" +
                        parts[3] + ";" + playerUuid + ";" + parts[5];

                // Add material if available
                if (parts.length >= 7) {
                    newData += ";" + parts[6];
                }

                trackingConfig.set("ID-Items." + uniqueId, newData);
                itemTrackingCache.put(uniqueId, newData);
                saveTracking();
            }
        }
    }

    /**
     * Updates the material type for a tracked item
     */
    public void updateItemMaterial(String uniqueId, Material material) {
        String currentData = itemTrackingCache.get(uniqueId);
        if (currentData != null) {
            String[] parts = currentData.split(";");
            if (parts.length >= 6) {
                // Keep original data, update material
                String newData = parts[0] + ";" + parts[1] + ";" + parts[2] + ";" +
                        parts[3] + ";" + parts[4] + ";" + parts[5] + ";" + material.toString();

                trackingConfig.set("ID-Items." + uniqueId, newData);
                itemTrackingCache.put(uniqueId, newData);
                saveTracking();
            }
        }
    }

    /**
     * Verifica si un ID ya existe en el sistema
     */
    public boolean idExists(String uniqueId) {
        return itemTrackingCache.containsKey(uniqueId);
    }

    /**
     * Gets information about a tracked item by its ID
     */
    public ItemInfo getItemInfo(String itemId) {
        String data = itemTrackingCache.get(itemId);
        if (data == null) return null;

        String[] parts = data.split(";");
        if (parts.length < 6) return null;

        ItemInfo info = new ItemInfo();
        info.setItemId(itemId);
        info.setOriginalOwnerName(parts[0]);
        info.setItemName(parts[1]);
        info.setCurrentOwnerName(parts[2]);
        info.setOriginalOwnerUUID(parts[3]);
        info.setCurrentOwnerUUID(parts[4]);
        info.setCreationTime(parts[5]);

        if (parts.length >= 7) {
            try {
                info.setItemMaterial(Material.valueOf(parts[6]));
            } catch (IllegalArgumentException e) {
                info.setItemMaterial(Material.PAPER);
            }
        }

        return info;
    }

    /**
     * Gets a list of all tracked items
     */
    public List<ItemInfo> getAllTrackedItems() {
        List<ItemInfo> result = new ArrayList<>();

        for (String itemId : itemTrackingCache.keySet()) {
            ItemInfo info = getItemInfo(itemId);
            if (info != null) {
                result.add(info);
            }
        }

        return result;
    }



    public void checkForDuplicates() {
        plugin.getLogger().info("Checking for duplicate items...");

        Map<String, Integer> itemCounts = new HashMap<>();

        // Recolectar todos los ítems con IDs en los inventarios de jugadores
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    PersistentDataContainer container = meta.getPersistentDataContainer();

                    if (container.has(itemIdKey, PersistentDataType.STRING)) {
                        String itemId = container.get(itemIdKey, PersistentDataType.STRING);

                        // Update material information
                        updateItemMaterial(itemId, item.getType());

                        // Incrementar contador
                        itemCounts.put(itemId, itemCounts.getOrDefault(itemId, 0) + 1);

                        // Actualizar último poseedor
                        updateItemOwner(itemId, player.getName(), player.getUniqueId().toString());
                    }
                }
            }
        }

        // Verificar duplicados
        for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
            if (entry.getValue() > 1) {
                plugin.getLogger().warning("Found duplicate item with ID: " + entry.getKey() +
                        " (Count: " + entry.getValue() + ")");

                handleDuplicateItem(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Maneja los items duplicados encontrados
     */
    private void handleDuplicateItem(String itemId, int count) {
        // Log a la consola
        ItemInfo itemInfo = getItemInfo(itemId);
        if (itemInfo != null) {
            plugin.getLogger().warning("Duplicate item info: Original owner: " + itemInfo.getOriginalOwnerName() +
                    ", Item: " + itemInfo.getItemName() + ", Last owner: " + itemInfo.getCurrentOwnerName());
        }

        // Implementación básica: eliminar duplicados dejando solo el primero
        boolean foundFirst = false;

        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = player.getInventory().getContents();
            boolean inventoryChanged = false;

            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    PersistentDataContainer container = meta.getPersistentDataContainer();

                    if (container.has(itemIdKey, PersistentDataType.STRING)) {
                        String currentId = container.get(itemIdKey, PersistentDataType.STRING);

                        if (currentId.equals(itemId)) {
                            if (!foundFirst) {
                                foundFirst = true;
                                // Notificar al jugador que tiene el item original
                                player.sendMessage("§c[MagmaItems] §eUno de tus items tiene un ID que ha sido duplicado. Este es el original.");
                            } else {
                                // Es un duplicado, eliminarlo
                                contents[i] = null;
                                inventoryChanged = true;
                                player.sendMessage("§c[MagmaItems] §cSe ha eliminado un item duplicado de tu inventario (ID: " + itemId + ")");
                            }
                        }
                    }
                }
            }

            if (inventoryChanged) {
                player.getInventory().setContents(contents);
                player.updateInventory();
            }
        }

        // Notificar a operadores
        for (Player op : Bukkit.getOnlinePlayers()) {
            if (op.isOp()) {
                op.sendMessage("§c[MagmaItems] §4¡Alerta! §cSe han detectado §4" + count + "§c items duplicados con ID: §4" + itemId);
            }
        }
    }

    /**
     * Class to store item tracking information
     */
    public static class ItemInfo {
        private String itemId;
        private String originalOwnerName;
        private String itemName;
        private String currentOwnerName;
        private String originalOwnerUUID;
        private String currentOwnerUUID;
        private String creationTime;
        private Material itemMaterial;

        // Getters and setters
        public String getItemId() { return itemId; }
        public void setItemId(String itemId) { this.itemId = itemId; }

        public String getOriginalOwnerName() { return originalOwnerName; }
        public void setOriginalOwnerName(String originalOwnerName) { this.originalOwnerName = originalOwnerName; }

        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }

        public String getCurrentOwnerName() { return currentOwnerName; }
        public void setCurrentOwnerName(String currentOwnerName) { this.currentOwnerName = currentOwnerName; }

        public String getOriginalOwnerUUID() { return originalOwnerUUID; }
        public void setOriginalOwnerUUID(String originalOwnerUUID) { this.originalOwnerUUID = originalOwnerUUID; }

        public String getCurrentOwnerUUID() { return currentOwnerUUID; }
        public void setCurrentOwnerUUID(String currentOwnerUUID) { this.currentOwnerUUID = currentOwnerUUID; }

        public String getCreationTime() { return creationTime; }
        public void setCreationTime(String creationTime) { this.creationTime = creationTime; }

        public Material getItemMaterial() { return itemMaterial; }
        public void setItemMaterial(Material itemMaterial) { this.itemMaterial = itemMaterial; }
    }
}