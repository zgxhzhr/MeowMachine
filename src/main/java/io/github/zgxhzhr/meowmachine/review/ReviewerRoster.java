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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
 * </ol>
 *
 * <p><b>加载时机</b>：{@code MeowMachine} 在 {@code FMLCommonSetupEvent} 里调用 {@link #load(Path)}。
 * 0.1.0 忘了调，导致 {@code file} 恒为 null → {@code save()} 直接空转、
 * 名册从不落盘（重启全丢）。现在 {@link #file()} 还做了兜底：
 * 即使忘了加载，第一次写盘也会自己补上路径，不会再静默失败。
 *
 * <p>0.4.0 起名册**连玩家名一起存**，后台的「审核员管理」界面才能认人（只有 UUID 没法看）。
 * 旧格式（{@code "uuid": "reviewer"}）仍能读，读到后名字留空、界面显示短 UUID。
 */
public final class ReviewerRoster {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "meow_machine_reviewers.json";

    /** 名册成员：uuid + 名字（可空）+ 角色 */
    public record Member(UUID uuid, String name, ReviewerRole role) {
    }

    /** UUID → 成员 */
    private static final Map<UUID, Member> ROSTER = new ConcurrentHashMap<>();

    /** 单人世界：作者是否可当自己的审核员 */

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
                JsonObject reviewers = root.has("reviewers") ? root.getAsJsonObject("reviewers") : null;
            if (reviewers != null) {
                for (String key : reviewers.keySet()) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        var value = reviewers.get(key);
                        if (value.isJsonObject()) {
                            JsonObject o = value.getAsJsonObject();
                            String name = o.has("name") ? o.get("name").getAsString() : "";
                            ReviewerRole role = ReviewerRole.valueOf(
                                    o.get("role").getAsString().toUpperCase());
                            ROSTER.put(uuid, new Member(uuid, name, role));
                        } else {
                            // 旧格式：uuid → 角色字符串，没有名字
                            ReviewerRole role = ReviewerRole.valueOf(value.getAsString().toUpperCase());
                            ROSTER.put(uuid, new Member(uuid, "", role));
                        }
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        MeowMachine.LOG.warn("[喵呜机] 审核员名册里有非法条目，已跳过: {}", key);
                    }
                }
            }
            MeowMachine.LOG.info("[喵呜机] 审核员名册加载完成：{} 人", ROSTER.size());
        } catch (IOException | RuntimeException e) {
            MeowMachine.LOG.warn("[喵呜机] 读取审核员名册失败，按空名册处理: {}", e.toString());
        }
    }

    private static synchronized void save() {
        Path f = file();
        try {
            JsonObject root = new JsonObject();
                JsonObject reviewers = new JsonObject();
            ROSTER.forEach((uuid, member) -> {
                JsonObject o = new JsonObject();
                o.addProperty("name", member.name() == null ? "" : member.name());
                o.addProperty("role", member.role().name().toLowerCase());
                reviewers.add(uuid.toString(), o);
            });
            root.add("reviewers", reviewers);
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            MeowMachine.LOG.warn("[喵呜机] 写审核员名册失败: {}", e.toString());
        }
    }

    // ---------------- 任命 ----------------

    /** 任命（OP 专用；调用方自己校验权限） */
    public static void grant(UUID player, String name, ReviewerRole role) {
        ROSTER.put(player, new Member(player, name == null ? "" : name, role));
        save();
    }

    public static void revoke(UUID player) {
        ROSTER.remove(player);
        save();
    }



    /** 名册成员列表（按名字排序，界面/命令用） */
    public static List<Member> members() {
        List<Member> list = new ArrayList<>(ROSTER.values());
        list.sort(Comparator.comparing(m -> m.name() == null ? "" : m.name(),
                String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** 是否在名册里（不看角色，助理也算） */
    public static boolean isRosterMember(UUID player) {
        return player != null && ROSTER.containsKey(player);
    }

    public static ReviewerRole roleOf(UUID player) {
        Member m = player == null ? null : ROSTER.get(player);
        return m == null ? null : m.role();
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
     */
    public static boolean canFinalize(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (isOp(player)) {
            return true;
        }
        ReviewerRole role = roleOf(player.getUUID());
        return role != null && role.canFinalize();
    }

    /**
     * 能否标记发现 / 查看待审队列（助理及以上）。
     *
     * <p>OP 与名册成员（审核员 / 助理）都能看队列、写意见；终裁权限另由
     * {@link #canFinalize} 与角色决定。
     */
    public static boolean canAnnotate(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return isOp(player) || isRosterMember(player.getUUID());
    }

    /** 当前是否**存在**任何能终审的人（决定「人工优先」是否成立） */
    public static boolean hasAnyFinalizer() {
        return ROSTER.values().stream().anyMatch(m -> m.role().canFinalize());
    }
}
