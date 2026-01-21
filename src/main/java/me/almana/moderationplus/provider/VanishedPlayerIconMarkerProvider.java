package me.almana.moderationplus.provider;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.asset.type.gameplay.WorldMapConfig;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.util.PositionUtil;
import me.almana.moderationplus.ModerationPlus;

import javax.annotation.Nonnull;
import java.util.function.Predicate;

public class VanishedPlayerIconMarkerProvider implements WorldMapManager.MarkerProvider {

    private final ModerationPlus plugin;

    public VanishedPlayerIconMarkerProvider(ModerationPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public void update(@Nonnull World world, @Nonnull GameplayConfig gameplayConfig, @Nonnull WorldMapTracker tracker,
            int chunkViewRadius, int playerChunkX, int playerChunkZ) {
        WorldMapConfig worldMapConfig = gameplayConfig.getWorldMapConfig();
        if (!worldMapConfig.isDisplayPlayers())
            return;
        if (!tracker.shouldUpdatePlayerMarkers())
            return;

        Player player = tracker.getPlayer();
        int chunkViewRadiusSq = chunkViewRadius * chunkViewRadius;

        Predicate<PlayerRef> playerMapFilter = tracker.getPlayerMapFilter();

        for (PlayerRef otherPlayer : world.getPlayerRefs()) {
            // Vanilla check: skip self
            if (otherPlayer.getUuid().equals(player.getUuid()))
                continue;

            // Custom check: skip vanished players
            if (plugin.isVanished(otherPlayer.getUuid()))
                continue;

            Transform otherPlayerTransform = otherPlayer.getTransform();
            Vector3d otherPos = otherPlayerTransform.getPosition();

            int otherChunkX = (int) otherPos.x >> 5;
            int otherChunkZ = (int) otherPos.z >> 5;
            int chunkDiffX = otherChunkX - playerChunkX;
            int chunkDiffZ = otherChunkZ - playerChunkZ;

            int chunkDistSq = chunkDiffX * chunkDiffX + chunkDiffZ * chunkDiffZ;
            if (chunkDistSq > chunkViewRadiusSq)
                continue;
            if (playerMapFilter != null && playerMapFilter.test(otherPlayer))
                continue;

            tracker.trySendMarker(chunkViewRadius, playerChunkX, playerChunkZ, otherPos, otherPlayer
                    .getHeadRotation().getYaw(),
                    "Player-" +
                            String.valueOf(otherPlayer.getUuid()),
                    "Player: " + otherPlayer.getUsername(), otherPlayer, (id, name, op) -> new MapMarker(id, name,
                            "Player.png", PositionUtil.toTransformPacket(op.getTransform()), null));
        }

        tracker.resetPlayerMarkersUpdateTimer();
    }
}
