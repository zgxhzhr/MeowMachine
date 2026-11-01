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
    }

    public static void detach() {
        store = null;
    }

    /** 通过时登记（含正文，供之后的修改对比） */
    public static void record(Submission sub) {
        if (store == null || sub == null || sub.state() != SubmissionState.APPROVED) {
            return;
        }
        store.put(new ApprovedSavedData.Entry(sub.id(), sub.title(), sub.authorName(),
                sub.body(), sub.revision(), System.currentTimeMillis()));
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
