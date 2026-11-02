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

    public record Entry(UUID id, String title, String author, String authorUuid,
                        String body, int revision, long time) {
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static ApprovedSavedData get(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.overworld().getDataStorage()
                .computeIfAbsent(ApprovedSavedData::load, ApprovedSavedData::new, DATA_NAME);
    }

    /** 只读视图（不要直接改内部 Map） */
    Map<UUID, Entry> entries() {
        return java.util.Collections.unmodifiableMap(entries);
    }

    /** 被退回（主动移除）的条目 id：回填时跳过，避免重启后复活 */
    private final java.util.Set<UUID> removed = new java.util.LinkedHashSet<>();

    boolean wasRemoved(UUID id) {
        return removed.contains(id);
    }

    void remove(UUID id) {
        removed.add(id);
        entries.remove(id);
        setDirty();
    }

    void put(Entry e) {
        removed.remove(e.id());
        entries.put(e.id(), e);
        setDirty();
    }

    public static ApprovedSavedData load(CompoundTag tag) {
        ApprovedSavedData d = new ApprovedSavedData();
        ListTag tombs = tag.getList("removed", Tag.TAG_STRING);
        for (int i = 0; i < tombs.size(); i++) {
            try {
                d.removed.add(UUID.fromString(tombs.getString(i)));
            } catch (RuntimeException ignored) {
            }
        }
        ListTag list = tag.getList("approved", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                UUID id = UUID.fromString(t.getString("id"));
                d.entries.put(id, new Entry(id, t.getString("title"), t.getString("author"),
                        t.getString("author_uuid"), t.getString("body"),
                        t.getInt("revision"), t.getLong("time")));
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
            t.putString("author_uuid", e.authorUuid() == null ? "" : e.authorUuid());
            t.putString("body", e.body() == null ? "" : e.body());
            t.putInt("revision", e.revision());
            t.putLong("time", e.time());
            list.add(t);
        }
        tag.put("approved", list);
        ListTag tombs = new ListTag();
        for (UUID id : removed) {
            tombs.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
        }
        tag.put("removed", tombs);
        return tag;
    }
}
