package io.github.zgxhzhr.meowmachine.review;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 已出结论的投稿历史（内存镜像 + 世界存档写穿）。
 *
 * <p>用途：「我的投稿」列表要能看到**已通过 / 已打回**的稿子，而不是它们从队列消失后就查无此人。
 *
 * <p>为什么存 body：作者点「编辑」把打回的稿子拉回来改时，得有正文。
 * 已通过的稿子只读（不允许再改再投）。
 */
public final class SubmissionHistory {

    /**
     * 一条历史记录。
     *
     * @param state 小写状态：approved / returned
     */
    public record Record(UUID id, String title, String state, String body, int revision, long time) {
    }

    /** 每人保留多少条（够翻旧账，又不至于把存档撑大） */
    private static final int MAX_PER_PLAYER = 20;

    private static final Map<UUID, List<Record>> BY_AUTHOR = new LinkedHashMap<>();
    private static volatile HistorySavedData store = null;

    private SubmissionHistory() {
    }

    public static void attach(MinecraftServer server) {
        store = HistorySavedData.get(server);
        BY_AUTHOR.clear();
        if (store != null) {
            BY_AUTHOR.putAll(store.entries());
        }
    }

    public static void detach() {
        BY_AUTHOR.clear();
        store = null;
    }

    /**
     * 记下一条结论。只在真的出了结论时调用（approved / returned）。
     *
     * <p>同一 id 再记录会覆盖旧条目（例如先打回、改完再通过，列表里只留最新一条）。
     */
    public static void record(Submission sub) {
        if (sub == null || sub.authorUuid() == null) {
            return;
        }
        SubmissionState st = sub.state();
        if (st != SubmissionState.APPROVED && st != SubmissionState.RETURNED) {
            return;
        }
        List<Record> list = BY_AUTHOR.computeIfAbsent(sub.authorUuid(), k -> new ArrayList<>());
        list.removeIf(r -> r.id().equals(sub.id()));
        list.add(0, new Record(sub.id(), sub.title(),
                st.name().toLowerCase(), sub.body(), sub.revision(), System.currentTimeMillis()));
        while (list.size() > MAX_PER_PLAYER) {
            list.remove(list.size() - 1);
        }
        if (store != null) {
            store.put(sub.authorUuid(), list);
        }
    }

    /** 全部历史记录（回填已通过索引用） */
    public static List<Record> allRecords() {
        List<Record> out = new ArrayList<>();
        BY_AUTHOR.values().forEach(out::addAll);
        return out;
    }

    /** 某个作者的历史（新的在前） */
    public static List<Record> byAuthor(UUID author) {
        if (author == null) {
            return List.of();
        }
        List<Record> list = BY_AUTHOR.get(author);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** 找某一条（编辑打回的稿子时用） */
    public static Optional<Record> find(UUID author, UUID id) {
        if (author == null || id == null) {
            return Optional.empty();
        }
        return byAuthor(author).stream().filter(r -> r.id().equals(id)).findFirst();
    }

    /** 条数（自测/日志用） */
    public static int size() {
        return BY_AUTHOR.values().stream().mapToInt(List::size).sum();
    }
}
