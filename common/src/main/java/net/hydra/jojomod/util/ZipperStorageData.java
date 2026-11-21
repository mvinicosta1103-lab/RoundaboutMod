package net.hydra.jojomod.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**Sticky Fingers' zipper storage works like an Ender Chest: every player has ONE personal one row inventory,
 * and every zipper storage opens that same inventory, in any block and any dimension.
 * The items belong to the player, not to the zipper, so popping or losing a zipper never loses items.
 * Saved in the overworld's data storage.*/
public class ZipperStorageData extends SavedData {

    public static final String ID = "roundabout_zipper_storage";
    public static final int SLOTS = 9;

    private final Map<UUID, SimpleContainer> storages = new HashMap<>();

    public static ZipperStorageData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(ZipperStorageData::load, ZipperStorageData::new, ID);
    }

    /**The storage of this player (created empty the first time)*/
    public SimpleContainer getContainer(UUID player) {
        return storages.computeIfAbsent(player, id -> createContainer());
    }

    private SimpleContainer createContainer() {
        SimpleContainer container = new SimpleContainer(SLOTS);
        container.addListener(changed -> this.setDirty());
        return container;
    }

    public static ZipperStorageData load(CompoundTag tag) {
        ZipperStorageData data = new ZipperStorageData();
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            SimpleContainer container = data.createContainer();
            container.fromTag(entry.getList("items", Tag.TAG_COMPOUND));
            data.storages.put(entry.getUUID("id"), container);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, SimpleContainer> entry : storages.entrySet()) {
            CompoundTag player = new CompoundTag();
            player.putUUID("id", entry.getKey());
            player.put("items", entry.getValue().createTag());
            players.add(player);
        }
        tag.put("players", players);
        return tag;
    }
}
