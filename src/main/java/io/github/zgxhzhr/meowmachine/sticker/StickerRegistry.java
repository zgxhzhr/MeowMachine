package io.github.zgxhzhr.meowmachine.sticker;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.zgxhzhr.meowmachine.MeowMachine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 梗图注册表。**从数据包加载**，加新表情只需丢一个 json（+ 可选 png），不改代码。
 *
 * <p>数据位置：{@code data/<命名空间>/stickers/*.json}
 *
 * <pre>{@code
 * {
 *   "texture": "meow_machine:textures/sticker/returned.png",  // 可省略，默认按 id 推
 *   "state": "returned",        // 或 "rule": 22  或 "fallback": true
 *   "priority": 10
 * }
 * }</pre>
 *
 * <p><b>只在服务端加载</b>：注册点是 Forge 的服务端数据重载事件
 * （{@code AddReloadListenerEvent}）。客户端不查询本表——渲染用的是服务端随结果包
 * 发过来的纹理路径（{@link #texturePath(String)}），所以专用服务器 + 原版客户端也能正常显示。
 */
public final class StickerRegistry extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    /** 当前生效的梗图 */
    private static volatile List<Sticker> ALL = List.of();
    /** id → 梗图 */
    private static volatile Map<ResourceLocation, Sticker> BY_ID = Map.of();
    /** 状态 → 该状态下的所有梗图（同状态可能有多张，取用时随机） */
    private static volatile Map<String, List<Sticker>> BY_STATE = Map.of();
    private static volatile Map<Integer, Sticker> BY_RULE = Map.of();
    private static volatile Sticker FALLBACK = null;

    public StickerRegistry() {
        super(GSON, "stickers");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        List<Sticker> loaded = new ArrayList<>();
        files.forEach((fileId, element) -> {
            try {
                JsonObject o = element.getAsJsonObject();
                String path = fileId.getPath();
                if (path.contains("/")) {
                    path = path.substring(path.lastIndexOf('/') + 1);
                }
                ResourceLocation id = new ResourceLocation(fileId.getNamespace(), path);

                ResourceLocation texture = o.has("texture")
                        ? new ResourceLocation(o.get("texture").getAsString()) : null;
                String state = o.has("state") ? o.get("state").getAsString() : null;
                Integer rule = o.has("rule") ? o.get("rule").getAsInt() : null;
                boolean fallback = o.has("fallback") && o.get("fallback").getAsBoolean();
                int priority = o.has("priority") ? o.get("priority").getAsInt() : 0;
                String desc = o.has("desc") ? o.get("desc").getAsString() : "";

                loaded.add(new Sticker(id, texture, state, rule, fallback, priority, desc));
            } catch (Exception e) {
                MeowMachine.LOG.warn("[喵呜机] 梗图定义解析失败 {}: {}", fileId, e.toString());
            }
        });

        // 按 priority 降序，查询时取第一个命中的即可
        loaded.sort(Comparator.comparingInt(Sticker::priority).reversed());

        Map<ResourceLocation, Sticker> byId = new HashMap<>();
        Map<String, List<Sticker>> byState = new HashMap<>();
        Map<Integer, Sticker> byRule = new HashMap<>();
        Sticker fallback = null;
        List<String> untriggerable = new ArrayList<>();
        for (Sticker s : loaded) {
            byId.put(s.id(), s);
            if (s.fallback()) {
                if (fallback == null) {
                    fallback = s;
                }
                continue;
            }
            if (s.state() != null) {
                byState.computeIfAbsent(s.state().toLowerCase(), k -> new ArrayList<>()).add(s);
            }
            if (s.rule() != null) {
                byRule.putIfAbsent(s.rule(), s);
            }
            if (s.state() == null && s.rule() == null) {
                untriggerable.add(s.id().toString());
            }
        }

        ALL = List.copyOf(loaded);
        BY_ID = Map.copyOf(byId);
        BY_STATE = Map.copyOf(byState);
        BY_RULE = Map.copyOf(byRule);
        FALLBACK = fallback;

        MeowMachine.LOG.info("[喵呜机] 梗图加载完成：{} 张（状态 {} / 规则 {} / 兜底 {}）",
                loaded.size(), byState.size(), byRule.size(), fallback != null);
        if (!untriggerable.isEmpty()) {
            // 这类图加载了但任何查询都取不到，是最容易悄悄失效的一类配置错误，
            // 所以启动时就明确报出来（而不是等人问「为什么这张图从没出现」）。
            MeowMachine.LOG.warn("[喵呜机] 以下梗图没有 state/rule/fallback，永远不会被选中: {}",
                    String.join(", ", untriggerable));
        }
    }

    // ---------------- 查询 ----------------

    /**
     * 按投稿状态查。
     *
     * <p>**同一状态可能有多张图**（例如「通过」有一整个系列），此时**随机取一张**，
     * 避免玩家反复看到同一条反馈。数据包里加新图会自动进池，不用改代码。
     */
    public static Optional<Sticker> forState(String state) {
        if (state == null) {
            return Optional.empty();
        }
        List<Sticker> list = BY_STATE.get(state.toLowerCase());
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(list.size())));
    }

    /** 按规则号查（命中某条规范时用） */
    public static Optional<Sticker> forRule(int ruleId) {
        return Optional.ofNullable(BY_RULE.get(ruleId));
    }

    /** 兜底图：审核者说不出具体依据时用 */
    public static Optional<Sticker> fallback() {
        return Optional.ofNullable(FALLBACK);
    }

    /** 该 id 是否存在（AI 返回的选图要校验） */
    public static boolean exists(String stickerId) {
        if (stickerId == null || stickerId.isBlank()) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(stickerId);
        return id != null && BY_ID.containsKey(id);
    }

    /**
     * 给 AI 的梗图目录：只列**能被选中**的图（有 state / rule / fallback 的）。
     *
     * <p>AI 判完可以自己挑一张最贴切的图，比本地「按规则号硬映射」更细
     * （尤其是四张「通过」：有进步 / 普通 / 简短 / 有小瑕疵）。
     */
    public static List<String> catalogLines() {
        List<String> lines = new ArrayList<>();
        for (Sticker s : ALL) {
            if (!s.fallback() && s.state() == null && s.rule() == null) {
                continue;
            }
            StringBuilder sb = new StringBuilder("- ").append(s.id());
            if (s.fallback()) {
                sb.append(" | fallback");
            } else if (s.rule() != null) {
                sb.append(" | rule ").append(s.rule());
            } else {
                sb.append(" | state ").append(s.state());
            }
            if (s.desc() != null && !s.desc().isBlank()) {
                sb.append(" | ").append(s.desc());
            }
            lines.add(sb.toString());
        }
        return lines;
    }

    /**
     * 算出一张梗图的实际纹理路径，空串表示「没有图」。
     *
     * <p>结果包只带 id 时客户端只能按 id 猜路径，数据包里自定义的 {@code texture} 就废了；
     * 这里在服务端算好路径一起发过去（{@code ReviewResultPacket.texture}）。
     */
    public static String texturePath(String stickerId) {
        if (stickerId == null || stickerId.isBlank()) {
            return "";
        }
        ResourceLocation id = ResourceLocation.tryParse(stickerId);
        if (id == null) {
            return "";
        }
        Sticker s = BY_ID.get(id);
        return (s != null ? s.resolvedTexture() : Sticker.textureFor(id)).toString();
    }
}
