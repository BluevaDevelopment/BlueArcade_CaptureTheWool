package net.blueva.arcade.modules.capture_the_wool.game;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import net.blueva.arcade.modules.capture_the_wool.state.ArenaState;
import net.blueva.arcade.modules.capture_the_wool.support.DescriptionService;
import net.blueva.arcade.modules.capture_the_wool.support.PlaceholderService;
import net.blueva.arcade.modules.capture_the_wool.support.armory.ArmoryService;
import net.blueva.arcade.modules.capture_the_wool.support.combat.CombatService;
import net.blueva.arcade.modules.capture_the_wool.support.loadout.PlayerLoadoutService;
import net.blueva.arcade.modules.capture_the_wool.support.outcome.OutcomeService;
import net.blueva.arcade.modules.capture_the_wool.support.wool.WoolDefinition;
import net.blueva.arcade.modules.capture_the_wool.support.wool.WoolService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CaptureTheWoolGame {

    private final ModuleInfo moduleInfo;
    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final StatsAPI statsAPI;

    private final Map<Integer, ArenaState> arenas = new ConcurrentHashMap<>();
    private final Map<Player, Integer> playerArena = new ConcurrentHashMap<>();
    private final Map<Integer, Set<UUID>> countdownPreparedByArena = new ConcurrentHashMap<>();

    private final DescriptionService descriptionService;
    private final PlayerLoadoutService loadoutService;
    private final PlaceholderService placeholderService;
    private final OutcomeService outcomeService;
    private final CombatService combatService;
    private final WoolService woolService;
    private final ArmoryService armoryService;

    public CaptureTheWoolGame(ModuleInfo moduleInfo,
                       ModuleConfigAPI moduleConfig,
                       CoreConfigAPI coreConfig,
                       StatsAPI statsAPI) {
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.statsAPI = statsAPI;
        this.descriptionService = new DescriptionService(moduleConfig);
        this.loadoutService = new PlayerLoadoutService(moduleConfig);
        this.placeholderService = new PlaceholderService(moduleConfig, this);
        this.outcomeService = new OutcomeService(moduleInfo, statsAPI, this, placeholderService);
        this.combatService = new CombatService(moduleConfig, coreConfig, statsAPI, this, loadoutService);
        this.woolService = new WoolService(moduleConfig);
        this.armoryService = new ArmoryService();
    }

    public void startGame(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSummarySettings().setGameSummaryEnabled(false);
        context.getSummarySettings().setFinalSummaryEnabled(false);
        context.getSummarySettings().setRewardsEnabled(true);

        context.getSchedulerAPI().cancelArenaTasks(arenaId);
        ArenaState state = new ArenaState(context);
        arenas.put(arenaId, state);

        List<WoolDefinition> woolDefs = woolService.loadWoolDefinitions(context);
        state.setWoolDefinitions(woolDefs);

        loadTeamSpawns(context, state);

        loadTeamRestrictedZones(context, state);

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        for (Player player : context.getPlayers()) {
            playerArena.put(player, arenaId);
            state.initializePlayer(player.getUniqueId());
            if (teamsAPI != null && teamsAPI.isEnabled() && teamsAPI.getTeam(player) == null) {
                teamsAPI.autoAssignPlayer(player);
            }
        }

        descriptionService.sendDescription(context);
    }

    private void loadTeamSpawns(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                ArenaState state) {
        if (context.getDataAccess() == null) {
            return;
        }

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return;
        }

        String spawnBase = resolveDataBasePath(context, "team_spawns");

        int teamIndex = 1;
        for (TeamInfo<Player, Material> team : teamsAPI.getTeams()) {
            String teamId = team.getId();
            if (teamId == null || teamId.isBlank()) {
                teamIndex++;
                continue;
            }
            teamId = teamId.toLowerCase();
            String canonicalPath = spawnBase + "." + teamId;
            String numericPath = spawnBase + "." + teamIndex;

            String resolvedPath = null;
            if (context.getDataAccess().hasGameData(canonicalPath)) {
                resolvedPath = canonicalPath;
            } else if (context.getDataAccess().hasGameData(numericPath)) {
                resolvedPath = numericPath;
            }

            if (resolvedPath != null) {
                Location spawn = context.getDataAccess().getGameLocation(resolvedPath);
                if (spawn != null) {
                    state.setTeamSpawn(teamId, spawn);
                }
            }
            teamIndex++;
        }
    }

    private void loadTeamRestrictedZones(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                         ArenaState state) {
        if (context.getDataAccess() == null) {
            return;
        }

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return;
        }

        String zonesBase = resolveDataBasePath(context, "restricted_zones");

        int teamIndex = 1;
        for (TeamInfo<Player, Material> team : teamsAPI.getTeams()) {
            String teamId = team.getId();
            if (teamId == null || teamId.isBlank()) {
                teamIndex++;
                continue;
            }
            teamId = teamId.toLowerCase();
            for (int i = 1; i <= 10; i++) {
                String canonicalBasePath = zonesBase + "." + teamId + "." + i;
                String numericBasePath = zonesBase + "." + teamIndex + "." + i;
                String resolvedBasePath;
                if (context.getDataAccess().hasGameData(canonicalBasePath + ".min")) {
                    resolvedBasePath = canonicalBasePath;
                } else if (context.getDataAccess().hasGameData(numericBasePath + ".min")) {
                    resolvedBasePath = numericBasePath;
                } else {
                    continue;
                }

                Location min = context.getDataAccess().getGameLocation(resolvedBasePath + ".min");
                Location max = context.getDataAccess().getGameLocation(resolvedBasePath + ".max");
                if (min != null && max != null) {
                    state.addTeamRestrictedZone(teamId, min, max);
                }
            }
            teamIndex++;
        }
    }

    public void handleCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    int secondsLeft) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return;
        }

        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }

            if (markCountdownPrepared(context.getArenaId(), player.getUniqueId())) {
                context.getSchedulerAPI().runAtEntity(player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    teleportToTeamSpawn(context, state, player);
                    player.setGameMode(GameMode.SPECTATOR);
                });
            }

            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.countdown"));

            String title = coreConfig.getLanguage("titles.starting_game.title")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            String subtitle = coreConfig.getLanguage("titles.starting_game.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 5);
        }
    }

    public void handleCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }

            String title = coreConfig.getLanguage("titles.game_started.title")
                    .replace("{game_display_name}", moduleInfo.getName());

            String subtitle = coreConfig.getLanguage("titles.game_started.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName());

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 20);
            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.start"));
        }
    }

    public void beginPlaying(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return;
        }


        if (context.getAlivePlayers().isEmpty() && !context.getPlayers().isEmpty()) {
            context.setPlayers(context.getPlayers());
        }

        startGameTimer(context, state);

        woolService.spawnAllWools(context, state);

        for (Player player : context.getPlayers()) {
            teleportToTeamSpawn(context, state, player);
            player.setGameMode(GameMode.SURVIVAL);
            loadoutService.restoreVitals(player);
            loadoutService.giveStartingItems(context, player);
            loadoutService.applyStartingEffects(player);
            registerFallProtection(state, player);
            context.getScoreboardAPI().showScoreboard(player, getScoreboardPath(context));
        }
    }

    public void finishGame(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();
        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        ArenaState state = arenas.remove(arenaId);
        countdownPreparedByArena.remove(arenaId);
        if (state != null) {
            woolService.clearTrackedWoolItems(state);
        }
        resetWorldDefaults(context);
        resetPlayerHearts(context.getPlayers());
        removePlayersFromArena(arenaId, context.getPlayers());

        if (statsAPI != null) {
            for (Player player : context.getPlayers()) {
                statsAPI.addModuleStat(player, moduleInfo.getId(), "games_played", 1);
            }
        }
    }

    public void shutdown() {
        Set<ArenaState> states = Set.copyOf(arenas.values());
        for (ArenaState state : states) {
            state.getContext().getSchedulerAPI().cancelModuleTasks("capture_the_wool");
            woolService.clearTrackedWoolItems(state);
            resetWorldDefaults(state.getContext());
            resetPlayerHearts(state.getContext().getPlayers());
        }

        arenas.clear();
        playerArena.clear();
        countdownPreparedByArena.clear();
    }

    private boolean markCountdownPrepared(int arenaId, UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return countdownPreparedByArena
                .computeIfAbsent(arenaId, ignored -> ConcurrentHashMap.newKeySet())
                .add(playerId);
    }

    public Map<String, String> getPlaceholders(Player player) {
        return placeholderService.buildPlaceholders(player);
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getContext(Player player) {
        Integer arenaId = playerArena.get(player);

        if (arenaId == null) {
            for (ArenaState state : arenas.values()) {
                if (state.getContext() != null && state.getContext().getPlayers().contains(player)) {
                    arenaId = state.getContext().getArenaId();
                    playerArena.put(player, arenaId);
                    break;
                }
            }
        }

        if (arenaId == null) {
            return null;
        }
        ArenaState state = arenas.get(arenaId);
        return state != null ? state.getContext() : null;
    }

    public ArenaState getArenaState(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        if (context == null) {
            return null;
        }
        return arenas.get(context.getArenaId());
    }

    public int getPlayerKills(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                              Player player) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return 0;
        }
        return state.getKills(player.getUniqueId());
    }

    public void addPlayerKill(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                              Player player) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return;
        }
        state.addKill(player.getUniqueId());
    }

    public int getPlayerDeaths(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                               Player player) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return 0;
        }
        return state.getDeaths(player.getUniqueId());
    }

    public void addPlayerDeath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                               Player player) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return;
        }
        state.addDeath(player.getUniqueId());
    }

    public void healKiller(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                           Player killer) {
        loadoutService.handleKillRegeneration(context, killer);
        context.getSoundsAPI().play(killer, coreConfig.getSound("sounds.in_game.respawn"));
    }

    public void handleKill(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                           Player attacker,
                           Player victim) {
        combatService.handleKillCredit(context, attacker);
        combatService.handleElimination(context, victim, attacker);
    }

    public void handleNonCombatDeath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                     Player victim) {
        combatService.handleElimination(context, victim, null);
    }

    public void handleWoolDrop(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                               Player player) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return;
        }
        woolService.handlePlayerDeath(context, state, player);
    }

    public boolean isPlayerCarryingWool(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                        Player player) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return false;
        }
        return woolService.isPlayerCarryingWool(state, player);
    }

    public boolean isCarriedWoolMaterial(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                         Player player,
                                         Material material) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return false;
        }
        return woolService.isCarriedWoolMaterial(state, player, material);
    }

    public boolean isObjectiveWoolMaterial(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                           Material material) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return false;
        }
        return woolService.isObjectiveWoolMaterial(state, material);
    }

    public void respawnPlayer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                              Player player) {
        ArenaState state = getArenaState(context);
        if (state == null || player == null || !player.isOnline()) {
            return;
        }

        teleportToTeamSpawn(context, state, player);
        player.setGameMode(GameMode.SURVIVAL);
        loadoutService.restoreVitals(player);
        loadoutService.giveStartingItems(context, player);
        loadoutService.applyStartingEffects(player);
        loadoutService.applyRespawnEffects(player);
        registerFallProtection(state, player);
    }

    private void teleportToTeamSpawn(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                     ArenaState state,
                                     Player player) {
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            throw new IllegalStateException("CTW requires TeamsAPI enabled to teleport players to team spawns.");
        }

        TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
        if (team == null) {
            throw new IllegalStateException("Player '" + player.getName()
                    + "' has no assigned team. Team spawn teleport cannot continue.");
        }

        Location spawn = state.getTeamSpawn(team.getId());
        if (spawn == null) {
            throw new IllegalStateException("Missing configured team spawn for team '" + team.getId()
                    + "' (player '" + player.getName() + "'). Refusing fallback to legacy spawn.");
        }

        player.teleport(spawn);
    }

    public boolean handleWoolPickup(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    Player player,
                                    Block block) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return false;
        }
        boolean picked = woolService.handleWoolPickup(context, state, player, block);
        if (picked) {
            if (statsAPI != null) {
                statsAPI.addModuleStat(player, moduleInfo.getId(), "wools_stolen", 1);
            }
            checkForWoolVictory(context);
        }
        return picked;
    }


    public String getWoolPickupBlockedMessage(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                              Player player,
                                              Block block) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return null;
        }
        return woolService.resolveWoolPickupBlockedMessage(context, state, player, block);
    }

    public boolean handleWoolCapture(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                     Player player,
                                     Block placedBlock,
                                     Material placedMaterial) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return false;
        }
        boolean captured = woolService.handleWoolCapture(context, state, player, placedBlock, placedMaterial);
        if (captured) {
            if (statsAPI != null) {
                statsAPI.addModuleStat(player, moduleInfo.getId(), "wools_captured", 1);
            }
            checkForWoolVictory(context);
        }
        return captured;
    }

    public boolean isCaptureLocation(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                     Location location) {
        ArenaState state = getArenaState(context);
        return woolService.isCaptureLocation(state, location);
    }

    public boolean isWoolSpawnLocation(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                       Location location) {
        ArenaState state = getArenaState(context);
        return woolService.isWoolSpawnLocation(state, location);
    }

    public boolean isInRestrictedZone(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                      Player player,
                                      Location location) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return false;
        }
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return false;
        }
        TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
        if (team == null) {
            return false;
        }
        return state.isInRestrictedZone(team.getId(), location);
    }

    public ArmoryService getArmoryService() {
        return armoryService;
    }

    public WoolService getWoolService() {
        return woolService;
    }

    public void endGame(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return;
        }

        outcomeService.endGame(context, state);
    }

    public ModuleConfigAPI getModuleConfig() {
        return moduleConfig;
    }

    public ModuleInfo getModuleInfo() {
        return moduleInfo;
    }

    public void removePlayersFromArena(int arenaId, List<Player> players) {
        for (Player player : players) {
            playerArena.remove(player);
        }
    }

    private void resetWorldDefaults(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        if (context == null || context.getArenaAPI() == null) {
            return;
        }
        World world = context.getArenaAPI().getWorld();
        if (world == null) {
            return;
        }
        world.setTime(1000L);
        world.setStorm(false);
        world.setThundering(false);
    }

    private void resetPlayerHearts(List<Player> players) {
        if (players == null) {
            return;
        }
        for (Player player : players) {
            if (player == null) {
                continue;
            }
            if (player.getAttribute(maxHealthAttribute()) != null) {
                player.getAttribute(maxHealthAttribute()).setBaseValue(20.0);
            }
            player.setHealth(Math.min(player.getHealth(), 20.0));
        }
    }

    public String getScoreboardPath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        int teamCount = teamsAPI != null && teamsAPI.isEnabled() ? teamsAPI.getTeams().size() : 0;
        if (teamCount <= 2) {
            return "scoreboard.team_size_2";
        }
        if (teamCount == 3) {
            return "scoreboard.team_size_3";
        }
        return "scoreboard.team_size_4";
    }

    public boolean isSoloMode(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        if (context == null) {
            return true;
        }
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return true;
        }
        if (context.getDataAccess() == null) {
            return false;
        }
        Integer teamSize = context.getDataAccess().getGameData("teams.size", Integer.class);
        Integer teamCount = context.getDataAccess().getGameData("teams.count", Integer.class);
        if (teamSize != null && teamSize <= 1) {
            return true;
        }
        return teamCount != null && teamCount <= 1;
    }

    public List<String> getAliveTeamIds(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            List<String> ids = new ArrayList<>();
            if (!context.getAlivePlayers().isEmpty()) {
                ids.add("solo");
            }
            return ids;
        }

        Set<String> teamIds = new HashSet<>();
        for (Player player : context.getAlivePlayers()) {
            TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
            if (team != null) {
                teamIds.add(team.getId());
            }
        }
        return new ArrayList<>(teamIds);
    }

    public Map<String, Integer> getTeamKills(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        Map<String, Integer> teamKills = new HashMap<>();
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        for (Player player : context.getPlayers()) {
            int kills = getPlayerKills(context, player);
            String teamId = "solo";
            if (teamsAPI != null && teamsAPI.isEnabled()) {
                TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
                if (team != null) {
                    teamId = team.getId();
                }
            }
            teamKills.merge(teamId, kills, Integer::sum);
        }
        return teamKills;
    }

    public Map<String, Integer> getTeamDeaths(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        Map<String, Integer> teamDeaths = new HashMap<>();
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        for (Player player : context.getPlayers()) {
            int deaths = getPlayerDeaths(context, player);
            String teamId = "solo";
            if (teamsAPI != null && teamsAPI.isEnabled()) {
                TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
                if (team != null) {
                    teamId = team.getId();
                }
            }
            teamDeaths.merge(teamId, deaths, Integer::sum);
        }
        return teamDeaths;
    }

    public List<Player> getTeamPlayers(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                       String teamId) {
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        List<Player> players = new ArrayList<>();
        for (Player player : context.getPlayers()) {
            if (teamsAPI == null || !teamsAPI.isEnabled()) {
                players.add(player);
                continue;
            }
            TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
            if (team != null && team.getId().equalsIgnoreCase(teamId)) {
                players.add(player);
            }
        }
        return players;
    }

    public void checkForWoolVictory(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        ArenaState state = getArenaState(context);
        if (state == null || state.isEnded()) {
            return;
        }

        if (shouldEndForVictory(context, state)) {
            endGame(context);
        }
    }

    private void startGameTimer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                ArenaState state) {
        int arenaId = context.getArenaId();
        int fallProtectionSeconds = Math.max(0, moduleConfig.getInt("spawn_protection.fall_damage_seconds", 5));

        String taskId = "arena_" + arenaId + "_capture_the_wool_timer";
        String hologramTaskId = "arena_" + arenaId + "_capture_the_wool_carrier_holograms";
        int carrierHologramIntervalTicks = Math.max(1, moduleConfig.getInt("wool.carrier_hologram_update_ticks", 2));

        context.getSchedulerAPI().runTimer(hologramTaskId, () -> {
            if (state.isEnded()) {
                context.getSchedulerAPI().cancelTask(hologramTaskId);
                return;
            }
            woolService.updateCarrierHolograms(state);
        }, 0L, carrierHologramIntervalTicks);

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (state.isEnded()) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            state.incrementMatchSeconds();
            refreshFallProtection(state, context.getPlayers(), fallProtectionSeconds);

            woolService.spawnAllWools(context, state);


            List<Player> allPlayers = context.getPlayers();

            if (shouldEndForVictory(context, state)) {
                endGame(context);
                return;
            }

            String actionBarTemplate = moduleConfig.getStringFrom("language.yml", "messages.action_bar.in_game");
            for (Player player : allPlayers) {
                if (!player.isOnline()) {
                    continue;
                }

                Map<String, String> customPlaceholders = placeholderService.buildPlaceholders(player);
                context.getMessagesAPI().sendActionBar(player, actionBarTemplate
                        .replace("{team}", customPlaceholders.getOrDefault("team", "-"))
                        .replace("{captured}", customPlaceholders.getOrDefault("team_wools_captured", "0"))
                        .replace("{objectives}", customPlaceholders.getOrDefault("team_wools_total", "0"))
                        .replace("{kills}", customPlaceholders.getOrDefault("kills", "0"))
                        .replace("{deaths}", customPlaceholders.getOrDefault("deaths", "0"))
                        .replace("{carrying}", customPlaceholders.getOrDefault("carrying_wool", moduleConfig.getStringFrom("language.yml", "messages.common.boolean_false")))
                        .replace("{elapsed}", String.valueOf(state.getMatchSeconds())));

                context.getScoreboardAPI().update(player, getScoreboardPath(context), customPlaceholders);
            }
        }, 0L, 20L);
    }

    private boolean shouldEndForVictory(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                        ArenaState state) {
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return false;
        }

        for (TeamInfo<Player, Material> team : teamsAPI.getTeams()) {
            String teamId = team.getId();
            if (teamId == null || teamId.isBlank()) {
                continue;
            }
            if (woolService.hasTeamCapturedAllWools(context, state, teamId)) {
                return true;
            }
        }
        return false;
    }

    private String resolveDataBasePath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                       String section) {
        if (context.getDataAccess().hasGameData("game.play_area." + section)) {
            return "game.play_area." + section;
        }
        return "game." + section;
    }

    private void registerFallProtection(ArenaState state, Player player) {
        if (state == null || player == null) {
            return;
        }
        int protectionSeconds = Math.max(0, moduleConfig.getInt("spawn_protection.fall_damage_seconds", 5));
        if (protectionSeconds <= 0) {
            return;
        }
        state.setFallProtection(player.getUniqueId(), System.currentTimeMillis() + (protectionSeconds * 1000L));
    }

    private void refreshFallProtection(ArenaState state, List<Player> players, int protectionSeconds) {
        if (state == null || protectionSeconds <= 0) {
            return;
        }
        for (Player player : players) {
            if (player == null) {
                continue;
            }
            if (state.hasFallProtection(player.getUniqueId())) {
                continue;
            }
            if (state.getMatchSeconds() == 1) {
                state.setFallProtection(player.getUniqueId(),
                        System.currentTimeMillis() + (protectionSeconds * 1000L));
            }
        }
    }

    public void trackPlacedBlock(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                 Location location) {
        ArenaState state = getArenaState(context);
        if (state != null) {
            state.trackPlacedBlock(location);
        }
    }

    public boolean canBreakBlock(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                 Block block) {
        ArenaState state = getArenaState(context);
        return state != null && state.isPlayerPlacedBlock(block.getLocation());
    }

    public void untrackPlacedBlock(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                   Location location) {
        ArenaState state = getArenaState(context);
        if (state != null) {
            state.untrackPlacedBlock(location);
        }
    }

    private Attribute maxHealthAttribute() {
        try {
            return Attribute.valueOf("MAX_HEALTH");
        } catch (IllegalArgumentException ignored) {
            return Attribute.valueOf("GENERIC_MAX_HEALTH");
        }
    }
}
