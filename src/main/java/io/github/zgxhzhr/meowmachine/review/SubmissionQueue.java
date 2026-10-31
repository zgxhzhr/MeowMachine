package io.github.zgxhzhr.meowmachine.review;

import io.github.zgxhzhr.meowmachine.MeowMachine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 人工审核队列。
 *
 * <p>**仍是纯内存**：服务端重启会清空。持久化（世界存档）还没做——
 * 这是接手时留下的最大一项未完成，已写在交接文档的「未完成」里。
 *
 * <p>用 {@link LinkedHashMap} 保持提交顺序（先来先审）。
 * 所有调用点都在服务端主线程（网络包的 consumerMainThread / 命令 / 事件），
 * 所以不需要额外加锁。
 */
public final class SubmissionQueue {

    /** 队列项：稿件 + 提交时间 */
    public record Entry(Submission submission, long submittedAt) {
    }

    private static final Map<UUID, Entry> PENDING = new LinkedHashMap<>();

    private SubmissionQueue() {
    }

    /** 入队。重复入队（同 id）会覆盖，保持幂等 */
    public static void enqueue(Submission sub, long now) {
        if (sub == null) {
            return;
        }
        PENDING.put(sub.id(), new Entry(sub, now));
        MeowMachine.LOG.info("[喵呜机] 稿件入队 id={} author={} 当前待审 {} 份",
                sub.id(), sub.authorName(), PENDING.size());
    }

    /** 待审快照（按提交顺序） */
    public static List<Entry> snapshot() {
        return new ArrayList<>(PENDING.values());
    }

    public static int size() {
        return PENDING.size();
    }

    /** 取出并从队列移除（裁决后调用） */
    public static Optional<Entry> claim(UUID id) {
        return Optional.ofNullable(PENDING.remove(id));
    }

    /** 仅移除（例如作者撤回、或作者又开始改稿） */
    public static boolean drop(UUID id) {
        return PENDING.remove(id) != null;
    }

    /** 按作者查（作者想看自己有哪些在审；单人自审时也只该看到自己的） */
    public static List<Entry> byAuthor(UUID author) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : PENDING.values()) {
            if (e.submission().authorUuid().equals(author)) {
                out.add(e);
            }
        }
        return out;
    }
}
