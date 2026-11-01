package io.github.zgxhzhr.meowmachine.review;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 草稿的世界存档数据：{@code <世界>/data/meow_machine_drafts.dat}。
 *
 * <p>放在世界存档里而不是玩家 NBT，是接手时的决定：草稿是「这个存档里的投稿」，
 * 跟档走更好备份/迁移；而且玩家 NBT 只有在线实体才有，离线补写很别扭
 * （审核员裁决时作者常常已经下线，见 {@link ReviewActions} 的离线补发）。
 *
 * <p>存取都走服务端主线程，所以内部用普通 {@link HashMap} 就够了。
 */
public final class DraftSavedData extends SavedData {

    public static final String DATA_NAME = "meow_machine_drafts";

    private final Map<UUID, Submission> drafts = new HashMap<>();

    public static DraftSavedData get(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.overworld().getDataStorage()
                .computeIfAbsent(DraftSavedData::load, DraftSavedData::new, DATA_NAME);
    }

    public Submission get(UUID author) {
        return drafts.get(author);
    }

    public void put(Submission submission) {
        if (submission == null) {
            return;
        }
        drafts.put(submission.authorUuid(), submission);
        setDirty();
    }

    // ---------------- 序列化 ----------------

    public static DraftSavedData load(CompoundTag tag) {
        DraftSavedData data = new DraftSavedData();
        ListTag list = tag.getList("drafts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                UUID author = UUID.fromString(t.getString("author"));
                UUID id = UUID.fromString(t.getString("id"));
                SubmissionState state;
                boolean reEdit = t.getBoolean("re_edit");
                try {
                    state = SubmissionState.valueOf(t.getString("state"));
                } catch (IllegalArgumentException e) {
                    state = SubmissionState.DRAFT;
                    reEdit = false;
                }
                Submission sub = new Submission(id, author, t.getString("authorName"),
                        t.getString("title"), t.getString("body"), state,
                        t.getInt("revision"), reEdit);
                data.drafts.put(author, sub);
            } catch (RuntimeException e) {
                // 单条坏了不该让整份存档读不出来
                io.github.zgxhzhr.meowmachine.MeowMachine.LOG.warn(
                        "[喵呜机] 草稿存档里有坏条目，已跳过: {}", e.toString());
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Submission s : drafts.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("author", s.authorUuid().toString());
            t.putString("authorName", s.authorName());
            t.putString("id", s.id().toString());
            t.putString("title", s.title());
            t.putString("body", s.body());
            t.putString("state", s.state().name());
            t.putInt("revision", s.revision());
            list.add(t);
        }
        tag.put("drafts", list);
        return tag;
    }
}
