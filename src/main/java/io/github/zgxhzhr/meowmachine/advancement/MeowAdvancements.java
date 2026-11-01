package io.github.zgxhzhr.meowmachine.advancement;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;

/**
 * 模组成就的发放工具。
 *
 * <p>成就都挂在 {@code meow_machine:root} 下（拿到投稿卷轴自动点亮），
 * 其余用 {@code minecraft:impossible} 触发器 + 这里按业务流程手动授予。
 *
 * <p>用 {@code award} 而不是自己写进度：Minecraft 会处理好「已获得就不再弹」。
 */
public final class MeowAdvancements {

    /** 第一次提交投稿 */
    public static final String FIRST_SUBMIT = "first_submit";
    /** 第一次过审 */
    public static final String FIRST_APPROVED = "first_approved";
    /** 第一次被打回 */
    public static final String FIRST_RETURNED = "first_returned";
    /** 被打回后改到过审 */
    public static final String REVISED_APPROVED = "revised_approved";
    /** 第一次终裁 */
    public static final String FIRST_DECISION = "first_decision";
    /** 第一次写审核意见 */
    public static final String FIRST_NOTE = "first_note";
    /** 被任命为审核员或助理 */
    public static final String APPOINTED = "appointed";

    private MeowAdvancements() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(MeowMachine.MOD_ID, path);
    }

    /**
     * 授予成就。
     *
     * <p>数据包没加载（例如客户端）或成就被别的数据包去掉时静默跳过，不抛异常。
     */
    public static void award(ServerPlayer player, String path) {
        if (player == null || player.getServer() == null) {
            return;
        }
        try {
            ServerAdvancementManager manager = player.getServer().getAdvancements();
            Advancement advancement = manager.getAdvancement(id(path));
            if (advancement != null) {
                player.getAdvancements().award(advancement, "code");
            }
        } catch (RuntimeException e) {
            MeowMachine.LOG.warn("[喵呜机] 授予成就失败 {}: {}", path, e.toString());
        }
    }
}
