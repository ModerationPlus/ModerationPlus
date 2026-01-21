package me.almana.moderationplus.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.universe.Universe;
import java.awt.Color;
import java.util.concurrent.CompletableFuture;

public class StaffChatCommand extends AbstractCommand {

    private final RequiredArg<String> messageArg;

    public StaffChatCommand(me.almana.moderationplus.ModerationPlus plugin) {
        super("schat", "Send a staff chat message");
        this.requirePermission("moderation.staffchat.send");
        this.messageArg = withRequiredArg("message", "Message to send", (ArgumentType<String>) ArgTypes.STRING);
        setAllowsExtraArguments(true);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String firstWord = ctx.get(messageArg);
        if (firstWord == null || firstWord.trim().isEmpty()) {
            ctx.sendMessage(Message.raw("Usage: /schat <message>").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        String fullInput = ctx.getInputString();
        String message = firstWord;

        int firstSpace = fullInput.indexOf(' ');
        if (firstSpace != -1 && firstSpace < fullInput.length() - 1) {
            message = fullInput.substring(firstSpace + 1).trim();
        }

        String senderName = sender.getDisplayName();
        String text = "[SC] " + senderName + ": " + message;

        Universe.get().getPlayers().forEach(playerRef -> {
            if (playerRef != null && playerRef.isValid()) {
                if (com.hypixel.hytale.server.core.permissions.PermissionsModule.get()
                        .hasPermission(playerRef.getUuid(), "moderation.staffchat.read")) {
                    playerRef.sendMessage(Message.raw(text).color(Color.MAGENTA));
                }
            }
        });

        com.hypixel.hytale.server.core.console.ConsoleSender.INSTANCE
                .sendMessage(Message.raw(text).color(Color.MAGENTA));

        return CompletableFuture.completedFuture(null);
    }
}
