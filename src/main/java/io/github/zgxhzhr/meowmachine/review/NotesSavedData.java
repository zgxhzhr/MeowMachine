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
 * 审核意见（标注）：助理不能终裁，但可以留意见；终裁时这些意见会并进给作者的结果里。
 *
 * <p>存 {@code <世界>/data/meow_machine_notes.dat}，结构：{@code notes} 是 ListTag，
 * 每项 {@code submission / text / time}。意见用「名字：内容」压成一行，显示时直接读。
 */
public final class NotesSavedData extends SavedData {

    public static final String DATA_NAME = "meow_machine_notes";

    private final Map<UUID, List<String>> entries = new LinkedHashMap<>();

    public static NotesSavedData get(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.overworld().getDataStorage()
                .computeIfAbsent(NotesSavedData::load, NotesSavedData::new, DATA_NAME);
    }

    public Map<UUID, List<String>> entries() {
        Map<UUID, List<String>> copy = new LinkedHashMap<>();
        entries.forEach((k, v) -> copy.put(k, new ArrayList<>(v)));
        return copy;
    }

    void put(UUID submission, List<String> notes) {
        if (submission == null) {
            return;
        }
        entries.put(submission, new ArrayList<>(notes));
        setDirty();
    }

    public static NotesSavedData load(CompoundTag tag) {
        NotesSavedData data = new NotesSavedData();
        ListTag list = tag.getList("notes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                UUID id = UUID.fromString(t.getString("submission"));
                data.entries.computeIfAbsent(id, k -> new ArrayList<>())
                        .add(t.getString("text"));
            } catch (RuntimeException e) {
                MeowMachine.LOG.warn("[喵呜机] 审核意见存档里有坏条目，已跳过: {}", e.toString());
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        entries.forEach((id, notes) -> {
            for (String text : notes) {
                CompoundTag t = new CompoundTag();
                t.putString("submission", id.toString());
                t.putString("text", text);
                list.add(t);
            }
        });
        tag.put("notes", list);
        return tag;
    }
}
