package me.almana.moderationplus.commands;

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

import me.almana.moderationplus.ModerationPlus;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import com.hypixel.hytale.server.core.universe.world.World;

public class FreezeCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;

    public FreezeCommand(ModerationPlus plugin) {
        super("freeze", "Freeze a player in place");
        this.plugin = plugin;
        this.requirePermission("moderation.freeze");
        this.playerArg = withRequiredArg("player", "Player to freeze", (ArgumentType<String>) ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String targetName = ctx.get(playerArg);

        UUID targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);
        if (targetUuid == null) {
            ctx.sendMessage(Message.raw("Player '" + targetName + "' not found.").color(java.awt.Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        PlayerRef ref = Universe.get().getPlayer(targetUuid);
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("Player '" + targetName + "' is not online.").color(java.awt.Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        String resolvedName = ref.getUsername();

        if (com.hypixel.hytale.server.core.permissions.PermissionsModule.get().hasPermission(targetUuid,
                "moderation.freeze.bypass")) {
            ctx.sendMessage(Message.raw(resolvedName + " cannot be frozen.").color(java.awt.Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        UUID worldUuid = ref.getWorldUuid();
        if (worldUuid != null) {
            World world = Universe.get().getWorld(worldUuid);
            if (world != null) {
                ((Executor) world).execute(() -> {
                    if (ref.isValid()) {
                        com.hypixel.hytale.server.core.modules.entity.component.TransformComponent transform = ref
                                .getReference()
                                .getStore().getComponent(ref.getReference(),
                                        com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
                                                .getComponentType());

                        if (transform != null) {
                            plugin.addFrozenPlayer(targetUuid, transform.getPosition());
                        } else {
                            plugin.addFrozenPlayer(targetUuid, new com.hypixel.hytale.math.vector.Vector3d(0, 100, 0));
                        }

                        ref.sendMessage(Message.raw("You have been frozen by staff. Do not log out.")
                                .color(java.awt.Color.RED));
                    }
                });
            }
        }

        String issuerName = (sender instanceof Player) ? sender.getDisplayName() : "Console";
        plugin.notifyStaff(
                Message.raw("[Staff] " + issuerName + " froze " + resolvedName + ".").color(java.awt.Color.YELLOW));

        ctx.sendMessage(Message.raw("Froze " + resolvedName).color(java.awt.Color.GREEN));

        return CompletableFuture.completedFuture(null);
    }
}
