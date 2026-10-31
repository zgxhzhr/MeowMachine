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
 * 离线结论信箱的世界存档：{@code <世界>/data/meow_machine_inbox.dat}。
 *
 * <p>队列和草稿都落盘之后，只剩「作者离线时攒下的裁决结果」还在内存里——
 * 万一服务器在作者上线前重启，审核员的一锤定音就没了。现在一并写穿存档。
 *
 * <p>NBT 结构：{@code inbox} 是 {@code ListTag}，每项一条待补发结论
 * （author / submissionId / verdict / reviewer / stickerId / findings）。
 */
public final class InboxSavedData extends SavedData {

    public static final String DATA_NAME = "meow_machine_inbox";

    private final Map<UUID, List<ResultInbox.Delivery>> entries = new LinkedHashMap<>();

    public static InboxSavedData get(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.overworld().getDataStorage()
                .computeIfAbsent(InboxSavedData::load, InboxSavedData::new, DATA_NAME);
    }

    /** 全部待补发结论（按作者） */
    public Map<UUID, List<ResultInbox.Delivery>> entries() {
        Map<UUID, List<ResultInbox.Delivery>> copy = new LinkedHashMap<>();
        entries.forEach((k, v) -> copy.put(k, new ArrayList<>(v)));
        return copy;
    }

    void put(UUID author, ResultInbox.Delivery delivery) {
        if (author == null || delivery == null) {
            return;
        }
        entries.computeIfAbsent(author, k -> new ArrayList<>()).add(delivery);
        setDirty();
    }

    void clear(UUID author) {
        if (entries.remove(author) != null) {
            setDirty();
        }
    }

    // ---------------- 序列化 ----------------

    public static InboxSavedData load(CompoundTag tag) {
        InboxSavedData data = new InboxSavedData();
        ListTag list = tag.getList("inbox", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                UUID author = UUID.fromString(t.getString("author"));
                UUID submissionId = UUID.fromString(t.getString("submissionId"));
                Verdict verdict = Verdict.valueOf(t.getString("verdict"));
                String reviewer = t.getString("reviewer");
                String stickerId = t.getString("stickerId");

                List<ReviewFinding> findings = new ArrayList<>();
                ListTag fl = t.getList("findings", Tag.TAG_COMPOUND);
                for (int j = 0; j < fl.size(); j++) {
                    CompoundTag f = fl.getCompound(j);
                    Integer ruleId = f.contains("ruleId") ? f.getInt("ruleId") : null;
                    String quote = f.contains("quote") ? f.getString("quote") : null;
                    findings.add(new ReviewFinding(ruleId, quote, f.getString("reason")));
                }

                ReviewResult result = ReviewResult.of(verdict, findings, reviewer, stickerId);
                data.entries.computeIfAbsent(author, k -> new ArrayList<>())
                        .add(new ResultInbox.Delivery(submissionId, result));
            } catch (RuntimeException e) {
                MeowMachine.LOG.warn("[喵呜机] 离线结论存档里有坏条目，已跳过: {}", e.toString());
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        entries.forEach((author, deliveries) -> {
            for (ResultInbox.Delivery d : deliveries) {
                CompoundTag t = new CompoundTag();
                t.putString("author", author.toString());
                t.putString("submissionId", d.submissionId() == null ? "" : d.submissionId().toString());
                t.putString("verdict", d.result().verdict().name());
                t.putString("reviewer", d.result().reviewer() == null ? "" : d.result().reviewer());
                t.putString("stickerId", d.result().stickerId() == null ? "" : d.result().stickerId());

                ListTag findings = new ListTag();
                for (ReviewFinding f : d.result().findings()) {
                    CompoundTag fc = new CompoundTag();
                    if (f.ruleId() != null) {
                        fc.putInt("ruleId", f.ruleId());
                    }
                    if (f.quote() != null) {
                        fc.putString("quote", f.quote());
                    }
                    fc.putString("reason", f.reason() == null ? "" : f.reason());
                    findings.add(fc);
                }
                t.put("findings", findings);
                list.add(t);
            }
        });
        tag.put("inbox", list);
        return tag;
    }
}
