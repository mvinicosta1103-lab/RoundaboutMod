package net.hydra.jojomod.access;

/** Synced, persistent limb-loss state shared by every LivingEntity. */
public interface IStickyFingersDismemberment {
    int roundabout$getStickyFingersDismemberment();

    void roundabout$setStickyFingersDismemberment(int mask);

    boolean roundabout$isInZipperDimension();

    void roundabout$setInZipperDimension(boolean inside);
}
