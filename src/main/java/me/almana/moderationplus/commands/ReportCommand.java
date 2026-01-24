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
import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ReportCommand extends AbstractCommand {

    private final RequiredArg<PlayerRef> playerArg;
    private final RequiredArg<String> reasonArg;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 30000; // 30 seconds

    public ReportCommand(ModerationPlus plugin) {
        super("report", "Report a player");

        this.playerArg = withRequiredArg("player", "Player to report", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        this.reasonArg = withRequiredArg("reason", "Reason for report", (ArgumentType<String>) ArgTypes.STRING);
        setAllowsExtraArguments(true);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        if (!(sender instanceof Player)) {
            ctx.sendMessage(Message.raw("Only players can use this command.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        UUID reporterUuid = sender.getUuid();

        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(reporterUuid)) {
            long lastReport = cooldowns.get(reporterUuid);
            if (now - lastReport < COOLDOWN_MS) {
                long secondsLeft = (COOLDOWN_MS - (now - lastReport)) / 1000;
                ctx.sendMessage(Message.raw("Please wait " + secondsLeft + "s before submitting another report.")
                        .color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
        }

        PlayerRef targetRef = ctx.get(playerArg);
        if (targetRef == null || !targetRef.isValid()) {
            ctx.sendMessage(Message.raw("That player is not online.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        if (targetRef.getUuid().equals(reporterUuid)) {
            ctx.sendMessage(Message.raw("You cannot report yourself.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        String firstWord = ctx.get(reasonArg);
        if (firstWord == null || firstWord.trim().isEmpty()) {
            ctx.sendMessage(Message.raw("Usage: /report <player> <reason>").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        String reason = firstWord;
        String fullInput = ctx.getInputString();

        String[] args = fullInput.split("\\s+");
        if (args.length > 2) {

            int start = fullInput.indexOf(args[2]);
            if (start != -1) {
                reason = fullInput.substring(start).trim();
            }
        }

        cooldowns.put(reporterUuid, now);

        String reporterName = sender.getDisplayName();
        String targetName = targetRef.getUsername();
        String reportMsg = "[REPORT] " + reporterName + " -> " + targetName + ": " + reason;

        ctx.sendMessage(Message.raw("Your report has been submitted.").color(Color.GREEN));

        Universe.get().getPlayers().forEach(staff -> {
            if (staff != null && staff.isValid()) {
                if (com.hypixel.hytale.server.core.permissions.PermissionsModule.get()
                        .hasPermission(staff.getUuid(), "moderation.report.receive")) {
                    staff.sendMessage(Message.raw(reportMsg).color(Color.RED));
                }
            }
        });

        com.hypixel.hytale.server.core.console.ConsoleSender.INSTANCE
                .sendMessage(Message.raw(reportMsg).color(Color.RED));

        return CompletableFuture.completedFuture(null);
    }
}
