package me.almana.moderationplus.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.Universe;
import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.api.chat.ChatChannel;
import me.almana.moderationplus.api.event.chat.StaffChatEvent;

public class StaffChatCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> messageArg;
    private final UUID CONSOLE_UUID = UUID.nameUUIDFromBytes("CONSOLE".getBytes());

    public StaffChatCommand(ModerationPlus plugin) {
        super("schat", "Send a staff chat message");
        this.plugin = plugin;
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
        String messageContent = firstWord;

        int firstSpace = fullInput.indexOf(' ');
        if (firstSpace != -1 && firstSpace < fullInput.length() - 1) {
            messageContent = fullInput.substring(firstSpace + 1).trim();
        }

        final String finalMessage = messageContent;

        // 1. Get Channel (Any thread is fine, map lookup)
        ChatChannel channel;
        try {
            channel = plugin.getChatChannelRegistry().getChannel("staff")
                    .orElseThrow(() -> new IllegalStateException("Staff channel not registered!"));
        } catch (Exception e) {
             ctx.sendMessage(Message.raw("Error: Staff channel missing.").color(Color.RED));
             return CompletableFuture.completedFuture(null);
        }

        UUID senderUuid = (sender instanceof Player) ? sender.getUuid() : me.almana.moderationplus.api.ModerationConstants.CONSOLE_UUID;

        // 2. Construct Event (Main Thread for safety if needed, here it's just POJO)
        StaffChatEvent event = new StaffChatEvent(senderUuid, channel, finalMessage);

        // 3. Dispatch Event (Main Thread)
        return CompletableFuture.runAsync(() -> plugin.getEventBus().dispatch(event), com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR)
                .thenAccept(v -> {
                    if (event.isCancelled()) return;

                    // 4. Resolve Recipients if not set
                    if (event.getRecipients() == null || event.getRecipients().isEmpty()) {
                        Set<UUID> resolved = channel.resolveRecipients(senderUuid);
                        event.setRecipients(resolved);
                    }
                    
                    // 5. Deliver
                    plugin.getStaffChatDelivery().deliver(event);
                });
    }
}
