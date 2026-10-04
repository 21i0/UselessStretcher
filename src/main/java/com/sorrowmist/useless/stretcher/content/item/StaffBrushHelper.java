package com.sorrowmist.useless.stretcher.content.item;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;

/** Generic brush bridge: works with vanilla and compatible archaeology block entities. */
public final class StaffBrushHelper {
    private StaffBrushHelper() { }

    public static InteractionResult tryBrush(ItemStack staff, UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockEntity target = level.getBlockEntity(context.getClickedPos());
        if (player == null || target == null || findBrush(target) == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel)) return InteractionResult.PASS;
        Method method = findBrush(target);
        Direction face = context.getClickedFace();
        long now = level.getGameTime();
        StaffBrushContext.run(() -> {
            try {
                // Vanilla requires ten brush advances. Passing future virtual times removes the
                // cooldown between them while the mixin resets the completed loot state.
                for (int i = 0; i < 64; i++) {
                    boolean complete = (boolean) method.invoke(target, now + (long) i * 10L, player, face);
                    if (complete) break;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // A third-party archaeology implementation is optional; normal brushing remains.
            }
        });
        return InteractionResult.SUCCESS;
    }

    private static Method findBrush(BlockEntity target) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod("brush", long.class, Player.class, Direction.class);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }
}
