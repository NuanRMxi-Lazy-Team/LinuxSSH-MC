package cn.moerain.linuxssh.client.config

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

/**
 * ModMenu integration for LinuxSSH mod
 */
@Environment(EnvType.CLIENT)
class ModMenuIntegration : ModMenuApi {
    /**
     * Get the config screen factory for LinuxSSH
     */
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent -> LinuxsshConfigScreen.create(parent) }
    }
}