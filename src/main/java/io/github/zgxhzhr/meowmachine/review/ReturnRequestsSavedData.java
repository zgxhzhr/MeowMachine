package io.github.zgxhzhr.meowmachine.review;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 已通过条目的「退回申请」：{@code <世界>/data/meow_machine_return_requests.dat}。 */
public final class ReturnRequestsSavedData extends SavedData {

    public static final String DATA_NAME = "meow_machine_return_requests";

    public record Request(UUID entryId, String requester, String reason, long time) {
    }

    private final Map<UUID, Request> requests = new LinkedHashMap<>();

    public static ReturnRequestsSavedData get(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.overworld().getDataStorage()
                .computeIfAbsent(ReturnRequestsSavedData::load, ReturnRequestsSavedData::new, DATA_NAME);
    }

    Map<UUID, Request> requests() {
        return requests;
    }

    void put(Request r) {
        requests.put(r.entryId(), r);
        setDirty();
    }

    void remove(UUID id) {
        if (requests.remove(id) != null) {
            setDirty();
        }
    }

    public static ReturnRequestsSavedData load(CompoundTag tag) {
        ReturnRequestsSavedData d = new ReturnRequestsSavedData();
        ListTag list = tag.getList("requests", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                UUID id = UUID.fromString(t.getString("id"));
                d.requests.put(id, new Request(id, t.getString("requester"),
                        t.getString("reason"), t.getLong("time")));
            } catch (RuntimeException ignored) {
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Request r : requests.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", r.entryId().toString());
            t.putString("requester", r.requester());
            t.putString("reason", r.reason());
            t.putLong("time", r.time());
            list.add(t);
        }
        tag.put("requests", list);
        return tag;
    }
}
