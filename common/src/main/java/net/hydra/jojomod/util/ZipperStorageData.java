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

public class ZipperStorageData extends SavedData {

    public static final String ID = "roundabout_zipper_storage";
    public static final int SLOTS = 9;

    private final Map<UUID, SimpleContainer> storages = new HashMap<>();

    public static ZipperStorageData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(ZipperStorageData::load, ZipperStorageData::new, ID);
    }

    public SimpleContainer getContainer(UUID player) {
        return getContainer(player, SLOTS);
    }

    public SimpleContainer getContainer(UUID player, int requiredSlots) {
        int requestedSize = toRowSize(requiredSlots);
        SimpleContainer existing = storages.computeIfAbsent(player, id -> createContainer(SLOTS));
        if (existing.getContainerSize() >= requestedSize) {
            return existing;
        }

        SimpleContainer expanded = createContainer(requestedSize);
        for (int slot = 0; slot < existing.getContainerSize(); slot++) {
            expanded.setItem(slot, existing.getItem(slot).copy());
        }
        storages.put(player, expanded);
        this.setDirty();
        return expanded;
    }

    private static int toRowSize(int requestedSlots) {
        int rows = Math.max(1, Math.min(6, (Math.max(SLOTS, requestedSlots) + 8) / 9));
        return rows * 9;
    }

    private SimpleContainer createContainer(int size) {
        SimpleContainer container = new SimpleContainer(size);
        container.addListener(changed -> this.setDirty());
        return container;
    }

    public static ZipperStorageData load(CompoundTag tag) {
        ZipperStorageData data = new ZipperStorageData();
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            ListTag items = entry.getList("items", Tag.TAG_COMPOUND);
            int requiredSize = SLOTS;
            for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
                requiredSize = Math.max(requiredSize, items.getCompound(itemIndex).getByte("Slot") + 1);
            }
            SimpleContainer container = data.createContainer(toRowSize(requiredSize));
            container.fromTag(items);
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
