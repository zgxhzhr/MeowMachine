package io.github.zgxhzhr.meowmachine.review;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.zgxhzhr.meowmachine.MeowMachine;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审核员名册。存在 {@code config/meow_machine_reviewers.json}。
 *
 * <p>权限判定顺序（对应设计里的「人工优先」）：
 * <ol>
 *   <li><b>OP</b> 天然是审核员（{@code hasPermissions(2)}），不需要任命</li>
 *   <li>被任命的 {@link ReviewerRole#REVIEWER} 可以终审</li>
 *   <li>被任命的 {@link ReviewerRole#ASSISTANT} 只能标记，不能终审</li>
 *   <li>单人且开了 {@code selfReview} 时，作者本人可以终审自己的稿</li>
 * </ol>
 */
public final class ReviewerRoster {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "meow_machine_reviewers.json";

    /** UUID → 角色 */
    private static final Map<UUID, ReviewerRole> ROSTER = new ConcurrentHashMap<>();

    /** 单人世界：作者是否可当自己的审核员 */
    private static volatile boolean selfReview = false;

    private static volatile Path file = null;

    private ReviewerRoster() {
    }

    public static void load(Path configDir) {
        file = configDir.resolve(FILE_NAME);
        ROSTER.clear();
        try {
            if (!Files.isRegularFile(file)) {
                save();
                return;
            }
            JsonObject root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    JsonObject.class);
            if (root == null) {
                return;
            }
            selfReview = root.has("selfReview") && root.get("selfReview").getAsBoolean();
            JsonObject reviewers = root.has("reviewers") ? root.getAsJsonObject("reviewers") : null;
            if (reviewers != null) {
                for (String key : reviewers.keySet()) {
                    try {
                        ROSTER.put(UUID.fromString(key),
                                ReviewerRole.valueOf(reviewers.get(key).getAsString().toUpperCase()));
                    } catch (IllegalArgumentException ignored) {
                        MeowMachine.LOG.warn("[喵呜机] 审核员名册里有非法条目，已跳过: {}", key);
                    }
                }
            }
            MeowMachine.LOG.info("[喵呜机] 审核员名册加载完成：{} 人（selfReview={}）",
                    ROSTER.size(), selfReview);
        } catch (IOException | RuntimeException e) {
            MeowMachine.LOG.warn("[喵呜机] 读取审核员名册失败，按空名册处理: {}", e.toString());
        }
    }

    private static synchronized void save() {
        if (file == null) {
            return;
        }
        try {
            JsonObject root = new JsonObject();
            root.addProperty("selfReview", selfReview);
            JsonObject reviewers = new JsonObject();
            ROSTER.forEach((k, v) -> reviewers.addProperty(k.toString(), v.name().toLowerCase()));
            root.add("reviewers", reviewers);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MeowMachine.LOG.warn("[喵呜机] 写审核员名册失败: {}", e.toString());
        }
    }

    // ---------------- 任命 ----------------

    /** 任命（OP 专用；调用方自己校验权限） */
    public static void grant(UUID player, ReviewerRole role) {
        ROSTER.put(player, role);
        save();
    }

    public static void revoke(UUID player) {
        ROSTER.remove(player);
        save();
    }

    public static void setSelfReview(boolean enabled) {
        selfReview = enabled;
        save();
    }

    public static boolean isSelfReviewEnabled() {
        return selfReview;
    }

    public static ReviewerRole roleOf(UUID player) {
        return ROSTER.get(player);
    }

    // ---------------- 判定 ----------------

    /** 是否是 OP（天然审核权） */
    public static boolean isOp(ServerPlayer player) {
        return player != null && player.hasPermissions(2);
    }

    /**
     * 能否给出最终结论。
     *
     * @param player 判定对象
     * @param author 投稿作者（用于 selfReview 判定；可为 null）
     */
    public static boolean canFinalize(ServerPlayer player, UUID author) {
        if (player == null) {
            return false;
        }
        if (isOp(player)) {
            return true;
        }
        ReviewerRole role = ROSTER.get(player.getUUID());
        if (role != null && role.canFinalize()) {
            return true;
        }
        return selfReview && author != null && author.equals(player.getUUID());
    }

    /** 能否标记发现（助理及以上） */
    public static boolean canAnnotate(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return isOp(player) || ROSTER.containsKey(player.getUUID());
    }

    /** 当前是否**存在**任何能终审的人（决定「人工优先」是否成立） */
    public static boolean hasAnyFinalizer() {
        return !ROSTER.isEmpty() && ROSTER.values().stream().anyMatch(ReviewerRole::canFinalize);
    }
}
