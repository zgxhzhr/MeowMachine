package io.github.zgxhzhr.meowmachine.review;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 已通过条目门面（内存镜像 + 存档写穿）。 */
public final class ApprovedIndex {

    private static volatile ApprovedSavedData store = null;

    private ApprovedIndex() {
    }

    public static void attach(MinecraftServer server) {
        store = ApprovedSavedData.get(server);
        backfillFromHistory();
    }

    /**
     * 用「投稿历史」里已通过的记录回填索引。
     *
     * <p>索引是 0.26 才加的；之前的通过记录只存在历史里，不回填的话
     * 「已通过条目」列表对老存档永远是空的（实测）。
     */
    private static void backfillFromHistory() {
        if (store == null) {
            return;
        }
        int added = 0;
        for (SubmissionHistory.Record r : SubmissionHistory.allRecords()) {
            if (!"approved".equals(r.state()) || store.entries().containsKey(r.id())) {
                continue;
            }
            store.put(new ApprovedSavedData.Entry(r.id(), r.title(), "", "", r.body(),
                    r.revision(), r.time()));
            added++;
        }
        if (added > 0) {
            io.github.zgxhzhr.meowmachine.MeowMachine.LOG.info(
                    "[喵呜机] 已通过索引回填 {} 条（来自投稿历史）", added);
        }
    }

    public static void detach() {
        store = null;
    }

    /** 通过时登记（含正文，供之后的修改对比） */
    public static void record(Submission sub) {
        if (store == null || sub == null || sub.state() != SubmissionState.APPROVED) {
            return;
        }
        // 已通过条目允许别人提交编辑：内容更新，但**原作者署名保留**
        // （否则编辑者过审后会把原作者的署名顶掉 —— 审查 P0-5）
        String author = sub.authorName();
        String authorUuid = sub.authorUuid() == null ? "" : sub.authorUuid().toString();
        ApprovedSavedData.Entry old = store.entries().get(sub.id());
        if (old != null && old.authorUuid() != null && !old.authorUuid().isBlank()
                && !old.authorUuid().equals(authorUuid)) {
            author = old.author();
            authorUuid = old.authorUuid();
        }
        store.put(new ApprovedSavedData.Entry(sub.id(), sub.title(), author, authorUuid,
                sub.body(), sub.revision(), System.currentTimeMillis()));
    }

    /** 退回：把条目从已通过索引里摘掉（作者之后可重新投稿） */
    public static void remove(UUID id) {
        if (store != null && id != null) {
            store.remove(id);
        }
    }

    public static Optional<ApprovedSavedData.Entry> find(UUID id) {
        return store == null || id == null ? Optional.empty()
                : Optional.ofNullable(store.entries().get(id));
    }

    /** 全部已通过条目（新→旧） */
    public static List<ApprovedSavedData.Entry> all() {
        if (store == null) {
            return List.of();
        }
        List<ApprovedSavedData.Entry> list = new ArrayList<>(store.entries().values());
        list.sort(Comparator.comparingLong(ApprovedSavedData.Entry::time).reversed());
        return list;
    }

    public static boolean isApproved(UUID id) {
        return find(id).isPresent();
    }
}
