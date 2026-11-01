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
 * AI 意见存档：{@code <世界>/data/meow_machine_ai_opinions.dat}。
 *
 * <p>开启「AI 审核」但**不接管**时，AI 仍然先跑一遍，它的发现要交给人类审核员看，
 * 而不是直接用来裁决——人工队列里的稿件会带上这份意见。
 */
public final class AiOpinionsSavedData extends SavedData {

    public static final String DATA_NAME = "meow_machine_ai_opinions";

    private final Map<UUID, List<String>> entries = new LinkedHashMap<>();

    public static AiOpinionsSavedData get(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.overworld().getDataStorage()
                .computeIfAbsent(AiOpinionsSavedData::load, AiOpinionsSavedData::new, DATA_NAME);
    }

    public Map<UUID, List<String>> entries() {
        Map<UUID, List<String>> copy = new LinkedHashMap<>();
        entries.forEach((k, v) -> copy.put(k, new ArrayList<>(v)));
        return copy;
    }

    void put(UUID submission, List<String> lines) {
        if (submission == null) {
            return;
        }
        entries.put(submission, new ArrayList<>(lines));
        setDirty();
    }

    void clear(UUID submission) {
        if (entries.remove(submission) != null) {
            setDirty();
        }
    }

    public static AiOpinionsSavedData load(CompoundTag tag) {
        AiOpinionsSavedData data = new AiOpinionsSavedData();
        ListTag list = tag.getList("opinions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                UUID id = UUID.fromString(t.getString("submission"));
                data.entries.computeIfAbsent(id, k -> new ArrayList<>()).add(t.getString("text"));
            } catch (RuntimeException e) {
                MeowMachine.LOG.warn("[喵呜机] AI 意见存档里有坏条目，已跳过: {}", e.toString());
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        entries.forEach((id, lines) -> {
            for (String text : lines) {
                CompoundTag t = new CompoundTag();
                t.putString("submission", id.toString());
                t.putString("text", text);
                list.add(t);
            }
        });
        tag.put("opinions", list);
        return tag;
    }
}
