package com.sorrowmist.useless.stretcher.content.capsule;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

/** SOURCE ONLY: chunk-owned persistence, with no server/global position index. */
@EventBusSubscriber(modid = "useless_stretcher", bus = EventBusSubscriber.Bus.MOD)
public final class CapsuleChunkGravityAttachments {
    public static final AttachmentType<CapsuleChunkGravityData> TYPE = AttachmentType
            .builder((IAttachmentHolder holder) -> empty(holder))
            .serialize(new IAttachmentSerializer<Tag, CapsuleChunkGravityData>() {
                @Override public CapsuleChunkGravityData read(IAttachmentHolder holder, Tag tag,
                                                               HolderLookup.Provider provider) {
                    ChunkAccess chunk = chunk(holder);
                    return CapsuleChunkGravityData.load(tag, chunk.getMinSection(),
                            chunk.getMinSection() + chunk.getSectionsCount());
                }
                @Override public Tag write(CapsuleChunkGravityData data, HolderLookup.Provider provider) {
                    // AttachmentHolder catches serializer exceptions and skips the
                    // payload. Preserve unknown tag types instead of throwing.
                    return data.saveTag();
                }
            }).build();

    private CapsuleChunkGravityAttachments() {}

    private static ChunkAccess chunk(IAttachmentHolder holder) {
        if (!(holder instanceof ChunkAccess chunk))
            throw new IllegalArgumentException("Capsule gravity attachment is only valid on chunks");
        return chunk;
    }
    private static CapsuleChunkGravityData empty(IAttachmentHolder holder) {
        ChunkAccess chunk = chunk(holder);
        return new CapsuleChunkGravityData(chunk.getMinSection(),
                chunk.getMinSection() + chunk.getSectionsCount());
    }
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
                ResourceLocation.fromNamespaceAndPath("useless_stretcher", "capsule_native_gravity"), () -> TYPE);
    }
}
