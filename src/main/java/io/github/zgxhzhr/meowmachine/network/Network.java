package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络通道。
 *
 * <p>这里**不用 MenuType**：投稿界面是纯客户端 Screen，内容通过几个小包来回传。
 * 好处是不必维护容器同步（EditBox 的内容本来就只该由作者自己看到），
 * 坏处是文本必须自己做长度校验——见 {@link #MAX_BODY_LEN}。
 *
 * <p>协议号随包结构变化递增：0.1.0 → "1"，本次加入草稿暂存与提示包后为 "2"。
 * 版本不一致的客户端会被 Forge 直接挡在连接阶段，不会跑到解码才炸。
 */
public final class Network {

    /** 协议号：包结构一改就 +1（0.1.0=1，0.2.0=2，0.3.0=3，0.4.0=4，0.5.0=5，0.7.0=6 加入「我的投稿」） */
    private static final String PROTOCOL = "16";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MeowMachine.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    /** 标题长度上限（字符） */
    public static final int MAX_TITLE_LEN = 64;
    /** 正文长度上限（字符）。真正落库前服务端还会再夹一次，客户端限制只是体验 */
    public static final int MAX_BODY_LEN = 16384;

    private Network() {
    }

    public static void register() {
        int id = 0;

        // 服务端 → 客户端：把草稿送回，客户端据此打开编辑界面
        CHANNEL.messageBuilder(OpenEditorPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenEditorPacket::encode)
                .decoder(OpenEditorPacket::decode)
                .consumerMainThread(OpenEditorPacket::handle)
                .add();

        // 客户端 → 服务端：提交（标题 + 正文）
        CHANNEL.messageBuilder(SubmitPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SubmitPacket::encode)
                .decoder(SubmitPacket::decode)
                .consumerMainThread(SubmitPacket::handle)
                .add();

        // 客户端 → 服务端：关界面时暂存草稿（不提交）
        CHANNEL.messageBuilder(SaveDraftPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SaveDraftPacket::encode)
                .decoder(SaveDraftPacket::decode)
                .consumerMainThread(SaveDraftPacket::handle)
                .add();

        // 客户端 → 服务端：丢当前草稿、开一份全新投稿
        CHANNEL.messageBuilder(NewSubmissionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(NewSubmissionPacket::encode)
                .decoder(NewSubmissionPacket::decode)
                .consumerMainThread(NewSubmissionPacket::handle)
                .add();

        // 提交端：从「我的投稿」把某篇拉回来继续编辑（队列里的会先撤回）
        CHANNEL.messageBuilder(EditMySubmissionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(EditMySubmissionPacket::encode)
                .decoder(EditMySubmissionPacket::decode)
                .consumerMainThread(EditMySubmissionPacket::handle)
                .add();

        // 提交端：查「我的投稿」（当前草稿 + 队列里自己的待审）
        CHANNEL.messageBuilder(MySubmissionsRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MySubmissionsRequestPacket::encode)
                .decoder(MySubmissionsRequestPacket::decode)
                .consumerMainThread(MySubmissionsRequestPacket::handle)
                .add();
        CHANNEL.messageBuilder(MySubmissionsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MySubmissionsPacket::encode)
                .decoder(MySubmissionsPacket::decode)
                .consumerMainThread(MySubmissionsPacket::handle)
                .add();

        // 服务端 → 客户端：审核结果 + 用哪张梗图 + 实际纹理路径
        CHANNEL.messageBuilder(ReviewResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReviewResultPacket::encode)
                .decoder(ReviewResultPacket::decode)
                .consumerMainThread(ReviewResultPacket::handle)
                .add();

        // 客户端 → 服务端：给待审稿留一条意见（助理/审核员都能写）
        CHANNEL.messageBuilder(AnnotatePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AnnotatePacket::encode)
                .decoder(AnnotatePacket::decode)
                .consumerMainThread(AnnotatePacket::handle)
                .add();

        // 客户端 → 服务端：请求待审列表（审核界面刷新/从详情页返回）
        CHANNEL.messageBuilder(ReviewListRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ReviewListRequestPacket::encode)
                .decoder(ReviewListRequestPacket::decode)
                .consumerMainThread(ReviewListRequestPacket::handle)
                .add();

        // 服务端 → 客户端：待审列表（打开审核界面）
        CHANNEL.messageBuilder(ReviewListPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReviewListPacket::encode)
                .decoder(ReviewListPacket::decode)
                .consumerMainThread(ReviewListPacket::handle)
                .add();

        // 客户端 → 服务端：界面上点了裁决按钮
        CHANNEL.messageBuilder(DecidePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DecidePacket::encode)
                .decoder(DecidePacket::decode)
                .consumerMainThread(DecidePacket::handle)
                .add();

        // 管理后台
        CHANNEL.messageBuilder(AdminStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AdminStatePacket::encode).decoder(AdminStatePacket::decode)
                .consumerMainThread(AdminStatePacket::handle).add();
        CHANNEL.messageBuilder(AdminActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AdminActionPacket::encode).decoder(AdminActionPacket::decode)
                .consumerMainThread(AdminActionPacket::handle).add();

        // 服务端 → 客户端：一句聊天栏提示（操作失败、状态不对等）
        CHANNEL.messageBuilder(NoticePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NoticePacket::encode).decoder(NoticePacket::decode)
                .consumerMainThread(NoticePacket::handle).add();

        // 审核员管理：名册状态 + 后台操作
        CHANNEL.messageBuilder(RosterStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RosterStatePacket::encode).decoder(RosterStatePacket::decode)
                .consumerMainThread(RosterStatePacket::handle).add();
        CHANNEL.messageBuilder(RosterActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RosterActionPacket::encode).decoder(RosterActionPacket::decode)
                .consumerMainThread(RosterActionPacket::handle).add();

        MeowMachine.LOG.info("[喵呜机] 网络通道注册完成（{} 个包，协议 {}）", id, PROTOCOL);
    }
}
