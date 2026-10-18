package com.sorrowmist.useless.stretcher.content.item;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;

/** Diamond armor stats; item icons use the useful ingot animation and players have no armor layer. */
public final class UsefulArmorItem extends ArmorItem {
    public UsefulArmorItem(Type type) {
        super(ArmorMaterials.DIAMOND, type,
                new Item.Properties().durability(type.getDurability(33)));
    }
}
