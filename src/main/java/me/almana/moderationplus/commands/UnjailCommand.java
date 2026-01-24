package me.almana.moderationplus.commands;

import java.awt.Color;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.storage.StorageManager.PlayerData;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class UnjailCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;

    public UnjailCommand(ModerationPlus plugin) {
        super("unjail", "Unjail a player");
        this.plugin = plugin;
        this.requirePermission("moderation.unjail");
        this.playerArg = withRequiredArg("player", "Player to unjail", (ArgumentType<String>) ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String targetName = ctx.get(playerArg);


        UUID targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);
        if (targetUuid == null) {
            ctx.sendMessage(Message.raw("Cannot resolve UUID for " + targetName).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        String resolvedName = targetName;
        com.hypixel.hytale.server.core.universe.PlayerRef ref = com.hypixel.hytale.server.core.universe.Universe.get()
                .getPlayer(targetUuid);

        if (ref != null && ref.isValid()) {
            resolvedName = ref.getUsername();
        }

        try {
            PlayerData playerData = plugin.getStorageManager().getPlayerByUUID(targetUuid);
            if (playerData == null) {
                ctx.sendMessage(Message.raw("Player " + resolvedName + " has no jail record.").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }


            java.util.List<me.almana.moderationplus.storage.Punishment> jails = plugin.getStorageManager()
                    .getActivePunishmentsByType(playerData.id(), "JAIL");
            String restoreLocData = null;
            if (!jails.isEmpty()) {
                restoreLocData = jails.get(0).extraData();
            }

            int deactivated = plugin.getStorageManager().deactivatePunishmentsByType(playerData.id(), "JAIL");

            if (deactivated == 0) {
                ctx.sendMessage(Message.raw(resolvedName + " is not jailed.").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }


            plugin.removeJailedPlayer(targetUuid);
            plugin.removeFrozenPlayer(targetUuid);


            if (restoreLocData != null && !restoreLocData.isEmpty() && ref != null && ref.isValid()) {
                try {
                    String[] parts = restoreLocData.split(":");
                    if (parts.length == 2) {
                        UUID worldUuid = UUID.fromString(parts[0]);
                        String[] coords = parts[1].split(",");
                        if (coords.length >= 6) {
                            double x = Double.parseDouble(coords[0]);
                            double y = Double.parseDouble(coords[1]);
                            double z = Double.parseDouble(coords[2]);
                            float yaw = Float.parseFloat(coords[3]);
                            float pitch = Float.parseFloat(coords[4]);


                            com.hypixel.hytale.server.core.universe.world.World world = com.hypixel.hytale.server.core.universe.Universe
                                    .get().getWorld(worldUuid);
                            if (world != null) {

                                com.hypixel.hytale.math.vector.Vector3d targetPos = new com.hypixel.hytale.math.vector.Vector3d(
                                        x, y, z);
                                com.hypixel.hytale.math.vector.Vector3f targetRot = new com.hypixel.hytale.math.vector.Vector3f(
                                        yaw, pitch, 0);

                                ((java.util.concurrent.Executor) world).execute(() -> {
                                    if (ref.isValid()) {
                                        com.hypixel.hytale.server.core.modules.entity.teleport.Teleport teleport = new com.hypixel.hytale.server.core.modules.entity.teleport.Teleport(
                                                targetPos, targetRot);
                                        ref.getReference().getStore().addComponent(ref.getReference(),
                                                com.hypixel.hytale.server.core.modules.entity.teleport.Teleport
                                                        .getComponentType(),
                                                teleport);
                                    }
                                });
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore errors
                }
            }


            String issuerName = (sender instanceof Player) ? sender.getDisplayName() : "Console";
            plugin.notifyStaff(
                    Message.raw("[Staff] " + issuerName + " unjailed " + resolvedName + ".").color(Color.GREEN));

            ctx.sendMessage(Message.raw("Unjailed " + resolvedName).color(Color.GREEN));

        } catch (Exception e) {
            ctx.sendMessage(Message.raw("Error unjailing player: " + e.getMessage()).color(java.awt.Color.RED));
            e.printStackTrace();
        }

        return CompletableFuture.completedFuture(null);
    }
}
