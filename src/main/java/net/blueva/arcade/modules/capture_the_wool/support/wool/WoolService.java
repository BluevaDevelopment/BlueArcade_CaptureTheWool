package net.blueva.arcade.modules.capture_the_wool.support.wool;

import net.blueva.arcade.api.arena.ArenaAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import net.blueva.arcade.api.ui.Hologram;
import net.blueva.arcade.api.ui.HologramAPI;
import net.blueva.arcade.modules.capture_the_wool.state.ArenaState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.Set;
import java.util.UUID;

public class WoolService {

    private static final int WOOL_ID_SCAN_LIMIT = 128;

    private final ModuleConfigAPI moduleConfig;

    public WoolService(ModuleConfigAPI moduleConfig) {
        this.moduleConfig = moduleConfig;
    }

    public List<WoolDefinition> loadWoolDefinitions(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        List<WoolDefinition> definitions = new ArrayList<>();
        if (context == null || context.getDataAccess() == null) {
            return definitions;
        }

        World runtimeWorld = resolveRuntimeWorld(context);
        Material defaultMaterial = resolveDefaultMaterial();

        String woolBasePath = resolveWoolBasePath(context);

        Set<String> woolIds = resolveWoolIds(context, woolBasePath);
        if (woolIds.isEmpty()) {
            return definitions;
        }

        for (String woolId : woolIds) {

            String basePath = woolBasePath + ".wools." + woolId;
            String ownerTeamId = context.getDataAccess().getGameData(basePath + ".owner_team", String.class);
            if (ownerTeamId == null || ownerTeamId.isBlank()) {
                continue;
            }
            ownerTeamId = ownerTeamId.toLowerCase(Locale.ROOT);

            Set<String> captureTeamIds = resolveCaptureTeams(context, basePath, ownerTeamId);

            Location spawnLoc = resolveGameLocation(context, basePath + ".spawn", runtimeWorld);
            Location captureLoc = resolveGameLocation(context, basePath + ".capture", runtimeWorld);
            if (spawnLoc == null) {
                continue;
            }

            String materialName = context.getDataAccess().getGameData(basePath + ".material", String.class);
            Material material = defaultMaterial;
            if (materialName != null && !materialName.isBlank()) {
                try {
                    material = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
            }

            definitions.add(new WoolDefinition(ownerTeamId, captureTeamIds, woolId, spawnLoc, captureLoc, material));
        }

        return definitions;
    }

    public void spawnAllWools(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                              ArenaState state) {
        if (state == null) {
            return;
        }

        for (WoolDefinition def : state.getWoolDefinitions()) {
            WoolState woolState = state.getWoolState(def.getKey());
            if (woolState == WoolState.CARRIED) {
                UUID carrier = state.getWoolCarrier(def.getKey());
                if (carrier == null) {
                    state.setWoolState(def.getKey(), WoolState.SPAWNED);
                    woolState = WoolState.SPAWNED;
                }
            }

            if (woolState == WoolState.SPAWNED) {
                keepWoolSpawnBlockAlive(context, state, def);
                continue;
            }

            clearSpawnWoolVisuals(state, def.getKey());
        }
    }

    private void keepWoolSpawnBlockAlive(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                         ArenaState state,
                                         WoolDefinition def) {
        Location spawnLoc = def.getSpawnLocation();
        if (spawnLoc == null) {
            return;
        }

        spawnLoc = resolveLocationWithRuntimeWorld(context, spawnLoc);
        if (spawnLoc.getWorld() == null) {
            return;
        }

        Block spawnBlock = spawnLoc.getBlock();
        if (spawnBlock.getType() != def.getMaterial()) {
            spawnBlock.setType(def.getMaterial());
        }

        Hologram<Location> trackedHologram = state.getWoolHologram(def.getKey());
        if (!isHologramAlive(trackedHologram)
                || !isHologramAtExpectedLocation(trackedHologram, spawnLoc)) {
            state.removeWoolHologram(def.getKey());
            spawnBreakInstructionHologram(context, state, def);
        }
    }

    private boolean isHologramAlive(Hologram<Location> hologram) {
        if (hologram == null || hologram.getId() == null) {
            return false;
        }
        Entity entity = Bukkit.getEntity(hologram.getId());
        return entity != null && entity.isValid() && !entity.isDead();
    }

    private boolean isHologramAtExpectedLocation(Hologram<Location> hologram, Location expectedWoolBlockLocation) {
        if (hologram == null || expectedWoolBlockLocation == null) {
            return false;
        }

        Location hologramLocation = hologram.getLocation();
        if (hologramLocation == null || hologramLocation.getWorld() == null || expectedWoolBlockLocation.getWorld() == null) {
            return false;
        }

        if (!hologramLocation.getWorld().equals(expectedWoolBlockLocation.getWorld())) {
            return false;
        }

        return hologramLocation.getBlockX() == expectedWoolBlockLocation.getBlockX()
                && hologramLocation.getBlockZ() == expectedWoolBlockLocation.getBlockZ();
    }

    private void spawnBreakInstructionHologram(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                               ArenaState state,
                                               WoolDefinition def) {
        HologramAPI<Location> hologramAPI = context.getHologramAPI();
        if (hologramAPI == null) {
            return;
        }

        String line1 = moduleConfig.getStringFrom("language.yml", "messages.wool.break_hint.title");
        String line2 = moduleConfig.getStringFrom("language.yml", "messages.wool.break_hint.subtitle");
        List<String> lines = new ArrayList<>();
        lines.add(line1 == null ? "<yellow><bold>BREAK THIS WOOL</bold></yellow>" : line1);
        lines.add(line2 == null ? "<gray>Break it to carry it.</gray>" : line2);

        Location baseSpawnLoc = def.getSpawnLocation();
        if (baseSpawnLoc == null) {
            return;
        }

        baseSpawnLoc = resolveLocationWithRuntimeWorld(context, baseSpawnLoc);
        if (baseSpawnLoc.getWorld() == null) {
            return;
        }

        Location hologramLoc = baseSpawnLoc.clone().add(0.5D, 1.8D, 0.5D);
        Hologram<Location> hologram = hologramAPI.spawn(state.getArenaId(), hologramLoc, lines);
        if (hologram != null) {
            state.setWoolHologram(def.getKey(), hologram);
        }
    }

    private void clearSpawnWoolVisuals(ArenaState state, String woolKey) {
        state.removeWoolHologram(woolKey);
    }


    private void spawnCarrierHologram(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                      ArenaState state,
                                      WoolDefinition def,
                                      Player carrier) {
        HologramAPI<Location> hologramAPI = context.getHologramAPI();
        if (hologramAPI == null || carrier == null) {
            return;
        }

        removeCarrierHologram(state, def.getKey());

        String carrierLine = moduleConfig.getStringFrom("language.yml", "messages.wool.carrier_hologram");
        if (carrierLine == null) {
            String colorTag = resolveWoolColor(def.getMaterial());
            carrierLine = colorTag + "⬛ CARRYING WOOL ⬛";
        }

        List<String> lines = new ArrayList<>();
        lines.add(carrierLine);

        Location hologramLoc = carrier.getLocation().clone().add(0.0D, 2.5D, 0.0D);
        Hologram<Location> hologram = hologramAPI.spawn(state.getArenaId(), hologramLoc, lines);
        if (hologram != null) {
            state.setCarrierHologram(def.getKey(), hologram);
        }
    }

    private void removeCarrierHologram(ArenaState state, String woolKey) {
        state.removeCarrierHologram(woolKey);
    }

    private void removeCarrierHologramsForPlayer(ArenaState state, UUID playerId) {
        if (state == null || playerId == null) {
            return;
        }
        for (WoolDefinition def : state.getWoolDefinitions()) {
            UUID carrier = state.getWoolCarrier(def.getKey());
            if (carrier != null && carrier.equals(playerId)) {
                removeCarrierHologram(state, def.getKey());
            }
        }
    }

    public void updateCarrierHolograms(ArenaState state) {
        if (state == null) {
            return;
        }
        for (WoolDefinition def : state.getWoolDefinitions()) {
            WoolState woolState = state.getWoolState(def.getKey());
            if (woolState != WoolState.CARRIED) {
                continue;
            }
            UUID carrierId = state.getWoolCarrier(def.getKey());
            if (carrierId == null) {
                continue;
            }
            Hologram<Location> hologram = state.getCarrierHologram(def.getKey());
            if (hologram == null) {
                continue;
            }
            Player carrier = Bukkit.getPlayer(carrierId);
            if (carrier == null || !carrier.isOnline()) {
                continue;
            }
            hologram.teleport(carrier.getLocation().clone().add(0.0D, 2.5D, 0.0D));
        }
    }

    public boolean handleWoolPickup(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    ArenaState state,
                                    Player player,
                                    Block block) {
        if (state == null || player == null || block == null) {
            return false;
        }

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return false;
        }

        TeamInfo<Player, Material> playerTeam = teamsAPI.getTeam(player);
        if (playerTeam == null) {
            return false;
        }

        String playerTeamId = playerTeam.getId().toLowerCase(Locale.ROOT);
        Location blockLoc = block.getLocation();

        for (WoolDefinition def : state.getWoolDefinitions()) {
            if (!locationsMatch(def.getSpawnLocation(), blockLoc)) {
                continue;
            }

            WoolState woolState = state.getWoolState(def.getKey());
            if (woolState != WoolState.SPAWNED) {
                return false;
            }

            if (!canTeamStealWool(teamsAPI, def, playerTeamId)) {
                return false;
            }

            block.setType(Material.AIR);

            clearSpawnWoolVisuals(state, def.getKey());

            player.getInventory().addItem(createNamedWoolItem(def));

            state.setWoolState(def.getKey(), WoolState.CARRIED);
            state.setWoolCarrier(def.getKey(), player.getUniqueId());

            spawnCarrierHologram(context, state, def, player);

            String message = moduleConfig.getStringFrom("language.yml", "messages.wool.picked_up");
            if (message != null) {
                message = message.replace("{player}", player.getName())
                        .replace("{team}", resolveTeamReference(teamsAPI, def.getTeamId()))
                        .replace("{wool}", def.getWoolId());
                for (Player p : context.getPlayers()) {
                    context.getMessagesAPI().sendRaw(p, message);
                }
            }

            for (Player p : context.getPlayers()) {
                context.getSoundsAPI().play(p, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            }

            return true;
        }

        return false;
    }

    public String resolveWoolPickupBlockedMessage(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                                  ArenaState state,
                                                  Player player,
                                                  Block block) {
        if (state == null || player == null || block == null || context == null) {
            return null;
        }

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return null;
        }

        TeamInfo<Player, Material> playerTeam = teamsAPI.getTeam(player);
        if (playerTeam == null) {
            return null;
        }

        String playerTeamId = playerTeam.getId().toLowerCase(Locale.ROOT);
        Location blockLoc = block.getLocation();

        for (WoolDefinition def : state.getWoolDefinitions()) {
            if (!locationsMatch(def.getSpawnLocation(), blockLoc)) {
                continue;
            }

            if (state.getWoolState(def.getKey()) != WoolState.SPAWNED) {
                return null;
            }

            if (def.getOwnerTeamId().equalsIgnoreCase(playerTeamId)) {
                String ownTeamMessage = moduleConfig.getStringFrom("language.yml", "messages.wool.cannot_break_own");
                if (ownTeamMessage == null || ownTeamMessage.isBlank()) {
                    ownTeamMessage = "<red>This wool belongs to your team. You must defend it.</red>";
                }
                return ownTeamMessage
                        .replace("{team}", resolveTeamReference(teamsAPI, def.getOwnerTeamId()))
                        .replace("{wool}", def.getWoolId());
            }

            if (!canTeamStealWool(teamsAPI, def, playerTeamId)) {
                String blockedMessage = moduleConfig.getStringFrom("language.yml", "messages.wool.cannot_break");
                if (blockedMessage == null || blockedMessage.isBlank()) {
                    blockedMessage = "<red>You cannot break this wool.</red>";
                }
                return blockedMessage
                        .replace("{team}", resolveTeamReference(teamsAPI, def.getOwnerTeamId()))
                        .replace("{wool}", def.getWoolId());
            }

            return null;
        }

        return null;
    }


    private boolean canTeamStealWool(TeamsAPI<Player, Material> teamsAPI, WoolDefinition def, String playerTeamId) {
        if (def == null || playerTeamId == null || playerTeamId.isBlank()) {
            return false;
        }

        String resolvedPlayerTeamId = resolveTeamReference(teamsAPI, playerTeamId);
        String resolvedOwnerTeamId = resolveTeamReference(teamsAPI, def.getOwnerTeamId());
        if (resolvedOwnerTeamId.equalsIgnoreCase(resolvedPlayerTeamId)) {
            return false;
        }

        Set<String> captureTeams = def.getCaptureTeamIds();
        if (captureTeams == null || captureTeams.isEmpty()) {
            return true;
        }

        for (String captureTeam : captureTeams) {
            String resolvedCaptureTeamId = resolveTeamReference(teamsAPI, captureTeam);
            if (!resolvedCaptureTeamId.isBlank() && resolvedCaptureTeamId.equalsIgnoreCase(resolvedPlayerTeamId)) {
                return true;
            }
        }

        return false;
    }

    private String resolveTeamReference(TeamsAPI<Player, Material> teamsAPI, String teamReference) {
        if (teamReference == null) {
            return "";
        }

        String normalizedReference = teamReference.trim().toLowerCase(Locale.ROOT);
        if (normalizedReference.isBlank()) {
            return "";
        }

        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return normalizedReference;
        }

        List<TeamInfo<Player, Material>> teams = teamsAPI.getTeams();
        for (TeamInfo<Player, Material> team : teams) {
            if (team != null && team.getId() != null && team.getId().equalsIgnoreCase(normalizedReference)) {
                return team.getId().toLowerCase(Locale.ROOT);
            }
        }

        if (isNumericId(normalizedReference)) {
            int teamIndex = Integer.parseInt(normalizedReference) - 1;
            if (teamIndex >= 0 && teamIndex < teams.size()) {
                TeamInfo<Player, Material> indexedTeam = teams.get(teamIndex);
                if (indexedTeam != null && indexedTeam.getId() != null) {
                    return indexedTeam.getId().toLowerCase(Locale.ROOT);
                }
            }
        }

        return normalizedReference;
    }

    private boolean isNumericId(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        for (int i = 0; i < raw.length(); i++) {
            if (!Character.isDigit(raw.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean handleWoolCapture(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                     ArenaState state,
                                     Player player,
                                     Block placedBlock,
                                     Material placedMaterial) {
        if (state == null || player == null || placedBlock == null) {
            return false;
        }

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return false;
        }

        TeamInfo<Player, Material> playerTeam = teamsAPI.getTeam(player);
        if (playerTeam == null) {
            return false;
        }

        String playerTeamId = playerTeam.getId().toLowerCase(Locale.ROOT);
        Location placeLoc = placedBlock.getLocation();

        for (WoolDefinition def : state.getWoolDefinitions()) {
            if (!locationsMatch(def.getCaptureLocation(), placeLoc)) {
                continue;
            }

            WoolState woolState = state.getWoolState(def.getKey());
            if (woolState == WoolState.CAPTURED) {
                return false;
            }

            UUID carrier = state.getWoolCarrier(def.getKey());
            if (woolState != WoolState.CARRIED || carrier == null || !carrier.equals(player.getUniqueId())) {
                return false;
            }

            if (!canTeamStealWool(teamsAPI, def, playerTeamId)) {
                return false;
            }

            if (placedMaterial != def.getMaterial()) {
                return false;
            }

            state.setWoolState(def.getKey(), WoolState.CAPTURED);
            state.removeWoolCarrier(def.getKey());
            state.setWoolCapturer(def.getKey(), player.getUniqueId());

            removeCarrierHologram(state, def.getKey());

            String message = moduleConfig.getStringFrom("language.yml", "messages.wool.captured");
            if (message != null) {
                message = message.replace("{player}", player.getName())
                        .replace("{team}", resolveTeamReference(teamsAPI, def.getTeamId()))
                        .replace("{wool}", def.getWoolId());
                for (Player p : context.getPlayers()) {
                    context.getMessagesAPI().sendRaw(p, message);
                }
            }

            for (Player p : context.getPlayers()) {
                context.getSoundsAPI().play(p, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }

            return true;
        }

        return false;
    }

    public void handlePlayerDeath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                  ArenaState state,
                                  Player player) {
        if (state == null || player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();

        removeCarrierHologramsForPlayer(state, playerId);

        boolean lostWool = false;

        for (WoolDefinition def : state.getWoolDefinitions()) {
            UUID carrier = state.getWoolCarrier(def.getKey());
            if (carrier != null && carrier.equals(playerId)) {
                state.setWoolState(def.getKey(), WoolState.SPAWNED);
                state.removeWoolCarrier(def.getKey());
                lostWool = true;

                keepWoolSpawnBlockAlive(context, state, def);
            }
        }

        removeWoolItems(player, state);

        if (lostWool && context != null) {
            String message = moduleConfig.getStringFrom("language.yml", "messages.wool.dropped");
            if (message != null) {
                message = message.replace("{player}", player.getName());
                for (Player online : context.getPlayers()) {
                    context.getMessagesAPI().sendRaw(online, message);
                }
            }

            for (Player online : context.getPlayers()) {
                context.getSoundsAPI().play(online, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
            }
        }
    }

    public boolean isPlayerCarryingWool(ArenaState state, Player player) {
        if (state == null || player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        for (WoolDefinition def : state.getWoolDefinitions()) {
            UUID carrier = state.getWoolCarrier(def.getKey());
            if (carrier != null && carrier.equals(playerId)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCarriedWoolMaterial(ArenaState state, Player player, Material material) {
        if (state == null || player == null || material == null) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        for (WoolDefinition def : state.getWoolDefinitions()) {
            UUID carrier = state.getWoolCarrier(def.getKey());
            if (carrier != null && carrier.equals(playerId) && def.getMaterial() == material) {
                return true;
            }
        }
        return false;
    }

    public boolean isObjectiveWoolMaterial(ArenaState state, Material material) {
        if (state == null || material == null) {
            return false;
        }

        Set<Material> objectiveMaterials = new HashSet<>();
        for (WoolDefinition def : state.getWoolDefinitions()) {
            objectiveMaterials.add(def.getMaterial());
        }
        return objectiveMaterials.contains(material);
    }

    public void clearTrackedWoolItems(ArenaState state) {
        if (state == null) {
            return;
        }

        for (Map.Entry<String, Hologram<Location>> entry : state.getWoolHolograms().entrySet()) {
            if (entry.getValue() != null) {
                entry.getValue().delete();
            }
            state.setWoolHologram(entry.getKey(), null);
        }

        for (Map.Entry<String, Hologram<Location>> entry : state.getCarrierHolograms().entrySet()) {
            if (entry.getValue() != null) {
                entry.getValue().delete();
            }
            state.setCarrierHologram(entry.getKey(), null);
        }
    }

    public boolean isCaptureLocation(ArenaState state, Location location) {
        if (state == null || location == null) {
            return false;
        }

        for (WoolDefinition def : state.getWoolDefinitions()) {
            if (locationsMatch(def.getCaptureLocation(), location)) {
                return true;
            }
        }
        return false;
    }

    public boolean isWoolSpawnLocation(ArenaState state, Location location) {
        if (state == null || location == null) {
            return false;
        }

        for (WoolDefinition def : state.getWoolDefinitions()) {
            if (locationsMatch(def.getSpawnLocation(), location)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasTeamCapturedAllWools(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                         ArenaState state,
                                         String teamId) {
        if (state == null || teamId == null) {
            return false;
        }

        TeamsAPI<Player, Material> teamsAPI = context != null ? context.getTeamsAPI() : null;
        String normalizedTeamId = resolveTeamReference(teamsAPI, teamId);
        boolean hasWools = false;

        for (WoolDefinition def : state.getWoolDefinitions()) {
            if (!isTeamAssignedToCaptureWool(teamsAPI, def, normalizedTeamId)) {
                continue;
            }
            hasWools = true;
            if (state.getWoolState(def.getKey()) != WoolState.CAPTURED) {
                return false;
            }
        }

        return hasWools;
    }


    public int getTeamObjectiveCount(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                     ArenaState state,
                                     String teamId) {
        if (state == null || teamId == null) {
            return 0;
        }
        TeamsAPI<Player, Material> teamsAPI = context != null ? context.getTeamsAPI() : null;
        String normalizedTeamId = resolveTeamReference(teamsAPI, teamId);
        int count = 0;
        for (WoolDefinition def : state.getWoolDefinitions()) {
            if (isTeamAssignedToCaptureWool(teamsAPI, def, normalizedTeamId)) {
                count++;
            }
        }
        return count;
    }

    public int getTeamCapturedObjectives(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                         ArenaState state,
                                         String teamId) {
        if (state == null || teamId == null) {
            return 0;
        }
        TeamsAPI<Player, Material> teamsAPI = context != null ? context.getTeamsAPI() : null;
        String normalizedTeamId = resolveTeamReference(teamsAPI, teamId);
        int count = 0;
        for (WoolDefinition def : state.getWoolDefinitions()) {
            if (!isTeamAssignedToCaptureWool(teamsAPI, def, normalizedTeamId)) {
                continue;
            }
            if (state.getWoolState(def.getKey()) == WoolState.CAPTURED) {
                count++;
            }
        }
        return count;
    }

    private boolean isTeamAssignedToCaptureWool(TeamsAPI<Player, Material> teamsAPI,
                                                 WoolDefinition def,
                                                 String normalizedTeamId) {
        if (def == null || normalizedTeamId == null || normalizedTeamId.isBlank()) {
            return false;
        }
        Set<String> captureTeams = def.getCaptureTeamIds();
        if (captureTeams == null || captureTeams.isEmpty()) {
            return false;
        }
        for (String captureTeam : captureTeams) {
            if (resolveTeamReference(teamsAPI, captureTeam).equalsIgnoreCase(normalizedTeamId)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> resolveCaptureTeams(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                            String basePath,
                                            String ownerTeamId) {
        Set<String> captureTeamIds = new LinkedHashSet<>();
        String captureTeamsRaw = context.getDataAccess().getGameData(basePath + ".capture_teams", String.class);
        if (captureTeamsRaw != null && !captureTeamsRaw.isBlank()) {
            for (String entry : captureTeamsRaw.split(",")) {
                String value = entry.trim().toLowerCase(Locale.ROOT);
                if (!value.isBlank() && !value.equals(ownerTeamId)) {
                    captureTeamIds.add(value);
                }
            }
        }

        String singleCaptureTeam = context.getDataAccess().getGameData(basePath + ".capture_team", String.class);
        if (singleCaptureTeam != null && !singleCaptureTeam.isBlank()) {
            String normalized = singleCaptureTeam.toLowerCase(Locale.ROOT);
            if (!normalized.equals(ownerTeamId)) {
                captureTeamIds.add(normalized);
            }
        }

        return captureTeamIds;
    }
    public String buildWoolStatusLine(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                      ArenaState state,
                                      String teamId) {
        if (state == null || teamId == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        TeamsAPI<Player, Material> teamsAPI = context != null ? context.getTeamsAPI() : null;
        String normalizedTeamId = resolveTeamReference(teamsAPI, teamId);

        for (WoolDefinition def : state.getWoolDefinitions()) {
            if (!resolveTeamReference(teamsAPI, def.getTeamId()).equalsIgnoreCase(normalizedTeamId)) {
                continue;
            }
            WoolState woolState = state.getWoolState(def.getKey());
            String colorCode = resolveWoolColor(def.getMaterial());
            if (woolState == WoolState.CAPTURED) {
                sb.append(colorCode).append("█");
            } else if (woolState == WoolState.CARRIED || woolState == WoolState.DROPPED) {
                sb.append(colorCode).append("░");
            } else {
                sb.append(colorCode).append("⬜");
            }
            sb.append(" ");
        }

        return sb.toString().trim();
    }

    private String resolveWoolColor(Material material) {
        if (material == null) {
            return "<white>";
        }
        String name = material.name().toUpperCase(Locale.ROOT);
        if (name.contains("RED")) return "<red>";
        if (name.contains("BLUE")) return "<blue>";
        if (name.contains("GREEN")) return "<green>";
        if (name.contains("YELLOW")) return "<yellow>";
        if (name.contains("ORANGE")) return "<gold>";
        if (name.contains("PURPLE")) return "<dark_purple>";
        if (name.contains("PINK")) return "<light_purple>";
        if (name.contains("CYAN")) return "<aqua>";
        if (name.contains("LIME")) return "<green>";
        if (name.contains("MAGENTA")) return "<light_purple>";
        if (name.contains("LIGHT_BLUE")) return "<aqua>";
        if (name.contains("BLACK")) return "<dark_gray>";
        if (name.contains("GRAY")) return "<gray>";
        if (name.contains("LIGHT_GRAY")) return "<gray>";
        if (name.contains("BROWN")) return "<gold>";
        return "<white>";
    }

    private void removeWoolItems(Player player, ArenaState state) {
        if (player == null || state == null) {
            return;
        }
        for (WoolDefinition def : state.getWoolDefinitions()) {
            player.getInventory().remove(def.getMaterial());
        }
    }

    private boolean locationsMatch(Location a, Location b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.getWorld() != null && b.getWorld() != null && !a.getWorld().equals(b.getWorld())) {
            return false;
        }
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private Material resolveDefaultMaterial() {
        String raw = moduleConfig.getString("wools.default_material", "WHITE_WOOL");
        try {
            return Material.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Material.WHITE_WOOL;
        }
    }

    private String resolveWoolBasePath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        if (context.getDataAccess().hasGameData("game.play_area.wool_registry")) {
            return "game.play_area";
        }
        return "game";
    }

    private ItemStack createNamedWoolItem(WoolDefinition def) {
        ItemStack stack = new ItemStack(def.getMaterial(), 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        StringJoiner joiner = new StringJoiner(" ");
        joiner.add("§6Wool");
        joiner.add("§e#" + def.getWoolId());
        joiner.add("§7(" + def.getOwnerTeamId() + " → " + def.getCaptureTeamId() + ")");
        meta.setDisplayName(joiner.toString());
        stack.setItemMeta(meta);
        return stack;
    }
    private Location resolveGameLocation(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                         String basePath,
                                         World runtimeWorld) {
        if (context == null || context.getDataAccess() == null || basePath == null) {
            return null;
        }

        Location location = context.getDataAccess().getGameLocation(basePath);
        if (location == null) {
            location = readLocationWithoutWorld(context, basePath, runtimeWorld);
        }
        return withWorldIfMissing(location, runtimeWorld);
    }

    private Location resolveLocationWithRuntimeWorld(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                                     Location location) {
        if (location == null) {
            return null;
        }
        return withWorldIfMissing(location, resolveRuntimeWorld(context));
    }

    private Set<String> resolveWoolIds(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                       String woolBasePath) {
        Set<String> ids = new LinkedHashSet<>();
        if (context == null || context.getDataAccess() == null || woolBasePath == null) {
            return ids;
        }

        String registryRaw = context.getDataAccess().getGameData(woolBasePath + ".wool_registry", String.class);
        if (registryRaw != null && !registryRaw.isBlank()) {
            for (String woolIdRaw : registryRaw.split(",")) {
                String woolId = woolIdRaw == null ? "" : woolIdRaw.trim().toLowerCase(Locale.ROOT);
                if (isNumericId(woolId)) {
                    ids.add(woolId);
                }
            }
        }

        if (!ids.isEmpty()) {
            return ids;
        }

        for (int i = 1; i <= WOOL_ID_SCAN_LIMIT; i++) {
            String id = String.valueOf(i);
            String basePath = woolBasePath + ".wools." + id;
            boolean hasOwner = context.getDataAccess().hasGameData(basePath + ".owner_team");
            boolean hasSpawn = context.getDataAccess().hasGameData(basePath + ".spawn.x")
                    && context.getDataAccess().hasGameData(basePath + ".spawn.y")
                    && context.getDataAccess().hasGameData(basePath + ".spawn.z");
            if (hasOwner && hasSpawn) {
                ids.add(id);
            }
        }

        return ids;
    }

    private World resolveRuntimeWorld(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {        if (context == null) {
            return null;
        }

        ArenaAPI<Location, World> arenaAPI = context.getArenaAPI();
        if (arenaAPI != null && arenaAPI.getWorld() != null) {
            return arenaAPI.getWorld();
        }

        String configuredWorldName = context.getDataAccess() != null
                ? context.getDataAccess().getGameData("basic.world", String.class)
                : null;
        if (configuredWorldName != null && !configuredWorldName.isBlank()) {
            World configuredWorld = Bukkit.getWorld(configuredWorldName);
            if (configuredWorld != null) {
                return configuredWorld;
            }
        }

        for (Player player : context.getPlayers()) {
            if (player != null && player.getWorld() != null) {
                return player.getWorld();
            }
        }
        return null;
    }

    private Location readLocationWithoutWorld(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                              String basePath,
                                              World fallbackWorld) {
        if (context == null || context.getDataAccess() == null || basePath == null || fallbackWorld == null) {
            return null;
        }

        Double x = context.getDataAccess().getGameData(basePath + ".x", Double.class);
        Double y = context.getDataAccess().getGameData(basePath + ".y", Double.class);
        Double z = context.getDataAccess().getGameData(basePath + ".z", Double.class);
        if (x == null || y == null || z == null) {
            return null;
        }

        Float yaw = context.getDataAccess().getGameData(basePath + ".yaw", Float.class);
        Float pitch = context.getDataAccess().getGameData(basePath + ".pitch", Float.class);
        return new Location(fallbackWorld, x, y, z, yaw == null ? 0.0F : yaw, pitch == null ? 0.0F : pitch);
    }

    private Location withWorldIfMissing(Location location, World fallbackWorld) {
        if (location == null || location.getWorld() != null || fallbackWorld == null) {
            return location;
        }
        return new Location(fallbackWorld, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

}
