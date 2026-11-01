package io.github.zgxhzhr.meowmachine.sticker;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.advancement.MeowAdvancements;
import io.github.zgxhzhr.meowmachine.util.PlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 梗图食用：每张图都有自己的饱食度/饱和度与效果，定义全部来自数据包 {@code food} 字段。
 *
 * <p>设计约束：全局 60 秒冷却（防止连吃）、效果只刷新不叠加、时长 ≤ 90 秒（AI 专属可到 120 秒）。
 */
public final class StickerFood {

    private static final String KEY_EATEN = "meow_machine_eaten_stickers";
    private static final List<String> AI_ONLY = List.of("helpless", "kurushi", "laowu", "meowmeow");

    private StickerFood() {
    }

    /**
     * 尝试食用。
     *
     * @return true 表示吃成功（调用方负责扣物品）
     */
    /** 只负责效果/特殊行为/成就：饱食度与饱和度交给原版 FoodProperties */
    public static void applyExtras(ServerPlayer player, String stickerId) {
        Sticker st = StickerRegistry.byId(stickerId).orElse(null);
        if (st == null) {
            return;
        }
        for (Sticker.FoodEffect fe : st.foodEffects()) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(fe.effect());
            if (effect != null) {
                player.addEffect(new MobEffectInstance(effect, fe.seconds() * 20, fe.amplifier()));
            }
        }
        applySpecial(player, st);
        CompoundTag data = PlayerData.persisted(player);
        List<String> eaten = readEaten(data);
        if (!eaten.contains(st.id().getPath())) {
            eaten.add(st.id().getPath());
            writeEaten(data, eaten);
            if (eaten.size() >= 8) {
                MeowAdvancements.award(player, "sticker_taste");
            }
            if (eaten.containsAll(AI_ONLY)) {
                MeowAdvancements.award(player, "meow_marked");
            }
        }
    }

    /** 旧入口：保留给可能的外部调用（饱食度由原版处理，这里只补效果） */
    public static boolean eat(ServerPlayer player, String stickerId) {
        applyExtras(player, stickerId);
        return StickerRegistry.byId(stickerId).isPresent();
    }

    private static void applySpecial(ServerPlayer player, Sticker st) {
        String special = st.special() == null ? "" : st.special();
        switch (special) {
            case "clear_negative" -> {
                List<MobEffect> bad = new ArrayList<>();
                player.getActiveEffects().forEach(e -> {
                    if (e.getEffect().isBeneficial()) {
                        return;
                    }
                    bad.add(e.getEffect());
                });
                bad.forEach(player::removeEffect);
            }
            case "xp10" -> player.giveExperiencePoints(10);
            case "slow_mobs" -> {
                AABB box = player.getBoundingBox().inflate(8);
                for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
                    if (e != player) {
                        e.addEffect(new MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 100, 0));
                    }
                }
            }
            case "glow_mobs" -> {
                AABB box = player.getBoundingBox().inflate(16);
                for (Mob e : player.level().getEntitiesOfClass(Mob.class, box)) {
                    e.addEffect(new MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.GLOWING, 200, 0));
                }
            }
            case "random_line" -> {
                int idx = java.util.concurrent.ThreadLocalRandom.current().nextInt(3) + 1;
                player.displayClientMessage(Component.translatable(
                        "chat.meow_machine.sticker_line." + idx), false);
            }
            default -> {
            }
        }
    }

    private static List<String> readEaten(CompoundTag data) {
        List<String> out = new ArrayList<>();
        ListTag list = data.getList(KEY_EATEN, net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return out;
    }

    private static void writeEaten(CompoundTag data, List<String> eaten) {
        ListTag list = new ListTag();
        for (String id : eaten) {
            list.add(StringTag.valueOf(id));
        }
        data.put(KEY_EATEN, list);
    }
}
