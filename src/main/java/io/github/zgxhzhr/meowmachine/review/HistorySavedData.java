package io.github.zgxhzhr.meowmachine.review;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 已出结论的投稿历史：{@code <世界>/data/meow_machine_history.dat}。
 *
 * <p>为什么要它：稿子一旦通过/打回，就会从人工队列里消失，而「我的投稿」只拼了
 * 「当前草稿 + 队列」，于是**已通过的稿子在自己列表里看不到**——作者会以为稿子丢了。
 *
 * <p>NBT：{@code history} 是 {@code ListTag}，每项一条
 * （author / id / title / state / body / revision / time）。
 */
public final class HistorySavedData extends SavedData {

    public static final String DATA_NAME = "meow_machine_history";

    private final Map<UUID, List<SubmissionHistory.Record>> entries = new LinkedHashMap<>();

    public static HistorySavedData get(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.overworld().getDataStorage()
                .computeIfAbsent(HistorySavedData::load, HistorySavedData::new, DATA_NAME);
    }

    /** 全部历史（按作者） */
    public Map<UUID, List<SubmissionHistory.Record>> entries() {
        Map<UUID, List<SubmissionHistory.Record>> copy = new LinkedHashMap<>();
        entries.forEach((k, v) -> copy.put(k, new ArrayList<>(v)));
        return copy;
    }

    void put(UUID author, List<SubmissionHistory.Record> records) {
        if (author == null) {
            return;
        }
        entries.put(author, new ArrayList<>(records));
        setDirty();
    }

    // ---------------- 序列化 ----------------

    public static HistorySavedData load(CompoundTag tag) {
        HistorySavedData data = new HistorySavedData();
        ListTag list = tag.getList("history", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                UUID author = UUID.fromString(t.getString("author"));
                UUID id = UUID.fromString(t.getString("id"));
                SubmissionHistory.Record r = new SubmissionHistory.Record(
                        id,
                        t.getString("title"),
                        t.getString("state"),
                        t.getString("body"),
                        t.getInt("revision"),
                        t.getLong("time"));
                data.entries.computeIfAbsent(author, k -> new ArrayList<>()).add(r);
            } catch (RuntimeException e) {
                MeowMachine.LOG.warn("[喵呜机] 投稿历史存档里有坏条目，已跳过: {}", e.toString());
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        entries.forEach((author, records) -> {
            for (SubmissionHistory.Record r : records) {
                CompoundTag t = new CompoundTag();
                t.putString("author", author.toString());
                t.putString("id", r.id().toString());
                t.putString("title", r.title() == null ? "" : r.title());
                t.putString("state", r.state() == null ? "" : r.state());
                t.putString("body", r.body() == null ? "" : r.body());
                t.putInt("revision", r.revision());
                t.putLong("time", r.time());
                list.add(t);
            }
        });
        tag.put("history", list);
        return tag;
    }
}
