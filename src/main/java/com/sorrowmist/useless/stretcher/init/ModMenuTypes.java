package com.sorrowmist.useless.stretcher.init;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.menu.OmniversalMyriadMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import com.sorrowmist.useless.compat.forge1201.registry.DeferredRegister;

import java.util.function.Supplier;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, UselessStretcherMod.MODID);

    public static final Supplier<MenuType<OmniversalMyriadMenu>> OMNIVERSAL_MYRIAD =
            MENUS.register("omniversal_myriad",
                    () -> IForgeMenuType.create(OmniversalMyriadMenu::new));

    private ModMenuTypes() {
    }
}
