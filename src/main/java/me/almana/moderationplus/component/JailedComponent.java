package me.almana.moderationplus.component;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class JailedComponent implements Component<EntityStore> {

    public static ComponentType<EntityStore, JailedComponent> TYPE;

    public static ComponentType<EntityStore, JailedComponent> getComponentType() {
        return TYPE;
    }

    private final Vector3d origin = new Vector3d();
    private double radius;
    private long expiresAt;

    public JailedComponent(Vector3d origin, double radius, long expiresAt) {
        this.origin.assign(origin);
        this.radius = radius;
        this.expiresAt = expiresAt;
    }

    public JailedComponent() {
    }

    public JailedComponent(JailedComponent other) {
        this.origin.assign(other.origin);
        this.radius = other.radius;
        this.expiresAt = other.expiresAt;
    }

    public Vector3d getOrigin() {
        return origin;
    }

    public double getRadius() {
        return radius;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        return new JailedComponent(this);
    }
}
