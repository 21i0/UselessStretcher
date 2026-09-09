package com.sorrowmist.useless.stretcher.content.blockentity;

import com.sorrowmist.useless.stretcher.content.ae.AeBindingStore;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import com.sorrowmist.useless.stretcher.content.mold.MyriadPatternStore;
import com.sorrowmist.useless.stretcher.init.ModBlockEntities;
import com.sorrowmist.useless.stretcher.menu.OmniversalMyriadMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class OmniversalMyriadBlockEntity extends BlockEntity implements MenuProvider {
    private static final String TAG_ENABLED = "EnabledMolds";
    private static final String TAG_PATTERN_REF = "PatternRef";
    private static final String TAG_AE_REF = "AeRef";

    private final Set<ResourceLocation> enabledMolds = new LinkedHashSet<>();
    private UUID patternRef;
    private UUID aeRef;

    public OmniversalMyriadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OMNIVERSAL_MYRIAD.get(), pos, state);
    }

    public Set<ResourceLocation> getEnabledMolds() {
        return enabledMolds;
    }

    public UUID getPatternRef() {
        return patternRef;
    }

    public UUID getAeRef() {
        return aeRef;
    }

    public void setAeRef(UUID ref) {
        this.aeRef = ref;
        setChanged();
    }

    /** Resolves the bound AE node position from the external binding store. */
    public BlockPos getAeNodePos() {
        if (aeRef == null) return null;
        return level instanceof ServerLevel serverLevel
                ? AeBindingStore.get(serverLevel).get(aeRef) : null;
    }

    public Set<ResourceLocation> getPatternMolds() {
        MyriadPatternStore store = store();
        return store == null ? Set.of() : store.get(patternRef).keySet();
    }

    public List<ItemStack> getPatterns() {
        MyriadPatternStore store = store();
        return store == null ? List.of() : store.flatten(patternRef);
    }

    public void setEnabledMolds(Collection<ResourceLocation> molds) {
        enabledMolds.clear();
        if (molds != null) enabledMolds.addAll(molds);
        setChanged();
    }

    public void setMoldPatterns(ResourceLocation moldId, Collection<ItemStack> patterns) {
        MyriadPatternStore store = store();
        if (store == null) return;
        if (patternRef == null) patternRef = UUID.randomUUID();
        store.setMold(patternRef, moldId, patterns == null ? List.of() : List.copyOf(patterns));
        setChanged();
    }

    public void clearPatterns() {
        MyriadPatternStore store = store();
        if (store != null) store.clear(patternRef);
        setChanged();
    }

    private MyriadPatternStore store() {
        return level instanceof ServerLevel serverLevel ? MyriadPatternStore.get(serverLevel) : null;
    }

    public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
        MyriadMoldData.writeEnabledMolds(stack, enabledMolds);
        MyriadMoldData.writePatternRef(stack, patternRef);
        MyriadMoldData.writeAeRef(stack, aeRef);
    }

    public void loadFromItem(ItemStack stack, HolderLookup.Provider registries) {
        enabledMolds.clear();
        enabledMolds.addAll(MyriadMoldData.readEnabledMolds(stack));
        patternRef = MyriadMoldData.readPatternRef(stack);
        aeRef = MyriadMoldData.readAeRef(stack);
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        ListTag enabled = new ListTag();
        for (ResourceLocation id : enabledMolds) enabled.add(StringTag.valueOf(id.toString()));
        tag.put(TAG_ENABLED, enabled);

        if (patternRef != null) tag.putString(TAG_PATTERN_REF, patternRef.toString());
        if (aeRef != null) tag.putString(TAG_AE_REF, aeRef.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        enabledMolds.clear();
        patternRef = null;
        aeRef = null;

        ListTag enabled = tag.getList(TAG_ENABLED, Tag.TAG_STRING);
        for (int i = 0; i < enabled.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(enabled.getString(i));
            if (id != null) enabledMolds.add(id);
        }

        String ref = tag.getString(TAG_PATTERN_REF);
        if (!ref.isEmpty()) {
            try {
                patternRef = UUID.fromString(ref);
            } catch (IllegalArgumentException ignored) {
            }
        }

        String ae = tag.getString(TAG_AE_REF);
        if (!ae.isEmpty()) {
            try {
                aeRef = UUID.fromString(ae);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.useless_stretcher.omniversal_myriad");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new OmniversalMyriadMenu(containerId, inventory, worldPosition);
    }
}
