package net.blueva.arcade.modules.capture_the_wool.support.wool;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class WoolDefinition {

    private final String ownerTeamId;
    private final Set<String> captureTeamIds;
    private final String woolId;
    private final Location spawnLocation;
    private final Location captureLocation;
    private final Material material;

    public WoolDefinition(String ownerTeamId,
                          Set<String> captureTeamIds,
                          String woolId,
                          Location spawnLocation,
                          Location captureLocation,
                          Material material) {
        this.ownerTeamId = ownerTeamId;
        this.captureTeamIds = captureTeamIds == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(captureTeamIds));
        this.woolId = woolId;
        this.spawnLocation = spawnLocation;
        this.captureLocation = captureLocation;
        this.material = material;
    }

    public String getTeamId() {
        return ownerTeamId;
    }

    public String getOwnerTeamId() {
        return ownerTeamId;
    }

    public String getCaptureTeamId() {
        if (captureTeamIds.isEmpty()) {
            return "";
        }
        return captureTeamIds.iterator().next();
    }

    public Set<String> getCaptureTeamIds() {
        return captureTeamIds;
    }

    public String getWoolId() {
        return woolId;
    }

    public String getKey() {
        return woolId.toLowerCase();
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public Location getCaptureLocation() {
        return captureLocation;
    }

    public Material getMaterial() {
        return material;
    }
}
