package me.almana.moderationplus.commands;

import com.hypixel.hytale.server.core.Message;
import java.awt.Color;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.accesscontrol.ban.TimedBan;
import com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleBanProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.concurrent.Executor;

import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.storage.Punishment;
import me.almana.moderationplus.storage.StorageManager.PlayerData;
import me.almana.moderationplus.utils.TimeUtils;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TempBanCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;
    private final RequiredArg<String> durationArg;
    private final RequiredArg<String> reasonArg;

    public TempBanCommand(ModerationPlus plugin) {
        super("tempban", "Ban a player temporarily");
        this.plugin = plugin;
        this.requirePermission("moderation.tempban");
        this.playerArg = withRequiredArg("player", "Player to ban", (ArgumentType<String>) ArgTypes.STRING);
        this.durationArg = withRequiredArg("duration", "Duration (e.g. 5m, 1h)",
                (ArgumentType<String>) ArgTypes.STRING);
        this.reasonArg = withRequiredArg("reason", "Ban reason", (ArgumentType<String>) ArgTypes.STRING);
        setAllowsExtraArguments(true);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String targetName = ctx.get(playerArg);
        String durationStr = ctx.get(durationArg);


        String fullInput = ctx.getInputString();
        String reason = "Banned by an operator.";
        int dIdx = fullInput.toLowerCase().indexOf(" " + durationStr.toLowerCase() + " ");
        if (dIdx != -1) {

            String sub = fullInput.substring(dIdx + durationStr.length() + 2).trim();
            if (!sub.isEmpty())
                reason = sub;
        } else {
            reason = ctx.get(reasonArg);
        }

        String issuerUuid = (sender instanceof Player) ? sender.getUuid().toString() : "CONSOLE";

        long duration;
        try {
            duration = TimeUtils.parseDuration(durationStr);
        } catch (IllegalArgumentException e) {
            ctx.sendMessage(Message.raw("Invalid duration format. Use 5m, 1h, 1d, etc.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        long expiresAtMillis = System.currentTimeMillis() + duration;
        Instant expiresOn = Instant.ofEpochMilli(expiresAtMillis);

        String resolvedName = targetName;
        boolean isOnline = false;


        UUID targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);

        if (targetUuid == null) {
            ctx.sendMessage(Message.raw("Cannot resolve UUID for " + targetName).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        PlayerRef ref = Universe.get().getPlayer(targetUuid);
        if (ref != null && ref.isValid()) {
            resolvedName = ref.getUsername();
            isOnline = true;


            if (com.hypixel.hytale.server.core.permissions.PermissionsModule.get().hasPermission(targetUuid,
                    "moderation.bypass")) {
                ctx.sendMessage(Message.raw(resolvedName + " cannot be punished.").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
        }

        PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(targetUuid, resolvedName);

        try {

            HytaleBanProvider banProvider = plugin.getBanProvider();
            if (banProvider != null) {
                if (banProvider.hasBan(targetUuid)) {
                    ctx.sendMessage(Message.raw("Player is already natively banned.").color(Color.RED));
                    return CompletableFuture.completedFuture(null);
                }

                UUID issuerId = (sender instanceof Player) ? sender.getUuid()
                        : UUID.nameUUIDFromBytes("CONSOLE".getBytes());
                TimedBan nativeBan = new TimedBan(targetUuid, issuerId, Instant.now(), expiresOn, reason);
                banProvider.modify(bans -> {
                    bans.put(targetUuid, nativeBan);
                    return true;
                });
            } else {
                ctx.sendMessage(Message.raw("Error: Native Ban Provider not found. Tempban failed.").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }


            List<Punishment> activeBans = plugin.getStorageManager().getActivePunishmentsByType(playerData.id(), "BAN");
            if (activeBans.isEmpty()) {
                Punishment ban = new Punishment(0, playerData.id(), "BAN", issuerUuid, reason,
                        System.currentTimeMillis(), expiresAtMillis, true, "{}");
                plugin.getStorageManager().createPunishment(ban);
            }

            String formattedDuration = TimeUtils.formatDuration(duration);
            String staffMsg = "[Staff] " + sender.getDisplayName() + " temp-banned " + resolvedName + " for "
                    + formattedDuration + " (" + reason + ")";
            plugin.notifyStaff(Message.raw(staffMsg).color(Color.GREEN));

            if (isOnline && ref != null && ref.isValid()) {
                final String finalFormattedDuration = formattedDuration;
                final String finalReason = reason;

                UUID worldUuid = ref.getWorldUuid();
                if (worldUuid != null) {
                    World world = Universe.get().getWorld(worldUuid);
                    if (world != null) {
                        ((Executor) world).execute(() -> {
                            if (ref.isValid()) {
                                ref.getPacketHandler().disconnect(
                                        "You are banned for " + finalFormattedDuration + ".\nReason: " + finalReason);
                            }
                        });
                    }
                }
            }

            ctx.sendMessage(
                    Message.raw("Temp-banned " + resolvedName + " for " + formattedDuration).color(Color.GREEN));

        } catch (Exception e) {
            ctx.sendMessage(Message.raw("Error processing tempban: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(null);
    }

}
