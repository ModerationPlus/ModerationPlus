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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.NameMatching;
import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.storage.StaffNote;
import me.almana.moderationplus.storage.StorageManager.PlayerData;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class NoteCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;
    private final RequiredArg<String> messageArg;

    public NoteCommand(ModerationPlus plugin) {
        super("note", "Add a staff note to a player");
        this.plugin = plugin;
        this.requirePermission("moderation.note");
        this.playerArg = withRequiredArg("player", "Player name", (ArgumentType<String>) ArgTypes.STRING);
        this.messageArg = withRequiredArg("message", "Note message", (ArgumentType<String>) ArgTypes.STRING);
        setAllowsExtraArguments(true);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String targetName = ctx.get(playerArg);


        String fullInput = ctx.getInputString();
        String message = "Note by staff";
        String cmdPrefix = "note " + targetName;
        int idx = fullInput.toLowerCase().indexOf(cmdPrefix.toLowerCase());
        if (idx != -1 && fullInput.length() > idx + cmdPrefix.length()) {
            message = fullInput.substring(idx + cmdPrefix.length()).trim();
        } else {
            message = ctx.get(messageArg);
        }
        if (message == null || message.isEmpty()) {
            ctx.sendMessage(Message.raw("Please provide a message for the note.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        String issuerUuid = (sender instanceof Player) ? sender.getUuid().toString() : "CONSOLE";
        String issuerName = (sender instanceof Player) ? sender.getDisplayName() : "Console";

        UUID targetUuid = null;
        String resolvedName = targetName;

        PlayerRef ref = Universe.get().getPlayer(targetName, NameMatching.EXACT);
        if (ref != null) {
            targetUuid = ref.getUuid();
            resolvedName = ref.getUsername();
        } else {
            targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);
        }

        if (targetUuid == null) {
            ctx.sendMessage(Message.raw("Cannot resolve UUID for " + targetName).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        try {
            PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(targetUuid, resolvedName);


            StaffNote note = new StaffNote(0, playerData.id(), issuerUuid, message, System.currentTimeMillis());
            plugin.getStorageManager().createStaffNote(note);


            String staffMsg = "[Staff] " + issuerName + " added a note to " + resolvedName + " (" + message + ")";
            plugin.notifyStaff(Message.raw(staffMsg).color(Color.YELLOW));

            ctx.sendMessage(Message.raw("Note added to " + resolvedName).color(Color.GREEN));

        } catch (Exception e) {
            ctx.sendMessage(Message.raw("Error adding note: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(null);
    }
}
