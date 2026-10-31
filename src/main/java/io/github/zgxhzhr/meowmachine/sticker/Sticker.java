package io.github.zgxhzhr.meowmachine.sticker;

import net.minecraft.resources.ResourceLocation;

/**
 * 一张梗图的定义。**完全由数据包提供**，Java 里不出现任何具体表情 id。
 *
 * <p>触发方式二选一：
 * <ul>
 *   <li>{@code state} —— 按投稿状态（submitted / approved / returned / revising）</li>
 *   <li>{@code rule} —— 按命中的规则编号（1~35）</li>
 * </ul>
 * 另有 {@code fallback} 标记：审核者「说不出依据」时用它（对应设计里的「要不直接打回算了」）。
 *
 * <p>{@code texture} 字段真的会被用到：服务端挑图时用 {@link #resolvedTexture()} 算出
 * 实际纹理路径，随结果包一起发给客户端（0.1.0 只发 id，这个字段等于摆设）。
 *
 * @param id       梗图 id（也是默认纹理名前缀）
 * @param texture  纹理路径；为空则按 {@code <ns>:textures/sticker/<id>.png} 推
 * @param state    触发状态（可空）
 * @param rule     触发规则号（可空）
 * @param fallback 是否兜底图
 * @param priority 多张匹配时取最大
 */
public record Sticker(ResourceLocation id, ResourceLocation texture,
                      String state, Integer rule, boolean fallback, int priority) {

    /** 解析后的实际纹理路径 */
    public ResourceLocation resolvedTexture() {
        return texture != null ? texture : textureFor(id);
    }

    /** 按 id 推默认纹理路径（数据包没写 texture 时用） */
    public static ResourceLocation textureFor(ResourceLocation id) {
        return new ResourceLocation(id.getNamespace(), "textures/sticker/" + id.getPath() + ".png");
    }
}
