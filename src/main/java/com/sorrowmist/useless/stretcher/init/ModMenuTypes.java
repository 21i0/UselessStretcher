package com.sorrowmist.useless.stretcher.init;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.menu.OmniversalMyriadMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, UselessStretcherMod.MODID);

    public static final Supplier<MenuType<OmniversalMyriadMenu>> OMNIVERSAL_MYRIAD =
            MENUS.register("omniversal_myriad",
                    () -> IMenuTypeExtension.create(OmniversalMyriadMenu::new));

    private ModMenuTypes() {
    }
}
