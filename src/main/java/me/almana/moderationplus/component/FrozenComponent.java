package me.almana.moderationplus.component;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class FrozenComponent implements Component<EntityStore> {



    public static ComponentType<EntityStore, FrozenComponent> TYPE;

    public static ComponentType<EntityStore, FrozenComponent> getComponentType() {
        return TYPE;
    }

    private final Vector3d origin = new Vector3d();

    public FrozenComponent(Vector3d origin) {
        this.origin.assign(origin);
    }


    public FrozenComponent() {
    }


    public FrozenComponent(FrozenComponent other) {
        this.origin.assign(other.origin);
    }

    public Vector3d getOrigin() {
        return origin;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        return new FrozenComponent(this);
    }
}
