package com.sorrowmist.useless.stretcher.content.blockentity;

import com.sorrowmist.useless.stretcher.content.ae.AeBindingStore;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import com.sorrowmist.useless.stretcher.content.mold.MyriadPatternStore;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldStore;
import com.sorrowmist.useless.stretcher.content.mold.PatternFetcher;
import com.sorrowmist.useless.stretcher.event.MyriadWorkQueue;
import appeng.api.stacks.AEItemKey;
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
    private UUID moldRef;
    private boolean resolveMolds;
    private UUID patternRef;
    private UUID aeRef;
    private PatternFetcher fetch;
    private final Set<ResourceLocation> pendingFetch = new LinkedHashSet<>();
    private String fetchResult = "";
    private int selectionVersion;

    public int selectionVersion() { return selectionVersion; }

    public OmniversalMyriadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OMNIVERSAL_MYRIAD.get(), pos, state);
    }

    public Set<ResourceLocation> getEnabledMolds() {
        if (resolveMolds && level instanceof ServerLevel serverLevel) {
            enabledMolds.clear();
            enabledMolds.addAll(MyriadMoldStore.get(serverLevel.getServer()).resolve(moldRef));
            resolveMolds = false;
        }
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
        return store == null ? Set.of() : store.molds(patternRef);
    }

    public List<ItemStack> getPatterns() {
        MyriadPatternStore store = store();
        return store == null ? List.of() : store.flatten(patternRef);
    }

    public void setEnabledMolds(Collection<ResourceLocation> molds) {
        moldRef = null;
        resolveMolds = false;
        enabledMolds.clear();
        if (molds != null) enabledMolds.addAll(molds);
        if (level instanceof ServerLevel serverLevel) {
            moldRef = MyriadMoldStore.get(serverLevel.getServer()).snapshot(enabledMolds);
        }
        selectionVersion++;
        setChanged();
    }

    public int getPatternCount() {
        MyriadPatternStore store = store();
        return store == null ? 0 : store.count(patternRef);
    }

    public void replacePatternKeys(ResourceLocation moldId, Set<AEItemKey> patterns) {
        MyriadPatternStore store = store();
        if (store == null) return;
        if (patternRef == null) patternRef = UUID.randomUUID();
        store.replace(patternRef, moldId, patterns);
        setChanged();
    }

    public void requestPatterns(Collection<ResourceLocation> ids) {
        selectionVersion++;
        fetchResult = "";
        for (ResourceLocation id : ids) {
            if (id != null && (fetch == null || !fetch.contains(id))) pendingFetch.add(id);
        }
        if (!pendingFetch.isEmpty() || fetch != null) MyriadWorkQueue.enqueue(this);
    }

    public void removePatterns(Collection<ResourceLocation> ids) {
        selectionVersion++;
        for (ResourceLocation id : ids) {
            pendingFetch.remove(id);
            if (fetch != null) fetch.cancel(id);
            replacePatternKeys(id, Set.of());
        }
    }

    public Set<ResourceLocation> getRequestedPatternMolds() {
        Set<ResourceLocation> ids = new LinkedHashSet<>(getPatternMolds());
        ids.addAll(pendingFetch);
        if (fetch != null) ids.addAll(fetch.molds());
        return ids;
    }

    public String getFetchProgress() {
        if (fetch != null) return fetch.progress();
        return pendingFetch.isEmpty() ? fetchResult : "0";
    }

    public void cancelFetch() {
        selectionVersion++;
        fetch = null;
        pendingFetch.clear();
    }

    public void setFetchFailed() { fetchResult = "failed"; }

    public boolean stepFetch() {
        if (!(level instanceof ServerLevel serverLevel)) return true;
        if (fetch == null && !pendingFetch.isEmpty()) {
            fetch = new PatternFetcher(pendingFetch);
            pendingFetch.clear();
        }
        if (fetch == null) return true;
        if (fetch.step(serverLevel, this)) {
            selectionVersion++;
            if (fetch.failures() > 0) fetchResult = "partial";
            fetch = null;
        }
        return fetch == null && pendingFetch.isEmpty();
    }

    @Override
    public void setRemoved() {
        cancelFetch();
        super.setRemoved();
    }

    public void clearPatterns() {
        cancelFetch();
        fetchResult = "";
        MyriadPatternStore store = store();
        if (store != null) store.clear(patternRef);
        setChanged();
    }

    private MyriadPatternStore store() {
        return level instanceof ServerLevel serverLevel ? MyriadPatternStore.get(serverLevel) : null;
    }

    public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
        if (level instanceof ServerLevel serverLevel) {
            MyriadMoldData.writeEnabledMolds(stack, getEnabledMolds(), serverLevel);
            moldRef = MyriadMoldData.readMoldRef(stack);
        }
        MyriadMoldData.writePatternRef(stack, patternRef);
        MyriadMoldData.writeAeRef(stack, aeRef);
    }

    public void loadFromItem(ItemStack stack, HolderLookup.Provider registries) {
        if (level instanceof ServerLevel serverLevel) MyriadMoldData.externalize(serverLevel, stack);
        enabledMolds.clear();
        enabledMolds.addAll(MyriadMoldData.readEnabledMolds(stack));
        moldRef = MyriadMoldData.readMoldRef(stack);
        resolveMolds = false;
        patternRef = MyriadMoldData.readPatternRef(stack);
        aeRef = MyriadMoldData.readAeRef(stack);
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Publish legacy selections before the server's SavedData/chunk save phases can diverge.
        if (moldRef == null && !enabledMolds.isEmpty() && level instanceof ServerLevel serverLevel) {
            moldRef = MyriadMoldStore.get(serverLevel.getServer()).snapshot(enabledMolds);
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        if (moldRef == null && level instanceof ServerLevel serverLevel) {
            moldRef = MyriadMoldStore.get(serverLevel.getServer()).snapshot(getEnabledMolds());
        }
        if (moldRef != null) {
            tag.putUUID("MoldRef", moldRef);
        } else if (!(level instanceof ServerLevel)) {
            ListTag enabled = new ListTag();
            for (ResourceLocation id : enabledMolds) enabled.add(StringTag.valueOf(id.toString()));
            tag.put(TAG_ENABLED, enabled);
        }

        if (patternRef != null) tag.putString(TAG_PATTERN_REF, patternRef.toString());
        if (aeRef != null) tag.putString(TAG_AE_REF, aeRef.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        enabledMolds.clear();
        moldRef = tag.hasUUID("MoldRef") ? tag.getUUID("MoldRef") : null;
        resolveMolds = moldRef != null;
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
