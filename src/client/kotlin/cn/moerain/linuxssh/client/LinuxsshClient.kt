package cn.moerain.linuxssh.client

import cn.moerain.linuxssh.config.LinuxsshConfig
import cn.moerain.linuxssh.Linuxssh
import cn.moerain.linuxssh.client.gui.LinuxsshTerminalScreen
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument

/**
 * LinuxSSH mod client entry point
 *
 * Let's dance!
 * @author Celesita
 */

class LinuxsshClient : ClientModInitializer {

    override fun onInitializeClient() {
        // Initialize configuration
        LinuxsshConfig.load()
        
        // Save configuration when client stops
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            LinuxsshConfig.save()
        }

        // Register client-side SSH commands
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, registryAccess ->
            val linuxssh = Linuxssh()
            
            val windowAction = com.mojang.brigadier.Command<FabricClientCommandSource> { context ->
                val client = Minecraft.getInstance()
                val player = client.player
                if (player != null) {
                    val session = Linuxssh.activeSessions[player.uuid]
                    if (session != null && session.isConnected) {
                        client.execute {
                            client.setScreen(LinuxsshTerminalScreen(session))
                        }
                    } else {
                        context.source.sendFeedback(Component.translatable("linuxssh.command.not_connected"))
                    }
                }
                1
            }

            dispatcher.register(
                literal<FabricClientCommandSource>("ssh").apply {
                    then(literal<FabricClientCommandSource>("window").executes(windowAction))
                    then(literal<FabricClientCommandSource>("windows").executes(windowAction))
                    then(literal<FabricClientCommandSource>("disconnect").executes { context ->
                        linuxssh.handleDisconnect(context.source)
                    })
                    then(literal<FabricClientCommandSource>("password").then(
                        argument<FabricClientCommandSource, String>("password", StringArgumentType.greedyString()).executes { context ->
                            linuxssh.handlePasswordInput(context.source, StringArgumentType.getString(context, "password"))
                        }
                    ))
                    then(literal<FabricClientCommandSource>("confirm").apply {
                        then(literal<FabricClientCommandSource>("yes").executes { context ->
                            linuxssh.handleFingerprintConfirmation(context.source, true)
                        })
                        then(literal<FabricClientCommandSource>("no").executes { context ->
                            linuxssh.handleFingerprintConfirmation(context.source, false)
                        })
                    })
                    then(literal<FabricClientCommandSource>("key").apply {
                        then(literal<FabricClientCommandSource>("import").then(
                            argument<FabricClientCommandSource, String>("keypath", StringArgumentType.greedyString()).executes { context ->
                                linuxssh.handleKeyImport(context.source, StringArgumentType.getString(context, "keypath"))
                            }
                        ))
                        then(literal<FabricClientCommandSource>("generate").executes { context ->
                            linuxssh.handleKeyGenerate(context.source)
                        })
                    })
                    then(literal<FabricClientCommandSource>("connect").then(
                        argument<FabricClientCommandSource, String>("connection", StringArgumentType.greedyString()).executes { context ->
                            linuxssh.handleSshConnect(context.source, StringArgumentType.getString(context, "connection"))
                        }
                    ))
                    then(literal<FabricClientCommandSource>("command").then(
                        argument<FabricClientCommandSource, String>("command", StringArgumentType.greedyString()).executes { context ->
                            linuxssh.handleSshCommand(context.source, StringArgumentType.getString(context, "command"))
                        }
                    ))
                }
            )
        }

        // Show reminder toast when joining a world
        ClientPlayConnectionEvents.JOIN.register { handler, sender, client ->
            client.execute {
                client.toastManager.addToast(
                    SystemToast(
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.translatable("linuxssh.toast.warning.title"),
                        Component.translatable("linuxssh.toast.warning.message")
                    )
                )
            }
        }
    }
}
