package com.whatever.actioncore;

import cpw.mods.fml.common.Mod;

@Mod(
        modid = ActionCore.MODID,
        name = ActionCore.NAME,
        version = ActionCore.VERSION
)
public class ActionCore
{
    @Mod.EventHandler
    public void initialize(cpw.mods.fml.common.event.FMLInitializationEvent event) {
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.whatever.actioncore.event.ActionCoreTickEventMethods());
        if (event.getSide().isClient()) cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.whatever.actioncore.event.ClientActionCoreTickEventMethods());
    }
    public static final String MODID = "actioncore";
    public static final String NAME = "Action Core";
    public static final String VERSION = "1.0.1";
}