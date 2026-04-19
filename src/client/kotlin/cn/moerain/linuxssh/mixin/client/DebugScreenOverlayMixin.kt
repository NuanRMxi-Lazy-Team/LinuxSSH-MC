package cn.moerain.linuxssh.mixin.client

import cn.moerain.linuxssh.client.debug.DebugEntrySshHostStatus
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(targets = ["net.minecraft.client.gui.components.debug.DebugScreenOverlay"])
abstract class DebugScreenOverlayMixin {

    @Shadow
    @Final
    private lateinit var debugScreenEntries: MutableList<Any>

    @Inject(method = ["<init>"], at = [At("RETURN")])
    private fun onInit(cb: CallbackInfo) {
        // Add our SSH host status entry to the debug screen
        if (!debugScreenEntries.any { it is DebugEntrySshHostStatus }) {
            debugScreenEntries.add(DebugEntrySshHostStatus())
        }
    }
}
