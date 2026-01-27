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
import java.util.UUID;
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

        // Case 1: Coordinates provided
        if (xArg.provided(ctx) && yArg.provided(ctx) && zArg.provided(ctx)) {
            double x = ctx.get(xArg);
            double y = ctx.get(yArg);
            double z = ctx.get(zArg);
            setJailLocation(ctx, x, y, z);
            return CompletableFuture.completedFuture(null);
        }

        // Case 2: No coordinates provided (Use Player Position)
        if (sender instanceof Player) {
            Player player = (Player) sender;
            final String username = player.getDisplayName();

            CompletableFuture.runAsync(() -> {
                try {
                    PlayerRef ref = Universe.get().getPlayer(username, NameMatching.EXACT);
                    if (ref == null || !ref.isValid()) {
                        player.sendMessage(plugin.getLanguageManager().translateToMessage("command.setjail.player_ref_failed", player.getUuid()));
                        return;
                    }

                    TransformComponent transform = ref.getReference().getStore()
                            .getComponent(ref.getReference(), TransformComponent.getComponentType());

                    if (transform == null) {
                        player.sendMessage(plugin.getLanguageManager().translateToMessage("command.setjail.player_location_failed", player.getUuid()));
                        return;
                    }

                    Vector3d position = transform.getPosition();
                    plugin.getConfigManager().setJailLocation(position.getX(), position.getY(), position.getZ());
                    player.sendMessage(plugin.getLanguageManager().translateToMessage(
                        "command.setjail.success",
                        player.getUuid(),
                        java.util.Map.of(
                            "x", String.format("%.1f", position.getX()),
                            "y", String.format("%.1f", position.getY()),
                            "z", String.format("%.1f", position.getZ())
                        )
                    ));

                } catch (Exception e) {
                    player.sendMessage(plugin.getLanguageManager().translateToMessage(
                        "command.setjail.failed",
                        player.getUuid(),
                        java.util.Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error")
                    ));
                    e.printStackTrace();
                }
            }, player.getWorld());

            return CompletableFuture.completedFuture(null);
        }

        // Case 3: No coordinates, and is Console
        ctx.sendMessage(plugin.getLanguageManager().translateToMessage("command.setjail.console_usage", null));
        return CompletableFuture.completedFuture(null);
    }

    private void setJailLocation(CommandContext ctx, double x, double y, double z) {
        CommandSender sender = ctx.sender();
        UUID uuid = (sender instanceof Player) ? sender.getUuid() : null;
        try {
            plugin.getConfigManager().setJailLocation(x, y, z);
            ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                "command.setjail.success",
                uuid,
                java.util.Map.of(
                    "x", String.format("%.1f", x),
                    "y", String.format("%.1f", y),
                    "z", String.format("%.1f", z)
                )
            ));
        } catch (Exception e) {
            ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                "command.setjail.failed",
                uuid,
                java.util.Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error")
            ));
            e.printStackTrace();
        }
    }
}
