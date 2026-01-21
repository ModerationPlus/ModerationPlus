package me.almana.moderationplus.commands;

import java.awt.Color;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.math.vector.Vector3d;
import me.almana.moderationplus.ModerationPlus;
import java.util.concurrent.CompletableFuture;

public class SetJailCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final OptionalArg<Double> xArg;
    private final OptionalArg<Double> yArg;
    private final OptionalArg<Double> zArg;

    public SetJailCommand(ModerationPlus plugin) {
        super("setjail", "Set the jail location");
        this.plugin = plugin;
        this.requirePermission("moderation.setjail");
        this.xArg = withOptionalArg("x", "X coordinate", (ArgumentType<Double>) ArgTypes.DOUBLE);
        this.yArg = withOptionalArg("y", "Y coordinate", (ArgumentType<Double>) ArgTypes.DOUBLE);
        this.zArg = withOptionalArg("z", "Z coordinate", (ArgumentType<Double>) ArgTypes.DOUBLE);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        if (xArg.provided(ctx) && yArg.provided(ctx) && zArg.provided(ctx)) {
            // Explicit coordinates
            double x = ctx.get(xArg);
            double y = ctx.get(yArg);
            double z = ctx.get(zArg);
            setJailLocation(ctx, x, y, z);
        } else {
            // Player position
            if (!(sender instanceof Player)) {
                ctx.sendMessage(Message.raw("Console must specify coordinates: /setjail <x> <y> <z>").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            Player player = (Player) sender;
            final String username = player.getDisplayName();

            // Resolve component
            CompletableFuture.runAsync(() -> {
                try {
                    PlayerRef ref = Universe.get().getPlayer(username, NameMatching.EXACT);
                    if (ref == null || !ref.isValid()) {
                        // Invalid reference
                        player.sendMessage(Message.raw("Failed to resolve your player reference.").color(Color.RED));
                        return;
                    }

                    TransformComponent transform = ref.getReference().getStore()
                            .getComponent(ref.getReference(), TransformComponent.getComponentType());

                    if (transform == null) {
                        player.sendMessage(Message.raw("Failed to get your position component.").color(Color.RED));
                        return;
                    }

                    Vector3d position = transform.getPosition();
                    plugin.getConfigManager().setJailLocation(position.getX(), position.getY(), position.getZ());
                    player.sendMessage(
                            Message.raw(String.format("Jail location set to your position (%.1f, %.1f, %.1f)",
                                    position.getX(), position.getY(), position.getZ())).color(Color.GREEN));

                } catch (Exception e) {
                    player.sendMessage(Message.raw("Error setting jail location: " + e.getMessage()).color(Color.RED));
                    e.printStackTrace();
                }
            }, player.getWorld());
        }
        return CompletableFuture.completedFuture(null);
    }

    private void setJailLocation(CommandContext ctx, double x, double y, double z) {
        try {
            plugin.getConfigManager().setJailLocation(x, y, z);
            ctx.sendMessage(Message.raw(String.format("Jail location set to X=%.1f Y=%.1f Z=%.1f", x, y, z))
                    .color(Color.GREEN));
        } catch (Exception e) {
            ctx.sendMessage(Message.raw("Error setting jail location: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }
    }
}
