package com.sorrowmist.useless.stretcher.client.render;

import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import java.util.WeakHashMap;

/** Local visual transition, shared by every rendered form; no extra server ticker. */
public final class TwoIDollWeather {
    public record Pose(int flags, float open, float sneeze) {
        public static final Pose CLEAR = new Pose(0, 0, -1);
    }
    private static final WeakHashMap<Object, State> STATES = new WeakHashMap<>();
    private static final class State {
        Level level;
        long last = Long.MIN_VALUE;
        float open;
        int flags;
    }
    private TwoIDollWeather() { }

    public static Pose sample(Object key, Level level, BlockPos pos, float partial, int salt) {
        if (level == null) return Pose.CLEAR;
        State state = STATES.computeIfAbsent(key, ignored -> new State());
        long now = level.getGameTime();
        if (state.level != level || now < state.last) {
            state.level = level; state.last = now - 1; state.open = 0;
        }
        if (now != state.last) {
            state.flags = TwoIDollEnvironment.conditions(level, pos);
            float step = Math.min(20, now - state.last) / 20.0F;
            state.open = net.minecraft.util.Mth.clamp(state.open
                    + ((state.flags & TwoIDollEnvironment.RAIN) != 0 ? step : -step), 0, 1);
            state.last = now;
        }
        return new Pose(state.flags, state.open, (state.flags & TwoIDollEnvironment.COLD) != 0
                ? TwoIDollEnvironment.sneezeProgress(now + partial, salt) : -1);
    }
}
