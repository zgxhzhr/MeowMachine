package io.github.zgxhzhr.meowmachine.review;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 人工审核队列的世界存档：{@code <世界>/data/meow_machine_queue.dat}。
 *
 * <p>0.1.0 的队列是纯内存，服务端一重启待审全丢（多人服里等于「审到一半的稿子消失」）。
 * 现在每次入队/裁决都写穿到存档，重启后 {@link SubmissionQueue#attach} 会把它们读回来。
 *
 * <p>用 {@link LinkedHashMap} 保持「先来先审」的顺序。
 */
public final class QueueSavedData extends SavedData {

    public static final String DATA_NAME = "meow_machine_queue";

    private final Map<UUID, SubmissionQueue.Entry> entries = new LinkedHashMap<>();

    public static QueueSavedData get(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.overworld().getDataStorage()
                .computeIfAbsent(QueueSavedData::load, QueueSavedData::new, DATA_NAME);
    }

    public Map<UUID, SubmissionQueue.Entry> entries() {
        return new LinkedHashMap<>(entries);
    }

    void put(SubmissionQueue.Entry entry) {
        if (entry == null) {
            return;
        }
        entries.put(entry.submission().id(), entry);
        setDirty();
    }

    void remove(UUID id) {
        if (entries.remove(id) != null) {
            setDirty();
        }
    }

    // ---------------- 序列化 ----------------

    public static QueueSavedData load(CompoundTag tag) {
        QueueSavedData data = new QueueSavedData();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                UUID id = UUID.fromString(t.getString("id"));
                SubmissionState state;
                boolean reEdit = t.getBoolean("re_edit");
                try {
                    state = SubmissionState.valueOf(t.getString("state"));
                } catch (IllegalArgumentException e) {
                    state = SubmissionState.SUBMITTED;
                    reEdit = false;
                }
                Submission sub = new Submission(id, UUID.fromString(t.getString("author")),
                        t.getString("authorName"), t.getString("title"), t.getString("body"),
                        state, t.getInt("revision"), reEdit);
                data.entries.put(id, new SubmissionQueue.Entry(sub, t.getLong("submittedAt")));
            } catch (RuntimeException e) {
                io.github.zgxhzhr.meowmachine.MeowMachine.LOG.warn(
                        "[喵呜机] 队列存档里有坏条目，已跳过: {}", e.toString());
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (SubmissionQueue.Entry e : entries.values()) {
            Submission s = e.submission();
            CompoundTag t = new CompoundTag();
            t.putString("author", s.authorUuid().toString());
            t.putString("authorName", s.authorName());
            t.putString("id", s.id().toString());
            t.putString("title", s.title());
            t.putString("body", s.body());
            t.putString("state", s.state().name());
            t.putInt("revision", s.revision());
            t.putLong("submittedAt", e.submittedAt());
            list.add(t);
        }
        tag.put("queue", list);
        return tag;
    }
}
