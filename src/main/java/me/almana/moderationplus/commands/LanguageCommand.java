package me.almana.moderationplus.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import me.almana.moderationplus.ModerationPlus;

import java.awt.Color;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class LanguageCommand extends AbstractCommand {

    private final ModerationPlus plugin;

    public LanguageCommand(ModerationPlus plugin) {
        super("language", "moderation.language");
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!(ctx.sender() instanceof Player)) {
            return CompletableFuture.completedFuture(null);
        }

        Player player = (Player) ctx.sender();
        @SuppressWarnings("removal")
        UUID uuid = player.getUuid();
        
        String input = ctx.getInputString();
        String[] parts = input.trim().split("\\s+");
        // parts[0] is command name "language"
        
        String[] args;
        if (parts.length > 1) {
            args = new String[parts.length - 1];
            System.arraycopy(parts, 1, args, 0, parts.length - 1);
        } else {
            args = new String[0];
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            ctx.sendMessage(Message.raw("Usage: /language [list|set|reset] [locale]").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        if (subCommand.equals("list")) {
            Set<String> locales = plugin.getLanguageManager().getAvailableLocales();
            String list = String.join(", ", locales);
            String msg = plugin.getLanguageManager().translateForPlayer("language.list.header", uuid,
                Map.of("list", list));
            ctx.sendMessage(Message.raw(msg).color(Color.GREEN));
            return CompletableFuture.completedFuture(null);
        }

        if (subCommand.equals("reset")) {
            // Write-through reset
            plugin.getLanguageManager().resetPlayerLocale(uuid);
            
            String msg = plugin.getLanguageManager().translateForPlayer("language.reset.success", uuid);
            ctx.sendMessage(Message.raw(msg).color(Color.GREEN));
            
            return CompletableFuture.completedFuture(null);
        }

        if (subCommand.equals("set")) {
            if (args.length < 2) {
                ctx.sendMessage(Message.raw("Usage: /language set <locale>").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            String inputLocale = args[1].toLowerCase(Locale.ROOT);
            Set<String> locales = plugin.getLanguageManager().getAvailableLocales();

            if (!locales.contains(inputLocale)) {
                String msg = plugin.getLanguageManager().translateForPlayer("language.invalid_locale", uuid,
                    Map.of("locale", inputLocale));
                ctx.sendMessage(Message.raw(msg).color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            // Write-through set
            plugin.getLanguageManager().setPlayerLocale(uuid, inputLocale);

            String msg = plugin.getLanguageManager().translateForPlayer("language.set.success", uuid,
                Map.of("locale", inputLocale));
            ctx.sendMessage(Message.raw(msg).color(Color.GREEN));
            
            return CompletableFuture.completedFuture(null);
        }

        ctx.sendMessage(Message.raw("Usage: /language [list|set|reset] [locale]").color(Color.RED));
        return CompletableFuture.completedFuture(null);
    }
}
