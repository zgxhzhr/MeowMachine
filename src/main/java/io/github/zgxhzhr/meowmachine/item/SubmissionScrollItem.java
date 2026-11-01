package io.github.zgxhzhr.meowmachine.item;

import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.OpenEditorPacket;
import io.github.zgxhzhr.meowmachine.review.DraftStore;
import io.github.zgxhzhr.meowmachine.review.Submission;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;

/**
 * 投稿卷轴。
 *
 * <p>右键时**由服务端**把当前草稿推回客户端，客户端收到再打开界面。
 * 这种「服务端驱动」的顺序比「客户端先开界面再拉数据」稳：不会出现界面已开、
 * 草稿还没到导致输入框空白的闪烁。
 *
 * <p>**已通过的稿子**（终局、且不允许再编辑）会自动换成一份全新草稿：
 * 否则玩家会永远卡在同一篇上，再也投不了第二篇（0.2.x 的实际反馈）。
 * 想主动开新稿也可以用界面里的「新建投稿」按钮。
 */
public class SubmissionScrollItem extends Item {

    public SubmissionScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            Submission draft = DraftStore.load(sp);
            // 草稿就继续编辑（哪怕还没写内容）；已提交 / 已打回 / 已通过一律开新稿，
            // 那些内容去「我的投稿」里查看或点编辑
            boolean isDraft = draft.state()
                    == io.github.zgxhzhr.meowmachine.review.SubmissionState.DRAFT;
            if (!isDraft) {
                draft = DraftStore.startNew(sp);
            }
            OpenEditorPacket packet = new OpenEditorPacket(
                    draft.title(), draft.body(), draft.state().name().toLowerCase());
            Network.CHANNEL.sendTo(packet, sp.connection.connection,
                    NetworkDirection.PLAY_TO_CLIENT);
        }

        return InteractionResultHolder.success(stack);
    }
}
