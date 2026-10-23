package com.sorrowmist.useless.stretcher.content.capsule;

import appeng.worldgen.meteorite.MeteoriteStructurePiece;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Extra absolute coordinates and physical bounds which vanilla Piece.move does not carry. */
final class CapsulePieceGeometry {
    static final int VERSION = 1;
    private CapsulePieceGeometry() { }

    static StructurePiece move(StructurePiece piece, int dx, int dy, int dz,
                               StructurePieceSerializationContext context) {
        if (piece instanceof MeteoriteStructurePiece meteorite) {
            return meteorite(meteorite, meteorite.getSettings().getPos().offset(dx, dy, dz), dx, dz, context);
        }
        piece.move(dx, dy, dz);
        return piece;
    }

    static StructurePiece migrate(StructurePiece piece, CapsuleInstances.Instance room,
                                  StructurePieceSerializationContext context) {
        if (!(piece instanceof MeteoriteStructurePiece meteorite)) return piece;
        BlockPos center = meteorite.getSettings().getPos();
        if (room.sourceCoordinates) center = center.offset(-room.sourceOffsetX, -room.sourceOffsetY, -room.sourceOffsetZ);
        return meteorite(meteorite, center, 0, 0, context);
    }

    private static MeteoriteStructurePiece meteorite(MeteoriteStructurePiece piece, BlockPos center,
                                                     int dx, int dz, StructurePieceSerializationContext context) {
        var tag = piece.createTag(context);
        var box = piece.getBoundingBox();
        tag.putLong("c", center.asLong());
        // AE2 19.x advertises a single Y layer for chunk intersection, while its native
        // placer writes the body at centerY - 8 .. centerY + 7 (and quartz buds above).
        // Use that actual envelope for support, trimming and the saved capsule plan.
        tag.putIntArray("BB", new int[]{box.minX() + dx, center.getY() - 8, box.minZ() + dz,
                box.maxX() + dx, center.getY() + 8, box.maxZ() + dz});
        return new MeteoriteStructurePiece(tag);
    }
}
