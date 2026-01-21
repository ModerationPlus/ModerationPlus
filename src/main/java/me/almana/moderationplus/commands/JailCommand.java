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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.storage.Punishment;
import me.almana.moderationplus.storage.StorageManager.PlayerData;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import com.hypixel.hytale.server.core.universe.world.World;

public class JailCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;

    public JailCommand(ModerationPlus plugin) {
        super("jail", "Jail a player");
        this.plugin = plugin;
        this.requirePermission("moderation.jail");
        this.playerArg = withRequiredArg("player", "Player to jail", (ArgumentType<String>) ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String targetName = ctx.get(playerArg);

        // Check location
        if (!plugin.getConfigManager().hasJailLocation()) {
            ctx.sendMessage(Message.raw("Jail location is not set. Use /setjail to configure.").color(Color.RED));
            if (sender.hasPermission("moderation.setjail")) {
                ctx.sendMessage(Message.raw("Run /setjail to set the jail location.").color(Color.YELLOW));
            }
            return CompletableFuture.completedFuture(null);
        }

        // Resolve UUID
        UUID targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);
        if (targetUuid == null) {
            ctx.sendMessage(Message.raw("Cannot resolve UUID for " + targetName).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        String resolvedName = targetName;
        PlayerRef ref = Universe.get().getPlayer(targetUuid);
        boolean isOnline = false;

        if (ref != null && ref.isValid()) {
            resolvedName = ref.getUsername();
            isOnline = true;
        }

        // Check bypass
        if (com.hypixel.hytale.server.core.permissions.PermissionsModule.get().hasPermission(targetUuid,
                "moderation.jail.bypass")) {
            ctx.sendMessage(Message.raw(resolvedName + " cannot be jailed.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        try {
            PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(targetUuid, resolvedName);

            // Teleport online
            if (isOnline && ref != null) {
                UUID worldUuid = ref.getWorldUuid();
                if (worldUuid != null) {
                    World world = Universe.get().getWorld(worldUuid);
                    if (world != null) {
                        ((Executor) world).execute(() -> {
                            if (ref.isValid()) {
                                // Capture location
                                com.hypixel.hytale.server.core.modules.entity.component.TransformComponent transform = ref
                                        .getReference().getStore().getComponent(ref.getReference(),
                                                com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
                                                        .getComponentType());
                                String originalLocStr = "";
                                if (transform != null) {
                                    Vector3d pos = transform.getPosition();
                                    Vector3f rot = transform.getRotation();
                                    // Location format
                                    originalLocStr = worldUuid.toString() + ":" + pos.x + "," + pos.y + "," + pos.z
                                            + "," + rot.x + "," + rot.y + "," + rot.z;
                                }

                                // Async database insert
                                final String finalOriginalLocStr = originalLocStr;
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        // Insert punishment
                                        String issuerUuid = (sender instanceof Player) ? sender.getUuid().toString()
                                                : "CONSOLE";
                                        Punishment punishment = new Punishment(
                                                0,
                                                playerData.id(),
                                                "JAIL",
                                                issuerUuid,
                                                "Jailed by staff",
                                                System.currentTimeMillis(),
                                                0,
                                                true,
                                                finalOriginalLocStr); // Store location
                                        plugin.getStorageManager().insertPunishment(punishment);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                });

                                double[] jailLoc = plugin.getConfigManager().getJailLocation();
                                Vector3d jailPos = new Vector3d(jailLoc[0], jailLoc[1], jailLoc[2]);
                                Vector3f jailRot = new Vector3f(0, 0, 0);

                                Teleport teleport = new Teleport(jailPos, jailRot);
                                ref.getReference().getStore().addComponent(ref.getReference(),
                                        Teleport.getComponentType(), teleport);

                                // Add components
                                plugin.addJailedPlayer(targetUuid, jailPos);
                                plugin.addFrozenPlayer(targetUuid, jailPos);
                            }
                        });
                    }
                }
            } else {
                // Offline player
                String issuerUuid = (sender instanceof Player) ? sender.getUuid().toString() : "CONSOLE";
                Punishment punishment = new Punishment(
                        0,
                        playerData.id(),
                        "JAIL",
                        issuerUuid,
                        "Jailed by staff",
                        System.currentTimeMillis(),
                        0,
                        true,
                        null); // No location
                plugin.getStorageManager().insertPunishment(punishment);
            }

            // Notify staff
            String issuerName = (sender instanceof Player) ? sender.getDisplayName() : "Console";
            plugin.notifyStaff(Message.raw("[Staff] " + issuerName + " jailed " + resolvedName + ".").color(Color.RED));

            ctx.sendMessage(Message.raw("Jailed " + resolvedName).color(Color.GREEN));

        } catch (Exception e) {
            ctx.sendMessage(Message.raw("Error jailing player: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }

        return CompletableFuture.completedFuture(null);
    }
}
