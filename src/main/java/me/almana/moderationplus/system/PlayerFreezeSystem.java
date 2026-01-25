package me.almana.moderationplus.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.almana.moderationplus.component.FrozenComponent;
import me.almana.moderationplus.component.JailedComponent;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.List;

public class PlayerFreezeSystem extends EntityTickingSystem<EntityStore> {

    private final me.almana.moderationplus.ModerationPlus plugin;
    private final Query<EntityStore> query;

    public PlayerFreezeSystem(me.almana.moderationplus.ModerationPlus plugin) {
        this.plugin = plugin;
        this.query = Query.and(
                Player.getComponentType(),
                PlayerInput.getComponentType(),
                TransformComponent.getComponentType());
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {


        FrozenComponent frozen = archetypeChunk.getComponent(index, FrozenComponent.getComponentType());
        JailedComponent jailed = archetypeChunk.getComponent(index, JailedComponent.getComponentType());

        if (frozen == null && jailed == null) {
            return;
        }

        PlayerInput playerInput = archetypeChunk.getComponent(index, PlayerInput.getComponentType());
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());

        if (playerInput == null || transform == null)
            return;

        if (jailed != null && jailed.getExpiresAt() > 0 && System.currentTimeMillis() > jailed.getExpiresAt()) {
             commandBuffer.removeComponent(archetypeChunk.getReferenceTo(index), JailedComponent.getComponentType());
             if (frozen != null) {
                 commandBuffer.removeComponent(archetypeChunk.getReferenceTo(index), FrozenComponent.getComponentType());
             }
             
             Player player = archetypeChunk.getComponent(index, Player.getComponentType());
             if (player != null) {
                 me.almana.moderationplus.service.ExecutionContext ctx = me.almana.moderationplus.service.ExecutionContext.console();
                 plugin.getModerationService().unjail(player.getUuid(), player.getDisplayName(), ctx);
             }
             return;
        }

        if (frozen != null) {
            handleFreeze(frozen, playerInput, transform, archetypeChunk, index, commandBuffer);
        } else {
            handleJail(jailed, transform, archetypeChunk, index, commandBuffer);
        }
    }

    private void handleFreeze(FrozenComponent frozen, PlayerInput playerInput, TransformComponent transform,
            ArchetypeChunk<EntityStore> chunk, int index, CommandBuffer<EntityStore> commandBuffer) {

        List<PlayerInput.InputUpdate> queue = playerInput.getMovementUpdateQueue();
        boolean cleared = false;

        Iterator<PlayerInput.InputUpdate> it = queue.iterator();
        while (it.hasNext()) {
            PlayerInput.InputUpdate update = it.next();
            if (update instanceof PlayerInput.AbsoluteMovement || update instanceof PlayerInput.RelativeMovement) {
                it.remove();
                cleared = true;
            }
        }


        Vector3d currentPos = transform.getPosition();
        Vector3d origin = frozen.getOrigin();

        double distSq = currentPos.distanceSquaredTo(origin);


        if (cleared || distSq > 0.01) {
            scheduleTeleport(chunk, index, origin, commandBuffer);
        }
    }

    private void handleJail(JailedComponent jailed, TransformComponent transform, ArchetypeChunk<EntityStore> chunk,
            int index, CommandBuffer<EntityStore> commandBuffer) {

        if (jailed.getExpiresAt() > 0 && System.currentTimeMillis() > jailed.getExpiresAt()) {
            commandBuffer.removeComponent(chunk.getReferenceTo(index), JailedComponent.getComponentType());
            return;
        }

        Vector3d currentPos = transform.getPosition();
        Vector3d origin = jailed.getOrigin();
        double radius = jailed.getRadius();

        double distSq = currentPos.distanceSquaredTo(origin);
        if (distSq > (radius * radius)) {

            scheduleTeleport(chunk, index, origin, commandBuffer);
        }
    }

    private void scheduleTeleport(ArchetypeChunk<EntityStore> chunk, int index, Vector3d target,
            CommandBuffer<EntityStore> commandBuffer) {

        if (chunk.getComponent(index, Teleport.getComponentType()) != null) {
            return;
        }


        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        Vector3f rotation = transform != null ? transform.getRotation() : new Vector3f(0, 0, 0);

        Teleport teleport = new Teleport(target, rotation);


        commandBuffer.addComponent(chunk.getReferenceTo(index), Teleport.getComponentType(), teleport);
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }
}
