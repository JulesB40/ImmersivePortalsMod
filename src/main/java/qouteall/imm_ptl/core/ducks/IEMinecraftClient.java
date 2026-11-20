package qouteall.imm_ptl.core.ducks;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;

public interface IEMinecraftClient {
    Screen ip_getCurrentScreen();
    
    void ip_setWorldRenderer(LevelRenderer r);
    
    void ip_setLevelExtractor(net.minecraft.client.renderer.extract.LevelExtractor e);
    
    Thread ip_getRunningThread();
}
