package io.github.zgxhzhr.meowmachine.review;

import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;

/** 吐槽门面（附在投稿结论上，最多 5 条、每条 120 字）。 */
public final class Rants {

    private static final int MAX_LINES = 5;
    private static final int MAX_LEN = 120;
    private static volatile RantsSavedData store = null;

    private Rants() {
    }

    public static void attach(MinecraftServer server) {
        store = RantsSavedData.get(server);
    }

    public static void detach() {
        store = null;
    }

    public static List<String> of(UUID id) {
        if (store == null || id == null) {
            return List.of();
        }
        List<String> list = store.rants().get(id);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** @return false 表示超出条数上限 */
    public static boolean add(UUID id, String text) {
        if (store == null || id == null || text == null || text.isBlank()) {
            return false;
        }
        List<String> now = store.rants().getOrDefault(id, List.of());
        if (now.size() >= MAX_LINES) {
            return false;
        }
        String trimmed = text.strip();
        if (trimmed.length() > MAX_LEN) {
            trimmed = trimmed.substring(0, MAX_LEN);
        }
        store.add(id, trimmed);
        return true;
    }
}
