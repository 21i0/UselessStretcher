package com.sorrowmist.useless.stretcher.init;

import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * 固定本模组物品的默认数据组件。
 *
 * <p>① 手杖自身的两个组件（{@link StretcherComponents#WONDROUS_STAFF_SPEED} /
 * {@link StretcherComponents#WONDROUS_STAFF_PERMANENT}）必须**默认就存在**。
 * 它们是手杖的身份标记：{@code BeefToolVariantsMixin} 靠
 * {@code source.has(WONDROUS_STAFF_SPEED)} 认出"这是我们自己的手杖"。
 * 而这两个组件原本只在玩家用 Shift+滚轮切档位时才被写入，于是"从没切过档位的手杖"
 * 一旦在 G 轮盘里切一次工具模式再切回来，就会被上游 {@code withWrenchTag} 重建成太初杖
 * ——手杖就此消失，表现为"新加速打不开、老的永久加速也取消不掉"。
 * 默认写入后任何一栈手杖都带标记，这个洞就彻底堵上了（数值与原来的
 * {@code getOrDefault(..., DEFAULT_GEAR / false)} 完全一致，行为不变）。
 *
 * <p>② 无用之物 2.3.6 把「无敌 / 玩家保护」模块的缺省判定从
 * {@code getOrDefault(BeefInvulnerabilityEnabledComponent, false)} 改成了
 * {@code enabled != null ? enabled : stack.getItem() instanceof EndlessBeafItem}。
 * 荒辰移晷之杖继承自 {@code EndlessBeafItem}，于是会**仅凭这个兜底**就默默继承该模块
 * （背包里放着就获得无敌、并在首次生效时清掉所有负面效果），物品上却看不到这个组件。
 * 我们的手杖本来就以「完整继承造化垂青之杖」为目标，所以这里保持 {@code true}，
 * 但**显式写进默认组件**：行为固定下来，将来上游再改缺省判定也不会悄悄翻转；
 * G 轮盘里也会正常显示为「已开启」，玩家仍可自行关闭。
 *
 * <p>注意：只能在 {@link ModifyDefaultComponentsEvent} 里改，别处调用
 * {@code Item#modifyDefaultComponentsFrom} 会抛
 * {@code IllegalStateException: Default components cannot be modified now!}。
 */
public final class ModItemDefaults {
    private ModItemDefaults() {
    }

    public static void modifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        event.modify(ModItems.WONDROUS_STAFF.get(), builder -> builder
                .set(UComponents.BeefInvulnerabilityEnabledComponent.get(), true)
                .set(StretcherComponents.WONDROUS_STAFF_SPEED.get(), WondrousStaffAcceleration.DEFAULT_GEAR)
                .set(StretcherComponents.WONDROUS_STAFF_PERMANENT.get(), false));
    }
}
