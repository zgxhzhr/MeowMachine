package io.github.zgxhzhr.meowmachine.review;

import io.github.zgxhzhr.meowmachine.MeowMachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「离线作者待领取的审核结果」信箱。
 *
 * <p>场景：审核员裁决时作者已经下线。以前这条路径会把结论**静默扔掉**
 * （队列已 claim、草稿没写、也没通知），作者再上线就永远等不到结果。
 * 现在离线时先存进这里，玩家登录时由 {@code MeowMachineEvents} 补发。
 *
 * <p>与 {@link SubmissionQueue} 一样是内存态：服务端重启会清空。
 * 队列本身也还没持久化，等队列上了 SavedData 时这里一并处理。
 */
public final class ResultInbox {

    /** 一条待补发的结论 */
    public record Delivery(UUID submissionId, ReviewResult result) {
    }

    private static final Map<UUID, List<Delivery>> PENDING = new ConcurrentHashMap<>();

    private ResultInbox() {
    }

    /** 存一条待补发结论 */
    public static void offer(UUID author, UUID submissionId, ReviewResult result) {
        if (author == null || result == null) {
            return;
        }
        PENDING.computeIfAbsent(author, k -> new ArrayList<>()).add(new Delivery(submissionId, result));
        MeowMachine.LOG.info("[喵呜机] 作者不在线，结论已存入待补发信箱 author={} verdict={}",
                author, result.verdict());
    }

    /** 取走某玩家的全部待补发结论（取完即清） */
    public static List<Delivery> drain(UUID author) {
        List<Delivery> list = PENDING.remove(author);
        return list == null ? List.of() : list;
    }

    /** 当前积压条数（后台/调试用） */
    public static int size() {
        return PENDING.values().stream().mapToInt(List::size).sum();
    }
}
