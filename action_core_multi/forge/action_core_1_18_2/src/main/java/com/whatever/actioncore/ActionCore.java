package com.whatever.actioncore;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(ActionCore.MODID)
public class ActionCore
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "actioncore";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public ActionCore() {
        var context = FMLJavaModLoadingContext.get();
        IEventBus modEventBus = context.getModEventBus();
    }
}
