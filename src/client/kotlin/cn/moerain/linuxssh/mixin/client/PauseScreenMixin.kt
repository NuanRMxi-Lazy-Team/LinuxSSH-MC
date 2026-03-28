package cn.moerain.linuxssh.mixin.client

import cn.moerain.linuxssh.client.config.LinuxsshConfigScreen
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(PauseScreen::class)
abstract class PauseScreenMixin(title: Component) : Screen(title) {
    @Inject(method = ["init"], at = [At("HEAD")])
    private fun onInit(ci: CallbackInfo) {
        // Placement in PauseScreen: Bottom-right, similar to OptionsScreen
        this.addRenderableWidget(
            Button.builder(Component.literal("SSH")) {
                this.minecraft.setScreen(LinuxsshConfigScreen.create(this))
            }.bounds(this.width / 2 + 104, this.height / 4 + 144 - 6, 45, 20).build()
        )
    }
}
