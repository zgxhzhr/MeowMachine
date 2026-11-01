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
 * <p>**假随机**：一轮 = 按配置权重配好的 10 张牌（默认 9 张原版 + 1 张模组），
 * 洗好后逐张发；发完才重配。抽完一轮你能拿到配置里承诺的比例，
 * 不会出现「连抽十几次全是模组物品」这种真随机的尾巴。
 */
public final class MysteryPoolSavedData extends SavedData {

    public static final String DATA_NAME = "meow_machine_mystery";
    /** 一轮牌堆大小 */
    private static final int DECK = 10;

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
     * 按权重取下一个结果。
     *
     * @param vanilla        原版候选（{@code minecraft:}）
     * @param modded         模组候选
     * @param vanillaWeight  一轮 10 张里放几张原版（0~10）
     * @param moddedEnabled  false = 只抽原版
     */
    public String takeNext(List<String> vanilla, List<String> modded,
                           int vanillaWeight, boolean moddedEnabled) {
        int vWeight = moddedEnabled ? Math.max(0, Math.min(DECK, vanillaWeight)) : DECK;
        int mWeight = DECK - vWeight;
        if (vanilla.isEmpty() && modded.isEmpty()) {
            return "";
        }
        // 牌堆里的东西如果已经不在这个世界了（删了模组），只清理不合法的
        remaining.removeIf(id -> !vanilla.contains(id) && !modded.contains(id));
        if (remaining.size() > DECK) {
            remaining.clear();
        }
        if (remaining.isEmpty()) {
            remaining.addAll(buildDeck(vanilla, modded, vWeight, mWeight));
            if (remaining.size() > DECK) {
            remaining.clear();
        }
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
     * 配一轮牌：**先发满原版，最后才发模组**。
     *
     * <p>用户要的规则是「原版 90% / 模组 10%，但原版已经出了 9 个，下一个必定是模组物品」——
     * 所以不能把 10 张牌完全打乱（那样模组可能落在队首，随后连着 9 次都没有），
     * 而是固定「原版块 + 模组块」：最多 9 抽就一定会见到模组物品，
     * 一个周期内模组出现次数也正好等于配置权重。
     */
    private List<String> buildDeck(List<String> vanilla, List<String> modded,
                                   int vWeight, int mWeight) {
        List<String> deck = new ArrayList<>(DECK);
        List<String> v = new ArrayList<>(vanilla);
        List<String> m = new ArrayList<>(modded);
        Collections.shuffle(v);
        Collections.shuffle(m);
        if (v.isEmpty()) {
            deck.addAll(m.subList(0, Math.min(m.size(), DECK)));
        } else if (m.isEmpty() || mWeight <= 0) {
            deck.addAll(v.subList(0, Math.min(v.size(), DECK)));
        } else {
            for (int i = 0; i < vWeight && i < v.size(); i++) {
                deck.add(v.get(i));
            }
            for (int i = 0; i < mWeight && i < m.size(); i++) {
                deck.add(m.get(i));
            }
        }
        // 注意：这里**不再整体打乱**——原版块在前、模组块在后是刻意的（见方法注释）
        return deck;
    }

    // ---------------- 序列化 ----------------

    /** 牌堆格式版本：1 = 旧版「全量候选洗牌」，2 = 现在的「一轮 10 张加权牌堆」 */
    private static final int DECK_VERSION = 2;

    public static MysteryPoolSavedData load(CompoundTag tag) {
        MysteryPoolSavedData d = new MysteryPoolSavedData();
        d.last = tag.getString("last");
        int version = tag.getInt("deck_version");
        ListTag list = tag.getList("remaining", Tag.TAG_STRING);
        if (version < DECK_VERSION || list.size() > DECK) {
            // 旧版存档里是几百上千个候选的洗牌队列，不清掉的话要先抽完它们才轮到新牌堆
            // （实测：22 次全是原版，模组物品一直不出现）
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
                MysteryConfig.vanillaWeight(), MysteryConfig.moddedEnabled());
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
