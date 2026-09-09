package com.sorrowmist.useless.stretcher.init;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.item.UselessStretcherItem;
import com.sorrowmist.useless.stretcher.content.item.WondrousStaffItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(UselessStretcherMod.MODID);

    public static final DeferredItem<BlockItem> OMNIVERSAL_MYRIAD = ITEMS.register("omniversal_myriad",
            () -> new BlockItem(ModBlocks.OMNIVERSAL_MYRIAD.get(), new Item.Properties()));

    public static final DeferredItem<UselessStretcherItem> USELESS_STRETCHER = ITEMS.register("useless_stretcher",
            () -> new UselessStretcherItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<WondrousStaffItem> WONDROUS_STAFF = ITEMS.register("wondrous_staff",
            WondrousStaffItem::new);

    private ModItems() {
    }
}
