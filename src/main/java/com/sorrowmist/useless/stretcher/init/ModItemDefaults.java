package com.sorrowmist.useless.stretcher.init;

import com.sorrowmist.useless.core.component.UComponents;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * 固定本模组物品的默认数据组件。
 *
 * <p>无用之物 2.3.6 把「无敌 / 玩家保护」模块的缺省判定从
 * {@code getOrDefault(BeefInvulnerabilityEnabledComponent, false)} 改成了
 * {@code enabled != null ? enabled : stack.getItem() instanceof EndlessBeafItem}。
 * 荒辰移晷之杖继承自 {@code EndlessBeafItem}，于是会**仅凭这个兜底**就默默继承该模块
 * （背包里放着就获得无敌、并在首次生效时清掉所有负面效果），物品上却看不到这个组件。
 *
 * <p>我们的手杖本来就以「完整继承造化垂青之杖」为目标，所以这里保持 {@code true}，
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
        event.modify(ModItems.WONDROUS_STAFF.get(), builder ->
                builder.set(UComponents.BeefInvulnerabilityEnabledComponent.get(), true));
    }
}
