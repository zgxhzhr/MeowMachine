package io.github.zgxhzhr.meowmachine.sticker;

import net.minecraft.resources.ResourceLocation;

/**
 * 一张梗图的定义。**完全由数据包提供**，Java 里不出现任何具体表情 id。
 *
 * <p>触发方式二选一：
 * <ul>
 *   <li>{@code state} —— 按投稿状态（draft / submitted / approved / returned）</li>
 *   <li>{@code rule} —— 按命中的规则编号（1~35）</li>
 * </ul>
 * 另有 {@code fallback} 标记：审核者「说不出依据」时用它（对应设计里的「要不直接打回算了」）。
 *
 * @param id       梗图 id（也是纹理名前缀）
 * @param texture  纹理路径；为空则按 {@code meow_machine:textures/sticker/<id>.png} 推
 * @param state    触发状态（可空）
 * @param rule     触发规则号（可空）
 * @param fallback 是否兜底图
 * @param priority 多张匹配时取最大
 */
public record Sticker(ResourceLocation id, ResourceLocation texture,
                      String state, Integer rule, boolean fallback, int priority) {

    /** 解析后的实际纹理路径 */
    public ResourceLocation resolvedTexture() {
        if (texture != null) {
            return texture;
        }
        return new ResourceLocation(id.getNamespace(), "textures/sticker/" + id.getPath() + ".png");
    }
}
