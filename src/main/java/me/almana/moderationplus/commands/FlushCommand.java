package me.almana.moderationplus.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;
import me.almana.moderationplus.ModerationPlus;

public class FlushCommand extends AbstractCommand {

    private final ModerationPlus plugin;

    public FlushCommand(ModerationPlus plugin) {
        super("flushdb", "Manually flush the database");
        this.plugin = plugin;
        this.requirePermission("moderation.flush");
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {

        ctx.sendMessage(Message.raw("Flushing database...").color(Color.YELLOW));

        return CompletableFuture.runAsync(() -> {
            try {
                plugin.getStorageManager().flush();
                ctx.sendMessage(Message.raw("Database flushed successfully.").color(Color.GREEN));
            } catch (Exception e) {
                ctx.sendMessage(Message.raw("Error flushing database: " + e.getMessage()).color(Color.RED));
                e.printStackTrace();
            }
        });
    }
}
