package net.hydra.jojomod.access;

/** Synced, persistent limb-loss state shared by every LivingEntity. */
public interface IStickyFingersDismemberment {
    int roundabout$getStickyFingersDismemberment();

    void roundabout$setStickyFingersDismemberment(int mask);

    int roundabout$getStickyFingersGraftMask();

    void roundabout$setStickyFingersGraftMask(int mask);

    String roundabout$getStickyFingersGraftData();

    void roundabout$setStickyFingersGraftData(String data);

    int roundabout$getStickyFingersAutoUnzipConfig();

    void roundabout$setStickyFingersAutoUnzipConfig(int config);

    boolean roundabout$isInZipperDimension();

    void roundabout$setInZipperDimension(boolean inside);
}
