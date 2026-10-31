package io.github.zgxhzhr.meowmachine.review;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.zgxhzhr.meowmachine.MeowMachine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

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
 *
 * <p><b>加载时机</b>：{@code MeowMachine} 在 {@code FMLCommonSetupEvent} 里调用 {@link #load(Path)}。
 * 0.1.0 忘了调，导致 {@code file} 恒为 null → {@code save()} 直接空转、
 * 名册与 selfReview 开关从不落盘（重启全丢）。现在 {@link #file()} 还做了兜底：
 * 即使忘了加载，第一次写盘也会自己补上路径，不会再静默失败。
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

    /** 目标文件路径；没加载过就用 config 目录兜底，避免 save() 静默变成空操作 */
    private static Path file() {
        Path f = file;
        if (f == null) {
            f = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
            file = f;
        }
        return f;
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
        Path f = file();
        try {
            JsonObject root = new JsonObject();
            root.addProperty("selfReview", selfReview);
            JsonObject reviewers = new JsonObject();
            ROSTER.forEach((k, v) -> reviewers.addProperty(k.toString(), v.name().toLowerCase()));
            root.add("reviewers", reviewers);
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
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

    /** 名册快照（后台/命令列表用） */
    public static Map<UUID, ReviewerRole> snapshot() {
        return Map.copyOf(ROSTER);
    }

    /** 是否在名册里（不看角色，助理也算） */
    public static boolean isRosterMember(UUID player) {
        return player != null && ROSTER.containsKey(player);
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

    /**
     * 能否标记发现 / 查看待审队列（助理及以上）。
     *
     * <p>注意这里**包含单人自审作者**：selfReview 让作者能拍板自己的稿，
     * 那他就必须能看到自己那份待审（否则能裁决却拿不到 id，人工路径死锁）。
     * 队列本身在命令层还会再收窄成「只看自己的」。
     */
    public static boolean canAnnotate(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return isOp(player) || isRosterMember(player.getUUID()) || selfReview;
    }

    /** 当前是否**存在**任何能终审的人（决定「人工优先」是否成立） */
    public static boolean hasAnyFinalizer() {
        return ROSTER.values().stream().anyMatch(ReviewerRole::canFinalize);
    }
}
