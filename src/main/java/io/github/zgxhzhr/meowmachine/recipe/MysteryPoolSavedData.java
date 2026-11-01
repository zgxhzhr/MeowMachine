package io.github.zgxhzhr.meowmachine.recipe;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 神秘合成的结果池：{@code <世界>/data/meow_machine_mystery.dat}。
 *
 * <p>规则：候选 = 当前世界里**有合成配方的物品**；每合一次从洗好的队列里取一个，
 * 保证每次结果不同；队列耗尽后重新洗牌（仍然避免连续两次相同）。
 */
public final class MysteryPoolSavedData extends SavedData {

    public static final String DATA_NAME = "meow_machine_mystery";

    private final List<String> remaining = new ArrayList<>();
    private String last = "";

    public static MysteryPoolSavedData get(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.overworld().getDataStorage()
                .computeIfAbsent(MysteryPoolSavedData::load, MysteryPoolSavedData::new, DATA_NAME);
    }

    /**
     * 取下一个结果。
     *
     * @param candidates 候选物品 id（调用方已过滤）
     */
    public String takeNext(List<String> candidates) {
        if (candidates.isEmpty()) {
            return "";
        }
        remaining.removeIf(id -> !candidates.contains(id));
        if (remaining.isEmpty()) {
            remaining.addAll(candidates);
            Collections.shuffle(remaining);
            // 上一张还在队首就换一个，避免连续两次相同
            if (remaining.size() > 1 && remaining.get(0).equals(last)) {
                Collections.swap(remaining, 0, 1);
            }
        }
        String next = remaining.remove(0);
        last = next;
        setDirty();
        return next;
    }

    // ---------------- 序列化 ----------------

    public static MysteryPoolSavedData load(CompoundTag tag) {
        MysteryPoolSavedData d = new MysteryPoolSavedData();
        d.last = tag.getString("last");
        ListTag list = tag.getList("remaining", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            d.remaining.add(list.getString(i));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putString("last", last);
        ListTag list = new ListTag();
        for (String id : remaining) {
            list.add(net.minecraft.nbt.StringTag.valueOf(id));
        }
        tag.put("remaining", list);
        return tag;
    }

    /** 候选池构建 + 取值的门面 */
    public static String roll(MinecraftServer server) {
        if (server == null) {
            return "";
        }
        Set<String> pool = new LinkedHashSet<>();
        var access = server.registryAccess();
        for (var recipe : server.getRecipeManager().getAllRecipesFor(
                net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
            ItemStackView view = new ItemStackView(recipe, access);
            String id = view.itemId();
            if (id == null || id.isBlank()) {
                continue;
            }
            // 排除本模组物品（避免产出梗图/终端之类，也避免和神秘配方本身互相嵌套）
            if (id.startsWith(MeowMachine.MOD_ID + ":")) {
                continue;
            }
            // 空气、屏障等「有配方但拿不到正常物品」的结果要排除
            if (id.equals("minecraft:air") || id.equals("minecraft:barrier")
                    || id.equals("minecraft:light") || id.equals("minecraft:structure_void")) {
                continue;
            }
            pool.add(id);
        }
        if (pool.isEmpty()) {
            return "";
        }
        MeowMachine.LOG.info("[喵呜机] 神秘合成候选池 {} 条", pool.size());
        MysteryPoolSavedData data = get(server);
        if (data == null) {
            return "";
        }
        String picked = data.takeNext(new ArrayList<>(pool));
        MeowMachine.LOG.debug("[喵呜机] 神秘合成结果 {}", picked);
        return picked;
    }

    /** 小包装：避免在循环里写泛型 */
    private record ItemStackView(net.minecraft.world.item.crafting.CraftingRecipe recipe,
                                 net.minecraft.core.RegistryAccess access) {
        String itemId() {
            try {
                var stack = recipe.getResultItem(access);
                if (stack == null || stack.isEmpty()) {
                    return "";
                }
                if (stack.getItem() == net.minecraft.world.item.Items.AIR) {
                    return "";
                }
                var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (key == null) {
                    return "";
                }
                String id = key.toString();
                // 再挡一次空气/占位
                if (id.equals("minecraft:air")) {
                    return "";
                }
                return id;
            } catch (RuntimeException e) {
                return "";
            }
        }
    }
}
