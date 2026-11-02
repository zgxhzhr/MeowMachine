package io.github.zgxhzhr.meowmachine.review;

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

/** 对「逆天 AI 审核」的吐槽：{@code <世界>/data/meow_machine_rants.dat}。 */
public final class RantsSavedData extends SavedData {

    public static final String DATA_NAME = "meow_machine_rants";

    private final Map<UUID, List<String>> rants = new LinkedHashMap<>();

    public static RantsSavedData get(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.overworld().getDataStorage()
                .computeIfAbsent(RantsSavedData::load, RantsSavedData::new, DATA_NAME);
    }

    Map<UUID, List<String>> rants() {
        return rants;
    }

    void add(UUID id, String text) {
        rants.computeIfAbsent(id, k -> new ArrayList<>()).add(text);
        setDirty();
    }

    public static RantsSavedData load(CompoundTag tag) {
        RantsSavedData d = new RantsSavedData();
        ListTag list = tag.getList("rants", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                UUID id = UUID.fromString(t.getString("id"));
                ListTag lines = t.getList("lines", Tag.TAG_STRING);
                List<String> out = new ArrayList<>();
                for (int k = 0; k < lines.size(); k++) {
                    out.add(lines.getString(k));
                }
                d.rants.put(id, out);
            } catch (RuntimeException ignored) {
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        rants.forEach((id, lines) -> {
            CompoundTag t = new CompoundTag();
            t.putString("id", id.toString());
            ListTag ls = new ListTag();
            for (String line : lines) {
                ls.add(net.minecraft.nbt.StringTag.valueOf(line));
            }
            t.put("lines", ls);
            list.add(t);
        });
        tag.put("rants", list);
        return tag;
    }
}
