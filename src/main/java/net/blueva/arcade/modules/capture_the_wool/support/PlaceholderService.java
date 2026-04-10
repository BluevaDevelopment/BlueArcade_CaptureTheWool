package net.blueva.arcade.modules.capture_the_wool.support;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import net.blueva.arcade.modules.capture_the_wool.game.CaptureTheWoolGame;
import net.blueva.arcade.modules.capture_the_wool.state.ArenaState;
import net.blueva.arcade.modules.capture_the_wool.support.wool.WoolDefinition;
import net.blueva.arcade.modules.capture_the_wool.support.wool.WoolState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlaceholderService {

    private final ModuleConfigAPI moduleConfig;
    private final CaptureTheWoolGame game;

    public PlaceholderService(ModuleConfigAPI moduleConfig, CaptureTheWoolGame game) {
        this.moduleConfig = moduleConfig;
        this.game = game;
    }

    public Map<String, String> buildPlaceholders(Player player) {
        Map<String, String> placeholders = new HashMap<>();

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game.getContext(player);
        if (context != null) {
            placeholders.put("alive", String.valueOf(context.getAlivePlayers().size()));
            placeholders.put("spectators", String.valueOf(context.getSpectators().size()));
            placeholders.put("kills", String.valueOf(game.getPlayerKills(context, player)));
            placeholders.put("deaths", String.valueOf(game.getPlayerDeaths(context, player)));
            placeholders.put("alive_teams", String.valueOf(game.getAliveTeamIds(context).size()));

            ArenaState state = game.getArenaState(context);
            String carryingYes = moduleConfig.getStringFrom("language.yml", "messages.common.boolean_true");
            String carryingNo = moduleConfig.getStringFrom("language.yml", "messages.common.boolean_false");
            placeholders.put("carrying_wool", state != null && game.getWoolService().isPlayerCarryingWool(state, player) ? carryingYes : carryingNo);

            TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
            if (teamsAPI != null && teamsAPI.isEnabled()) {
                TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
                placeholders.put("team", team != null ? team.getDisplayName() : "-");

                if (state != null) {
                    if (team != null && team.getId() != null) {
                        int captured = game.getWoolService().getTeamCapturedObjectives(context, state, team.getId());
                        int total = game.getWoolService().getTeamObjectiveCount(context, state, team.getId());
                        placeholders.put("team_wools_captured", String.valueOf(captured));
                        placeholders.put("team_wools_total", String.valueOf(total));
                    } else {
                        placeholders.put("team_wools_captured", "0");
                        placeholders.put("team_wools_total", "0");
                    }

                    int index = 1;
                    for (Object teamObj : teamsAPI.getTeams()) {
                        if (!(teamObj instanceof TeamInfo teamInfo) || teamInfo.getId() == null) {
                            continue;
                        }
                        String teamId = teamInfo.getId().toLowerCase();
                        String woolStatus = game.getWoolService().buildWoolStatusLine(context, state, teamId);
                        if (woolStatus == null || woolStatus.isBlank()) {
                            woolStatus = "⬜";
                        }
                        placeholders.put("wool_status_" + teamId, woolStatus);
                        placeholders.put("wool_status_team_" + index, woolStatus);
                        placeholders.put("wool_status_team_" + teamId, woolStatus);
                        index++;
                    }

                    if (team != null && team.getId() != null) {
                        String playerTeamStatus = placeholders.getOrDefault(
                                "wool_status_team_" + team.getId().toLowerCase(), "⬜");
                        placeholders.put("wool_status_team_id", playerTeamStatus);
                    }

                    buildTeamSummaryPlaceholders(context, state, teamsAPI, placeholders, team);
                }
            } else {
                placeholders.put("team", "-");
            }
        }

        return placeholders;
    }

    private void buildTeamSummaryPlaceholders(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                              ArenaState state,
                                              TeamsAPI<Player, Material> teamsAPI,
                                              Map<String, String> placeholders,
                                              TeamInfo<Player, Material> playerTeam) {
        Map<String, Integer> teamKills = game.getTeamKills(context);
        Map<String, Integer> teamDeaths = game.getTeamDeaths(context);

        int index = 1;
        for (Object teamObj : teamsAPI.getTeams()) {
            if (!(teamObj instanceof TeamInfo teamInfo) || teamInfo.getId() == null) {
                continue;
            }

            String teamId = teamInfo.getId();
            boolean isPlayerTeam = playerTeam != null
                    && playerTeam.getId() != null
                    && playerTeam.getId().equalsIgnoreCase(teamId);

            int captured = game.getWoolService().getTeamCapturedObjectives(context, state, teamId);
            int totalObjectives = game.getWoolService().getTeamObjectiveCount(context, state, teamId);
            int kills = teamKills.getOrDefault(teamId, 0);
            int deaths = teamDeaths.getOrDefault(teamId, 0);

            placeholders.put("team_header_" + index,
                    "<white>" + teamInfo.getDisplayName() + (isPlayerTeam ? " <green><bold><- You</bold></green>" : "") + "</white>" + uniqueSuffix(index, 0));
            placeholders.put("team_summary_" + index,
                    "<white>" + teamInfo.getDisplayName() + "</white> <gray>W "
                            + captured + "/" + totalObjectives + " • K " + kills + " • D " + deaths + "</gray>");

            List<WoolDefinition> teamWools = new ArrayList<>();
            for (WoolDefinition def : state.getWoolDefinitions()) {
                if (def.getOwnerTeamId() != null && def.getOwnerTeamId().equalsIgnoreCase(teamId)) {
                    teamWools.add(def);
                }
            }

            teamWools.sort((a, b) -> compareWoolIds(a.getWoolId(), b.getWoolId()));

            for (int obj = 1; obj <= 2; obj++) {
                String objectivePlaceholder = "team_objective_" + index + "_" + obj;
                if (obj <= teamWools.size()) {
                    WoolDefinition def = teamWools.get(obj - 1);
                    placeholders.put(objectivePlaceholder, buildObjectiveLine(state, def, isPlayerTeam, index, obj));
                } else {
                    placeholders.put(objectivePlaceholder, "<dark_gray>·</dark_gray>" + uniqueSuffix(index, obj));
                }
            }

            index++;
        }

        for (int i = index; i <= 6; i++) {
            placeholders.put("team_header_" + i, "<black>.</black>" + uniqueSuffix(i, 0));
            placeholders.put("team_summary_" + i, " ");
            placeholders.put("team_objective_" + i + "_1", "<black>.</black>" + uniqueSuffix(i, 1));
            placeholders.put("team_objective_" + i + "_2", "<black>.</black>" + uniqueSuffix(i, 2));
        }
    }

    private String buildObjectiveLine(ArenaState state, WoolDefinition def, boolean playerOwnsTeam, int teamIndex, int objectiveIndex) {
        WoolState woolState = state.getWoolState(def.getKey());
        String checkbox;
        String label;

        if (woolState == WoolState.CAPTURED) {
            checkbox = "<red>☒</red>";
            label = playerOwnsTeam ? "<red>Lost</red>" : "<green>Captured</green>";
        } else if (woolState == WoolState.CARRIED || woolState == WoolState.DROPPED) {
            checkbox = "<gold>☐</gold>";
            label = playerOwnsTeam ? "<gold>Under Attack</gold>" : "<gold>Taken</gold>";
        } else {
            checkbox = "<green>☐</green>";
            label = playerOwnsTeam ? "<green>Defended</green>" : "<green>Safe</green>";
        }

        return "<gray>-</gray> " + checkbox + " " + label + uniqueSuffix(teamIndex, objectiveIndex);
    }


    private String uniqueSuffix(int teamIndex, int slotIndex) {
        int count = Math.max(1, (teamIndex * 3) + slotIndex);
        return "​".repeat(count);
    }

    private int compareWoolIds(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }

        try {
            return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
        } catch (NumberFormatException ignored) {
            return a.compareToIgnoreCase(b);
        }
    }

    public List<Player> getPlayersSortedByKills(
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
            List<Player> players,
            int limit) {
        Map<Player, Integer> killCounts = new HashMap<>();
        for (Player player : players) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            killCounts.put(player, game.getPlayerKills(context, player));
        }

        List<Map.Entry<Player, Integer>> sorted = new java.util.ArrayList<>(killCounts.entrySet());
        sorted.sort((a, b) -> {
            int compare = Integer.compare(b.getValue(), a.getValue());
            if (compare != 0) {
                return compare;
            }
            return a.getKey().getName().compareToIgnoreCase(b.getKey().getName());
        });

        List<Player> orderedPlayers = new java.util.ArrayList<>();
        for (Map.Entry<Player, Integer> entry : sorted) {
            orderedPlayers.add(entry.getKey());
            if (orderedPlayers.size() >= limit) {
                break;
            }
        }

        return orderedPlayers;
    }

    public ModuleConfigAPI getModuleConfig() {
        return moduleConfig;
    }

}
