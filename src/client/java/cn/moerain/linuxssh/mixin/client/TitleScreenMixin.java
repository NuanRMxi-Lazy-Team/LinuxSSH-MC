package cn.moerain.linuxssh.mixin.client;

import cn.moerain.linuxssh.client.MinecraftBridge;
import cn.moerain.linuxssh.client.config.LinuxsshConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        final Screen self = this;
        this.addRenderableWidget(
            Button.builder(Component.literal("SSH"), new Button.OnPress() {
                @Override
                public void onPress(Button button) {
                    MinecraftBridge.setScreen(Minecraft.getInstance(), LinuxsshConfigScreen.create(self));
                }
            }).bounds(this.width / 2 + 104, this.height / 6 + 144 - 6, 45, 20).build()
        );
    }
}
