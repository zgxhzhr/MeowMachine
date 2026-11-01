package io.github.zgxhzhr.meowmachine.recipe;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 神秘合成的结果池：{@code <世界>/data/meow_machine_mystery.dat}。
 *
 * <p>**假随机**：一轮 = 按配置比例配好的 20 张牌（默认 15 张原版 + 5 张模组 = 模组 25%），
 * 模组物品均匀插在原版之间，发完一轮才重配。
 */
public final class MysteryPoolSavedData extends SavedData {

    public static final String DATA_NAME = "meow_machine_mystery";
    /** 一轮牌堆大小（20 张 → 百分比可按 5% 粒度配） */
    private static final int DECK = 20;
    /** 牌堆格式版本：1 = 旧版全量候选洗牌，3 = 现在的加权牌堆 */
    private static final int DECK_VERSION = 3;

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
     * 按比例取下一个结果。
     *
     * @param vanilla        原版候选（{@code minecraft:}）
     * @param modded         模组候选
     * @param moddedPercent  模组物品百分比（0~100）
     * @param moddedEnabled  false = 只抽原版
     */
    public String takeNext(List<String> vanilla, List<String> modded,
                           int moddedPercent, boolean moddedEnabled) {
        if (vanilla.isEmpty() && modded.isEmpty()) {
            return "";
        }
        int mWeight = moddedEnabled
                ? Math.max(0, Math.min(DECK, Math.round(moddedPercent / (100f / DECK))))
                : 0;
        int vWeight = DECK - mWeight;

        remaining.removeIf(id -> !vanilla.contains(id) && !modded.contains(id));
        if (remaining.size() > DECK) {
            remaining.clear();
        }
        if (remaining.isEmpty()) {
            remaining.addAll(buildDeck(vanilla, modded, vWeight, mWeight));
            if (remaining.isEmpty()) {
                return "";
            }
            if (remaining.size() > 1 && remaining.get(0).equals(last)) {
                Collections.swap(remaining, 0, 1);
            }
        }
        String next = remaining.remove(0);
        last = next;
        setDirty();
        return next;
    }

    /**
     * 配一轮牌：模组物品**均匀插在原版之间**。
     *
     * <p>25% 时大约每 4 个位置就有 1 个模组：最长连抽 4 次就能见到模组物品，
     * 也不会出现「连着 5 个模组」的突兀感。两类内部各自洗牌，具体抽到哪件仍然随机。
     */
    private List<String> buildDeck(List<String> vanilla, List<String> modded,
                                   int vWeight, int mWeight) {
        List<String> v = new ArrayList<>(vanilla);
        List<String> m = new ArrayList<>(modded);
        Collections.shuffle(v);
        Collections.shuffle(m);

        int mSlots = Math.min(mWeight, m.size());
        int vSlots = Math.min(vWeight, v.size());
        if (mSlots <= 0) {
            return new ArrayList<>(v.subList(0, Math.min(vSlots, DECK)));
        }
        if (vSlots <= 0) {
            return new ArrayList<>(m.subList(0, Math.min(mSlots, DECK)));
        }
        List<String> deck = new ArrayList<>(DECK);
        int mi = 0;
        int vi = 0;
        int total = mSlots + vSlots;
        for (int i = 0; i < total; i++) {
            int wantModded = (int) Math.round((i + 1) * (double) mSlots / total);
            if (mi < wantModded && mi < mSlots) {
                deck.add(m.get(mi++));
            } else if (vi < vSlots) {
                deck.add(v.get(vi++));
            } else if (mi < mSlots) {
                deck.add(m.get(mi++));
            }
        }
        return deck;
    }

    // ---------------- 序列化 ----------------

    public static MysteryPoolSavedData load(CompoundTag tag) {
        MysteryPoolSavedData d = new MysteryPoolSavedData();
        d.last = tag.getString("last");
        int version = tag.getInt("deck_version");
        ListTag list = tag.getList("remaining", Tag.TAG_STRING);
        if (version < DECK_VERSION || list.size() > DECK) {
            // 旧版存档存的是几百上千个候选的洗牌队列，不清掉要先抽完它们才轮到新牌堆
            MeowMachine.LOG.info("[喵呜机] 神秘合成牌堆格式升级，重置旧队列（{} 条）", list.size());
            return d;
        }
        for (int i = 0; i < list.size(); i++) {
            d.remaining.add(list.getString(i));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("deck_version", DECK_VERSION);
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
        List<String> vanilla = new ArrayList<>();
        List<String> modded = new ArrayList<>();
        var access = server.registryAccess();
        for (var recipe : server.getRecipeManager().getAllRecipesFor(
                net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
            String id = new ItemStackView(recipe, access).itemId();
            if (id == null || id.isBlank() || id.startsWith(MeowMachine.MOD_ID + ":")) {
                continue;
            }
            if (id.equals("minecraft:air") || id.equals("minecraft:barrier")
                    || id.equals("minecraft:light") || id.equals("minecraft:structure_void")) {
                continue;
            }
            if (id.startsWith("minecraft:")) {
                vanilla.add(id);
            } else {
                modded.add(id);
            }
        }
        if (vanilla.isEmpty() && modded.isEmpty()) {
            return "";
        }
        MeowMachine.LOG.info("[喵呜机] 神秘合成候选池：原版 {} 条 / 模组 {} 条",
                vanilla.size(), modded.size());
        MysteryPoolSavedData data = get(server);
        if (data == null) {
            return "";
        }
        String picked = data.takeNext(List.copyOf(vanilla), List.copyOf(modded),
                MysteryConfig.moddedPercent(), MysteryConfig.moddedEnabled());
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
                return id.equals("minecraft:air") ? "" : id;
            } catch (RuntimeException e) {
                return "";
            }
        }
    }
}
