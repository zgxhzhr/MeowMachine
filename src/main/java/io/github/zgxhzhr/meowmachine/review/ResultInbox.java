package io.github.zgxhzhr.meowmachine.review;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「离线作者待领取的审核结果」信箱。
 *
 * <p>场景：审核员裁决时作者已经下线。以前这条路径会把结论**静默扔掉**
 * （队列已 claim、草稿没写、也没通知），作者再上线就永远等不到结果。
 * 现在离线时先存进这里，玩家登录时由 {@code MeowMachineEvents} 补发。
 *
 * <p><b>持久化</b>：与队列、草稿一致，服务端启动时 {@link #attach(MinecraftServer)}
 * 从 {@link InboxSavedData}（{@code <世界>/data/meow_machine_inbox.dat}）读回，
 * 每次投递/取走都写穿，重启不会丢一条已经拍板的结论。
 * 没 attach 时（纯逻辑测试）退化为内存。
 */
public final class ResultInbox {

    /** 一条待补发的结论 */
    public record Delivery(UUID submissionId, ReviewResult result) {
    }

    private static final Map<UUID, List<Delivery>> PENDING = new LinkedHashMap<>();

    /** 存档后端；null 表示没有世界存档（退化为纯内存） */
    private static volatile InboxSavedData store = null;

    private ResultInbox() {
    }

    /** 服务端启动：绑定存档并灌入已有待补发结论 */
    public static void attach(MinecraftServer server) {
        store = InboxSavedData.get(server);
        PENDING.clear();
        if (store != null) {
            store.entries().forEach((author, list) -> PENDING.put(author, new ArrayList<>(list)));
        }
        MeowMachine.LOG.info("[喵呜机] 离线结论信箱已恢复：{} 位作者 / {} 条",
                PENDING.size(), PENDING.values().stream().mapToInt(List::size).sum());
    }

    /** 服务端停止：解绑 */
    public static void detach() {
        store = null;
    }

    /** 存一条待补发结论 */
    public static void offer(UUID author, UUID submissionId, ReviewResult result) {
        if (author == null || result == null) {
            return;
        }
        Delivery delivery = new Delivery(submissionId, result);
        PENDING.computeIfAbsent(author, k -> new ArrayList<>()).add(delivery);
        InboxSavedData s = store;
        if (s != null) {
            s.put(author, delivery);
        }
        MeowMachine.LOG.info("[喵呜机] 作者不在线，结论已存入待补发信箱 author={} verdict={}",
                author, result.verdict());
    }

    /** 取走某玩家的全部待补发结论（取完即清） */
    public static List<Delivery> drain(UUID author) {
        List<Delivery> list = PENDING.remove(author);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        InboxSavedData s = store;
        if (s != null) {
            s.clear(author);
        }
        return list;
    }

    /** 当前积压条数（后台/调试用） */
    public static int size() {
        return PENDING.values().stream().mapToInt(List::size).sum();
    }
}
