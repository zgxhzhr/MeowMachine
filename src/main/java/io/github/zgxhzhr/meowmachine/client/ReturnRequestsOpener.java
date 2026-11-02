package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.ReturnRequestListPacket;

/** 缓存最近一次「退回申请」列表，供审核界面显示入口按钮。 */
public final class ReturnRequestsOpener {

    private static volatile ReturnRequestListPacket latest = null;

    private ReturnRequestsOpener() {
    }

    public static void open(ReturnRequestListPacket msg) {
        latest = msg;
    }

    public static int pendingCount() {
        ReturnRequestListPacket p = latest;
        return p == null ? 0 : p.entries().size();
    }

    public static ReturnRequestListPacket latest() {
        return latest;
    }
}
