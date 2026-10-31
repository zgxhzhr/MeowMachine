package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.MeowMachine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 与 IMBlocker 的软兼容层。
 *
 * <p>IMBlocker（输入法冲突修复，常见于中文玩家）只在它认识的文本框里打开输入法，
 * 判断依据是「屏幕白名单 + 焦点组件」。我们的界面是自定义 Screen，正文还是自绘输入区，
 * 它不认识 → 整个界面**禁用了输入法**，表现就是「中文打不进去」。
 *
 * <p>IMBlocker 5.x 暴露了公开的 {@code FocusContainer.MINECRAFT.setPreferredState(boolean)}，
 * 这就是它自己对白名单屏幕做的同一件事。这里用反射调用（**不引入编译依赖**）：
 * 装了 IMBlocker 就顺带把输入法打开，没装就是空操作。
 */
public final class ImeCompat {

    private static final String FOCUS_CONTAINER = "io.github.reserveword.imblocker.common.gui.FocusContainer";

    private static boolean resolved = false;
    private static Object container;
    private static Method setPreferredState;
    private static boolean logged = false;

    private ImeCompat() {
    }

    /** 请求打开/关闭输入法（IMBlocker 不存在时静默忽略） */
    public static void wantIme(boolean wanted) {
        resolve();
        if (container == null || setPreferredState == null) {
            return;
        }
        try {
            setPreferredState.invoke(container, wanted);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // IMBlocker 版本不兼容时最多是「输入法照旧不弹」，不该影响打开界面
        }
    }

    /** 这个界面有文本输入，打开时调用 */
    public static void onTextScreenOpened() {
        wantIme(true);
    }

    /** 界面关闭/被替换时调用，把状态还回去（否则输入法会一直开着） */
    public static void onTextScreenClosed() {
        wantIme(false);
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> c = Class.forName(FOCUS_CONTAINER);
            Field field = c.getField("MINECRAFT");
            container = field.get(null);
            setPreferredState = c.getMethod("setPreferredState", boolean.class);
            if (!logged) {
                logged = true;
                MeowMachine.LOG.info("[喵呜机] 检测到 IMBlocker，已接入输入法开关（中文输入可用）");
            }
        } catch (Throwable t) {
            // 没装 IMBlocker 或名称变了：正常情况，安静跳过
            container = null;
            setPreferredState = null;
        }
    }
}
