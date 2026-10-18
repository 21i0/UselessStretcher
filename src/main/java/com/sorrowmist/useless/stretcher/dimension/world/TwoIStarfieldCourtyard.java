package com.sorrowmist.useless.stretcher.dimension.world;

import com.sorrowmist.useless.stretcher.content.entity.TwoIStarfieldData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Small deterministic white/blue/amethyst garden. Used only while a chunk is first generated. */
public final class TwoIStarfieldCourtyard {
    public static final int GATE_Z = 12;
    public static final int ENTRANCE_Z = 17;
    public static final int MAX_HEIGHT = 6;

    private TwoIStarfieldCourtyard() {
    }

    public static BlockPos entrance(BlockPos center) {
        return center.offset(0, 0, ENTRANCE_Z);
    }

    public static boolean inside(BlockPos center, Vec3 pos) {
        return Math.abs(pos.x - center.getX() - 0.5D) <= 10.0D
                && pos.z > center.getZ() - 10.5D && pos.z < center.getZ() + 10.5D
                && pos.y >= center.getY() - 0.5D && pos.y < center.getY() + 4.0D;
    }

    public static int relative(int worldCoordinate) {
        return Math.floorMod(worldCoordinate - 8 + TwoIStarfieldData.AREA_SPACING / 2,
                TwoIStarfieldData.AREA_SPACING) - TwoIStarfieldData.AREA_SPACING / 2;
    }

    /** Null means retain the normal glowing floor / air. The complete blueprint is under 2k blocks. */
    public static BlockState stateAt(int x, int y, int z) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        if (ax > 13 || z < -13 || z > 19 || y < -1 || y > MAX_HEIGHT) return null;
        if (y == -1) {
            if (ax <= 12 && az <= 12) {
                boolean bed = ax >= 5 && ax <= 9 && az >= 4 && az <= 7;
                if (bed) return Blocks.MOSS_BLOCK.defaultBlockState();
                if (ax == 12 || az == 12 || ax == 4 && az <= 10 || az == 3 && ax <= 10)
                    return Blocks.PURPUR_BLOCK.defaultBlockState();
                if (ax + az <= 3 || ax == 1 && az <= 10 || az == 1 && ax <= 10)
                    return Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
                if (ax == 11 && az == 11 || ax == 4 && az == 3) return Blocks.SEA_LANTERN.defaultBlockState();
                return Blocks.SMOOTH_QUARTZ.defaultBlockState();
            }
            if (ax <= 2 && z > 12) return (ax == 2 ? Blocks.PURPUR_BLOCK : Blocks.SMOOTH_QUARTZ).defaultBlockState();
            return null;
        }
        // Five-block-wide open arch; no roof over the garden hides the sky animation.
        if (z == GATE_Z) {
            if (ax == 3 && y <= 4) return (y == 4 ? Blocks.SEA_LANTERN : Blocks.QUARTZ_PILLAR).defaultBlockState();
            if (ax <= 3 && y == 5) return Blocks.SMOOTH_QUARTZ.defaultBlockState();
            if (ax <= 1 && y == 6) return (x == 0 ? Blocks.AMETHYST_BLOCK : Blocks.PURPUR_SLAB).defaultBlockState();
        }
        // Corner lantern pillars, low garden outline, and two small sitting benches.
        if (ax == 10 && az == 10 && y <= 3)
            return (y == 3 ? Blocks.SEA_LANTERN : Blocks.QUARTZ_PILLAR).defaultBlockState();
        if (ax == 10 && az == 10 && y == 4) return Blocks.AMETHYST_CLUSTER.defaultBlockState();
        if (y == 0 && (ax == 12 && az <= 12 || az == 12 && ax <= 12 && !(z > 0 && ax <= 2)))
            return Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState();
        if (z == -9 && ax <= 2 && y == 0) return (ax == 2 ? Blocks.QUARTZ_BLOCK : Blocks.SMOOTH_QUARTZ_SLAB).defaultBlockState();
        if (ax == 9 && az <= 1 && y == 0) return Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState();
        if (y == 0 && ax >= 5 && ax <= 9 && az >= 4 && az <= 7) {
            if ((ax + az) % 3 == 0) return Blocks.ALLIUM.defaultBlockState();
            if ((ax + az) % 3 == 1) return Blocks.CORNFLOWER.defaultBlockState();
            return Blocks.WHITE_TULIP.defaultBlockState();
        }
        // A tiny starlight pedestal, with a walking ring around it rather than a deep pool.
        if (ax <= 1 && az <= 1 && y == 0) return (x == 0 && z == 0 ? Blocks.SEA_LANTERN : Blocks.PURPUR_SLAB).defaultBlockState();
        if (x == 0 && z == 0 && y == 1) return Blocks.AMETHYST_CLUSTER.defaultBlockState();
        return null;
    }
}
