package com.hyperclient.HyperMiner.baritone.logging;

import com.hyperclient.HyperMiner.HyperMiner;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import org.apache.logging.log4j.LogManager;

public class Logger {
    // POV : You think this is a token logger
    public static void log(String msg){
        LogManager.getLogger(MightyMiner.MODID).info(msg);
    }
    public static void playerLog(String msg){
        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("[Baritone] : " + msg));
    }
}
