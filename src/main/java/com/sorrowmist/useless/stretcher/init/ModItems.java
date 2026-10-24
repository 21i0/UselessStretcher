package com.sorrowmist.useless.stretcher.init;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.item.UselessStretcherItem;
import com.sorrowmist.useless.stretcher.content.item.WondrousStaffItem;
import com.sorrowmist.useless.stretcher.content.item.RangeReclaimerItem;
import com.sorrowmist.useless.stretcher.content.item.TwoIDollItem;
import com.sorrowmist.useless.stretcher.content.item.DimensionCapsuleItem;
import com.sorrowmist.useless.stretcher.content.item.UsefulArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import com.sorrowmist.useless.compat.forge1201.registry.DeferredItem;
import com.sorrowmist.useless.compat.forge1201.registry.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(UselessStretcherMod.MODID);

    public static final DeferredItem<BlockItem> OMNIVERSAL_MYRIAD = ITEMS.register("omniversal_myriad",
            () -> new BlockItem(ModBlocks.OMNIVERSAL_MYRIAD.get(), new Item.Properties()));

    public static final DeferredItem<UselessStretcherItem> USELESS_STRETCHER = ITEMS.register("useless_stretcher",
            () -> new UselessStretcherItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<WondrousStaffItem> WONDROUS_STAFF = ITEMS.register("wondrous_staff",
            WondrousStaffItem::new);

    /** No recipe: intended for server operators and normally obtained with /give. */
    public static final DeferredItem<RangeReclaimerItem> RANGE_RECLAIMER = ITEMS.register("range_reclaimer",
            () -> new RangeReclaimerItem(new Item.Properties()));

    public static final DeferredItem<TwoIDollItem> TWO_I_DOLL = ITEMS.register("two_i_doll",
            TwoIDollItem::new);

    public static final DeferredItem<DimensionCapsuleItem> DIMENSION_CAPSULE = ITEMS.register("dimension_capsule",
            () -> new DimensionCapsuleItem(new Item.Properties()));

    // Creative-mode suit; its standard ingot recipes are disabled by default.
    public static final DeferredItem<UsefulArmorItem> USEFUL_ARMOR_HELMET = ITEMS.register("useful_armor_helmet",
            () -> new UsefulArmorItem(net.minecraft.world.item.ArmorItem.Type.HELMET));
    public static final DeferredItem<UsefulArmorItem> USEFUL_ARMOR_CHESTPLATE = ITEMS.register("useful_armor_chestplate",
            () -> new UsefulArmorItem(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE));
    public static final DeferredItem<UsefulArmorItem> USEFUL_ARMOR_LEGGINGS = ITEMS.register("useful_armor_leggings",
            () -> new UsefulArmorItem(net.minecraft.world.item.ArmorItem.Type.LEGGINGS));
    public static final DeferredItem<UsefulArmorItem> USEFUL_ARMOR_BOOTS = ITEMS.register("useful_armor_boots",
            () -> new UsefulArmorItem(net.minecraft.world.item.ArmorItem.Type.BOOTS));

    private ModItems() {
    }
}
