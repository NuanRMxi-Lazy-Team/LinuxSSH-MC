package cn.moerain.linuxssh.mixin.client;

import cn.moerain.linuxssh.client.debug.DebugEntrySshHostStatus;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {

    @Inject(
        method = "extractLines",
        at = @At("HEAD")
    )
    private void onExtractLines(GuiGraphicsExtractor extractor, List<String> list, boolean isLeft, int y, CallbackInfo ci) {
        if (isLeft) {
            DebugEntrySshHostStatus.appendStatusLines(list);
        }
    }
}
