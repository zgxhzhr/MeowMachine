package io.github.zgxhzhr.meowmachine.review;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 审核意见（标注）的门面：内存镜像 + 世界存档写穿。
 *
 * <p>谁能写：任何 {@code canAnnotate} 的玩家（OP / 审核员 / 助理）。
 * 谁能终裁：只有 OP 与审核员——助理的意见要等终裁的人采纳。
 */
public final class ReviewNotes {

    /** 每条意见的长度上限 */
    public static final int MAX_NOTE_LEN = 200;
    /** 每篇最多留几条（防止刷屏） */
    private static final int MAX_PER_SUBMISSION = 6;

    private static final Map<UUID, List<String>> BY_SUBMISSION = new LinkedHashMap<>();
    private static volatile NotesSavedData store = null;

    private ReviewNotes() {
    }

    public static void attach(MinecraftServer server) {
        store = NotesSavedData.get(server);
        BY_SUBMISSION.clear();
        if (store != null) {
            BY_SUBMISSION.putAll(store.entries());
        }
    }

    public static void detach() {
        BY_SUBMISSION.clear();
        store = null;
    }

    /**
     * 记一条意见。
     *
     * @return 是否记下（空内容、超长、超条数都算失败）
     */
    public static boolean add(UUID submissionId, String reviewerName, String text) {
        if (submissionId == null || text == null || text.isBlank()) {
            return false;
        }
        String clean = text.strip();
        if (clean.length() > MAX_NOTE_LEN) {
            clean = clean.substring(0, MAX_NOTE_LEN);
        }
        String line = (reviewerName == null ? "" : reviewerName) + "：" + clean;
        List<String> notes = BY_SUBMISSION.computeIfAbsent(submissionId, k -> new ArrayList<>());
        if (notes.size() >= MAX_PER_SUBMISSION) {
            return false;
        }
        notes.add(line);
        if (store != null) {
            store.put(submissionId, notes);
        }
        return true;
    }

    public static List<String> of(UUID submissionId) {
        if (submissionId == null) {
            return List.of();
        }
        List<String> notes = BY_SUBMISSION.get(submissionId);
        return notes == null ? List.of() : List.copyOf(notes);
    }

    public static Optional<List<String>> find(UUID submissionId) {
        List<String> notes = of(submissionId);
        return notes.isEmpty() ? Optional.empty() : Optional.of(notes);
    }

    /** 终裁时把意见并入结果：拆出「内容」部分，作为附言式说明给作者看 */
    public static List<String> notesOnly(UUID submissionId) {
        List<String> out = new ArrayList<>();
        for (String line : of(submissionId)) {
            int idx = line.indexOf('：');
            out.add(idx >= 0 && idx + 1 < line.length() ? line.substring(idx + 1) : line);
        }
        return out;
    }

    /** 终裁后清掉这篇的意见 */
    public static void clear(UUID submissionId) {
        if (submissionId == null) {
            return;
        }
        if (BY_SUBMISSION.remove(submissionId) != null && store != null) {
            store.put(submissionId, List.of());
        }
    }

    public static int size() {
        return BY_SUBMISSION.values().stream().mapToInt(List::size).sum();
    }
}
