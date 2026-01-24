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
import java.util.concurrent.CompletableFuture;

public class ChatLockdownCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> modeArg;

    public ChatLockdownCommand(ModerationPlus plugin) {
        super("chatlockdown", "Toggle chat lockdown");
        this.plugin = plugin;
        this.requirePermission("moderation.chatlockdown");
        this.modeArg = withRequiredArg("mode", "on or off", (ArgumentType<String>) ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String mode = null;
        try {
            mode = ctx.get(modeArg);
        } catch (Exception e) {

        }

        boolean newState;
        if (mode == null) {
            newState = !plugin.getChatLocked();
        } else {
            String modeLower = mode.toLowerCase();
            if (modeLower.equals("on")) {
                newState = true;
            } else if (modeLower.equals("off")) {
                newState = false;
            } else {
                ctx.sendMessage(Message.raw("Usage: /chatlockdown [on|off]").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
        }

        if (newState) {
            plugin.setChatLocked(true);
            ctx.sendMessage(Message.raw("Chat has been locked.").color(Color.GREEN));

            String issuerName = (sender instanceof Player) ? sender.getDisplayName() : "Console";
            plugin.notifyStaff(Message.raw("[Staff] " + issuerName + " enabled chat lockdown.").color(Color.RED));

        } else {
            plugin.setChatLocked(false);
            ctx.sendMessage(Message.raw("Chat has been unlocked.").color(Color.GREEN));

            String issuerName = (sender instanceof Player) ? sender.getDisplayName() : "Console";
            plugin.notifyStaff(Message.raw("[Staff] " + issuerName + " disabled chat lockdown.").color(Color.GREEN));
        }

        return CompletableFuture.completedFuture(null);
    }
}
