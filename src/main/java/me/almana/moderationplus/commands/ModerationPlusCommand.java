package me.almana.moderationplus.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import me.almana.moderationplus.ModerationPlus;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;

public class ModerationPlusCommand extends AbstractCommand {

    private final ModerationPlus plugin;

    public ModerationPlusCommand(ModerationPlus plugin) {
        super("moderationplus", "moderation.admin");
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        String input = ctx.getInputString();
        String[] parts = input.trim().split("\\s+");
        
        String[] args;
        if (parts.length > 1) {
            args = new String[parts.length - 1];
            System.arraycopy(parts, 1, args, 0, parts.length - 1);
        } else {
            args = new String[0];
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("lang") && args[1].equalsIgnoreCase("reload")) {
            plugin.getLanguageManager().reload();
            ctx.sendMessage(Message.raw("Language system reloaded.").color(Color.GREEN));
            return CompletableFuture.completedFuture(null);
        }

        ctx.sendMessage(Message.raw("Usage: /moderationplus lang reload").color(Color.RED));
        return CompletableFuture.completedFuture(null);
    }
}
