package io.github.zgxhzhr.meowmachine.review;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 人工审核队列。
 *
 * <p><b>持久化</b>：服务端启动时 {@link #attach(MinecraftServer)} 把
 * {@link QueueSavedData}（{@code <世界>/data/meow_machine_queue.dat}）读进内存，
 * 之后每次入队/裁决都写穿回存档，所以重启不再丢待审。
 * 没 attach 时（纯逻辑测试/客户端）退化为纯内存。
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

    /** 存档后端；null 表示当前没有世界存档（退化为纯内存） */
    private static volatile QueueSavedData store = null;

    private SubmissionQueue() {
    }

    /** 服务端启动时绑定存档并灌入已有待审 */
    public static void attach(MinecraftServer server) {
        store = QueueSavedData.get(server);
        PENDING.clear();
        if (store != null) {
            PENDING.putAll(store.entries());
        }
        MeowMachine.LOG.info("[喵呜机] 待审队列已从存档恢复：{} 份", PENDING.size());
    }

    /** 服务端停止时解绑（之后不再往存档写） */
    public static void detach() {
        store = null;
    }

    /** 入队。重复入队（同 id）会覆盖，保持幂等 */
    public static void enqueue(Submission sub, long now) {
        if (sub == null) {
            return;
        }
        Entry entry = new Entry(sub, now);
        PENDING.put(sub.id(), entry);
        QueueSavedData s = store;
        if (s != null) {
            s.put(entry);
        }
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
        Entry removed = PENDING.remove(id);
        if (removed != null) {
            QueueSavedData s = store;
            if (s != null) {
                s.remove(id);
            }
        }
        return Optional.ofNullable(removed);
    }

    /** 仅移除（例如作者撤回、或作者又开始改稿） */
    public static boolean drop(UUID id) {
        boolean removed = PENDING.remove(id) != null;
        if (removed) {
            QueueSavedData s = store;
            if (s != null) {
                s.remove(id);
            }
        }
        return removed;
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
