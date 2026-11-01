package io.github.zgxhzhr.meowmachine.review;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 已通过条目索引：{@code <世界>/data/meow_machine_approved.dat}，含上一版正文（做修改对比用）。 */
public final class ApprovedSavedData extends SavedData {

    public static final String DATA_NAME = "meow_machine_approved";

    public record Entry(UUID id, String title, String author, String body, int revision, long time) {
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static ApprovedSavedData get(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.overworld().getDataStorage()
                .computeIfAbsent(ApprovedSavedData::load, ApprovedSavedData::new, DATA_NAME);
    }

    Map<UUID, Entry> entries() {
        return entries;
    }

    void put(Entry e) {
        entries.put(e.id(), e);
        setDirty();
    }

    public static ApprovedSavedData load(CompoundTag tag) {
        ApprovedSavedData d = new ApprovedSavedData();
        ListTag list = tag.getList("approved", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                UUID id = UUID.fromString(t.getString("id"));
                d.entries.put(id, new Entry(id, t.getString("title"), t.getString("author"),
                        t.getString("body"), t.getInt("revision"), t.getLong("time")));
            } catch (RuntimeException e) {
                MeowMachine.LOG.warn("[喵呜机] 已通过索引里有坏条目，已跳过: {}", e.toString());
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", e.id().toString());
            t.putString("title", e.title() == null ? "" : e.title());
            t.putString("author", e.author() == null ? "" : e.author());
            t.putString("body", e.body() == null ? "" : e.body());
            t.putInt("revision", e.revision());
            t.putLong("time", e.time());
            list.add(t);
        }
        tag.put("approved", list);
        return tag;
    }
}
