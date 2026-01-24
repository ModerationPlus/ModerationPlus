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
import com.hypixel.hytale.server.core.modules.accesscontrol.ban.InfiniteBan;
import com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleBanProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.concurrent.Executor;

import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.storage.Punishment;
import me.almana.moderationplus.storage.StorageManager.PlayerData;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BanCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;

    public BanCommand(ModerationPlus plugin) {
        super("ban", "Ban a player permanently");
        this.plugin = plugin;
        this.requirePermission("moderation.ban");
        this.playerArg = withRequiredArg("player", "Player to ban", (ArgumentType<String>) ArgTypes.STRING);

        setAllowsExtraArguments(true);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String targetName = ctx.get(playerArg);

        String fullInput = ctx.getInputString();
        String reason = "Banned by an operator.";
        String cmdPrefix = "ban " + targetName;
        int idx = fullInput.toLowerCase().indexOf(cmdPrefix.toLowerCase());
        if (idx != -1 && fullInput.length() > idx + cmdPrefix.length()) {
            reason = fullInput.substring(idx + cmdPrefix.length()).trim();
        } else {

            reason = "Banned by an operator.";
        }
        if (reason == null || reason.isEmpty())
            reason = "Banned by an operator.";

        String issuerUuid = (sender instanceof Player) ? sender.getUuid().toString() : "CONSOLE";
        UUID targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);

        if (targetUuid == null) {

            ctx.sendMessage(Message.raw("Player '" + targetName + "' not found in database.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        String resolvedName = targetName;
        PlayerRef ref = Universe.get().getPlayer(targetUuid);
        boolean isOnline = false;

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
                InfiniteBan nativeBan = new InfiniteBan(targetUuid, issuerId, Instant.now(), reason);
                banProvider.modify(bans -> {
                    bans.put(targetUuid, nativeBan);
                    return true;
                });
            } else {
                ctx.sendMessage(Message.raw(
                        "Error: Native Ban Provider not found. Proceeding with DB only (Will not block login!).")
                        .color(Color.RED));
            }

            List<Punishment> activeBans = plugin.getStorageManager().getActivePunishmentsByType(playerData.id(), "BAN");
            if (activeBans.isEmpty()) {
                Punishment ban = new Punishment(0, playerData.id(), "BAN", issuerUuid, reason,
                        System.currentTimeMillis(), 0, true, "{}");
                plugin.getStorageManager().createPunishment(ban);
            }

            String staffMsg = "[Staff] " + sender.getDisplayName() + " banned " + resolvedName + " (" + reason + ")";
            plugin.notifyStaff(Message.raw(staffMsg).color(Color.GREEN));

            if (isOnline && ref != null && ref.isValid()) {
                final String finalReason = reason;
                UUID worldUuid = ref.getWorldUuid();
                if (worldUuid != null) {
                    World world = Universe.get().getWorld(worldUuid);
                    if (world != null) {
                        ((Executor) world).execute(() -> {
                            if (ref.isValid()) {
                                ref.getPacketHandler()
                                        .disconnect("You are permanently banned.\nReason: " + finalReason);
                            }
                        });
                    }
                }
            }

            ctx.sendMessage(Message.raw("Banned " + resolvedName).color(Color.GREEN));

        } catch (Exception e) {
            ctx.sendMessage(Message.raw("Error processing ban: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(null);
    }
}
