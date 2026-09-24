package com.sorrowmist.useless.stretcher.content.item;

import net.minecraft.world.item.Item;

/** Administrative tool for removing any placed time-flow field. */
public final class RangeReclaimerItem extends Item {
    public RangeReclaimerItem(Properties properties) {
        super(properties.stacksTo(1));
    }
}
