package cn.moerain.linuxssh.mixin.client

import cn.moerain.linuxssh.client.config.LinuxsshConfigScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(OptionsScreen::class)
abstract class OptionsScreenMixin(title: Component) : Screen(title) {
    @Inject(method = ["init"], at = [At("HEAD")])
    private fun onInit(ci: CallbackInfo) {
        this.addRenderableWidget(
            Button.builder(Component.translatable("linuxssh.config.title")) {
                this.minecraft?.setScreen(LinuxsshConfigScreen.create(this))
            }.bounds(this.width / 2 - 155, this.height / 6 + 130, 150, 20).build()
        )
    }
}
