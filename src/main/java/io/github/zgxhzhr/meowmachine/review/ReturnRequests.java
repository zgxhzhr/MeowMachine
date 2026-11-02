package io.github.zgxhzhr.meowmachine.review;

import net.minecraft.server.MinecraftServer;

import java.util.Optional;
import java.util.UUID;

/** 退回申请门面：任何人都能对已通过条目提交申请，审核员可直接执行退回。 */
public final class ReturnRequests {

    private static volatile ReturnRequestsSavedData store = null;

    private ReturnRequests() {
    }

    public static void attach(MinecraftServer server) {
        store = ReturnRequestsSavedData.get(server);
    }

    public static void detach() {
        store = null;
    }

    public static void request(UUID entryId, String requester, UUID requesterId, String reason) {
        if (store == null || entryId == null) {
            return;
        }
        store.put(new ReturnRequestsSavedData.Request(entryId, requester == null ? "" : requester,
                requesterId,
                reason == null ? "" : reason, System.currentTimeMillis()));
    }

    public static Optional<ReturnRequestsSavedData.Request> get(UUID entryId) {
        return store == null || entryId == null ? Optional.empty()
                : Optional.ofNullable(store.requests().get(entryId));
    }

    public static void clear(UUID entryId) {
        if (store != null) {
            store.remove(entryId);
        }
    }
}
