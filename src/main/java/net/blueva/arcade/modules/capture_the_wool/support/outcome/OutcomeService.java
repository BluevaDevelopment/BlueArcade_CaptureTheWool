package net.blueva.arcade.modules.capture_the_wool.support.outcome;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import net.blueva.arcade.modules.capture_the_wool.game.CaptureTheWoolGame;
import net.blueva.arcade.modules.capture_the_wool.state.ArenaState;
import net.blueva.arcade.modules.capture_the_wool.support.PlaceholderService;
import net.blueva.arcade.modules.capture_the_wool.support.wool.WoolDefinition;
import net.blueva.arcade.modules.capture_the_wool.support.wool.WoolState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class OutcomeService {

    private final ModuleInfo moduleInfo;
    private final StatsAPI statsAPI;
    private final CaptureTheWoolGame game;
    private final PlaceholderService placeholderService;

    public OutcomeService(ModuleInfo moduleInfo,
                          StatsAPI statsAPI,
                          CaptureTheWoolGame game,
                          PlaceholderService placeholderService) {
        this.moduleInfo = moduleInfo;
        this.statsAPI = statsAPI;
        this.game = game;
        this.placeholderService = placeholderService;
    }

    public void endGame(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                        ArenaState state) {
        if (state.markEnded()) {
            return;
        }

        context.getSchedulerAPI().cancelArenaTasks(context.getArenaId());

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI != null && teamsAPI.isEnabled()) {
            for (TeamInfo<Player, Material> team : teamsAPI.getTeams()) {
                String teamId = team.getId();
                if (teamId == null || teamId.isBlank()) {
                    continue;
                }
                if (game.getWoolService().hasTeamCapturedAllWools(context, state, teamId)) {
                    declareWinningTeam(context, state, teamId);
                    context.endGame();
                    return;
                }
            }
        }

        declareTopTeamByKills(context, state);
        context.endGame();
    }

    private void declareWinningTeam(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    ArenaState state,
                                    String teamId) {
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        List<Player> winners = game.getTeamPlayers(context, teamId);
        if (teamsAPI != null && teamsAPI.isEnabled()) {
            teamsAPI.setWinningTeam(teamId);
        }
        if (!winners.isEmpty()) {
            context.markSharedFirstPlace(winners);
        }
        showFinalScoreboard(context, state, teamId);
        handleWinStats(state, winners);
    }

    private void declareTopTeamByKills(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                       ArenaState state) {
        Map<String, Integer> teamKills = game.getTeamKills(context);
        if (teamKills.isEmpty()) {
            handleNoWinner(context);
            return;
        }

        int maxKills = teamKills.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<String> topTeams = teamKills.entrySet().stream()
                .filter(entry -> entry.getValue() == maxKills)
                .map(Map.Entry::getKey)
                .toList();

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI != null && teamsAPI.isEnabled()) {
            teamsAPI.setWinningTeams(topTeams);
        }

        List<Player> winners = new ArrayList<>();
        for (String teamId : topTeams) {
            winners.addAll(game.getTeamPlayers(context, teamId));
        }
        if (!winners.isEmpty()) {
            context.markSharedFirstPlace(winners);
        }
        String winnerTeam = topTeams.isEmpty() ? "-" : topTeams.get(0);
        showFinalScoreboard(context, state, winnerTeam);
        handleWinStats(state, winners);
    }


    private void showFinalScoreboard(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                     ArenaState state,
                                     String winningTeamId) {
        if (context == null || state == null) {
            return;
        }

        Map<String, String> placeholders = new java.util.HashMap<>();
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        String winningDisplay = winningTeamId;
        if (teamsAPI != null && teamsAPI.isEnabled() && winningTeamId != null) {
            for (TeamInfo<Player, Material> team : teamsAPI.getTeams()) {
                if (team != null && team.getId() != null && team.getId().equalsIgnoreCase(winningTeamId)) {
                    winningDisplay = team.getDisplayName();
                    break;
                }
            }
        }

        placeholders.put("winning_team", winningDisplay == null ? "-" : winningDisplay);
        placeholders.put("winning_captured", String.valueOf(game.getWoolService().getTeamCapturedObjectives(context, state, winningTeamId)));
        placeholders.put("winning_objectives", String.valueOf(game.getWoolService().getTeamObjectiveCount(context, state, winningTeamId)));
        populateTopKillsPlaceholders(context, placeholders);

        sendWinnerOutcomeMessage(context, state, placeholders);

        List<Player> players = context.getPlayers();
        for (Player player : players) {
            Map<String, String> finalPlaceholders = new java.util.HashMap<>(placeholders);
            finalPlaceholders.putAll(game.getPlaceholders(player));
            context.getScoreboardAPI().showModuleFinalScoreboard(player, "scoreboard.final.winner", finalPlaceholders);
        }
    }

    private void populateTopKillsPlaceholders(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                              Map<String, String> placeholders) {
        if (context == null || placeholders == null) {
            return;
        }

        List<Player> topKillers = placeholderService.getPlayersSortedByKills(
                context,
                new ArrayList<>(context.getPlayers()),
                5
        );

        for (int i = 1; i <= 5; i++) {
            if (topKillers.size() >= i) {
                Player killer = topKillers.get(i - 1);
                placeholders.put("top_kills_" + i, killer.getName());
                placeholders.put("top_kills_value_" + i, String.valueOf(game.getPlayerKills(context, killer)));
            } else {
                placeholders.put("top_kills_" + i, "-");
                placeholders.put("top_kills_value_" + i, "0");
            }
        }
    }

    private void sendWinnerOutcomeMessage(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                          ArenaState state,
                                          Map<String, String> placeholders) {
        if (context == null || state == null) {
            return;
        }

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return;
        }

        String separator = getWinnerBoardText("messages.winner_board.separator");
        String title = getWinnerBoardText("messages.winner_board.title");
        String teamCapturesTemplate = getWinnerBoardText("messages.winner_board.team_title");
        String missingText = getWinnerBoardText("messages.winner_board.missing_text");
        String linePrefix = getWinnerBoardText("messages.winner_board.line_prefix");
        String lineGap = getWinnerBoardText("messages.winner_board.entry_gap");
        int entriesPerLine = Math.max(1, placeholderService.getModuleConfig().getInt("messages.winner_board.entries_per_line", 2));

        List<String> lines = new ArrayList<>();
        lines.add(separator);
        lines.add(linePrefix + applyPlaceholders(title, placeholders));
        lines.add(" ");

        int teamIndex = 1;
        for (TeamInfo<Player, Material> teamInfo : teamsAPI.getTeams()) {
            if (teamInfo == null || teamInfo.getId() == null) {
                teamIndex++;
                continue;
            }

            String teamId = teamInfo.getId();
            String teamDisplay = teamInfo.getDisplayName() == null || teamInfo.getDisplayName().isBlank()
                    ? teamId
                    : teamInfo.getDisplayName();

            String teamLine = teamCapturesTemplate
                    .replace("{team}", teamDisplay)
                    .replace("{team_id}", teamId)
                    .replace("{team_index}", String.valueOf(teamIndex));
            lines.add(linePrefix + teamLine);

            List<WoolDefinition> capturableWools = getCapturableWoolsForTeam(state, teamId, teamIndex);
            if (capturableWools.isEmpty()) {
                lines.add(linePrefix + formatMissingEntry("yellow", missingText));
                teamIndex++;
                continue;
            }

            List<String> formattedEntries = new ArrayList<>();
            for (WoolDefinition wool : capturableWools) {
                String woolColor = getWoolColorTag(wool.getMaterial());
                WoolState woolState = state.getWoolState(wool.getKey());
                UUID capturerId = state.getWoolCapturer(wool.getKey());

                if (woolState == WoolState.CAPTURED && capturerId != null) {
                    String capturerName = resolvePlayerName(context, capturerId);
                    formattedEntries.add(formatCapturedEntry(woolColor, capturerName));
                } else {
                    formattedEntries.add(formatMissingEntry(woolColor, missingText));
                }
            }

            for (int i = 0; i < formattedEntries.size(); i += entriesPerLine) {
                int end = Math.min(i + entriesPerLine, formattedEntries.size());
                List<String> row = formattedEntries.subList(i, end);
                lines.add(linePrefix + String.join(lineGap, row));
            }

            teamIndex++;
            lines.add(" ");
        }

        lines.add(separator);

        for (String line : lines) {
            String processed = applyPlaceholders(line, placeholders);
            for (Player player : context.getPlayers()) {
                context.getMessagesAPI().sendRaw(player, processed);
            }
        }
    }

    private String getWinnerBoardText(String path) {
        return placeholderService.getModuleConfig().getStringFrom("language.yml", path);
    }

    private String formatCapturedEntry(String woolColor, String playerName) {
        String color = woolColor == null || woolColor.isBlank() ? "white" : woolColor;
        return "<" + color + ">■</" + color + "> <white>" + playerName + "</white>";
    }

    private String formatMissingEntry(String woolColor, String missingText) {
        String color = woolColor == null || woolColor.isBlank() ? "white" : woolColor;
        return "<" + color + ">□</" + color + "> <gray>" + missingText + "</gray>";
    }

    private String getWoolColorTag(Material material) {
        if (material == null) {
            return "white";
        }

        return switch (material) {
            case WHITE_WOOL -> "white";
            case ORANGE_WOOL -> "gold";
            case MAGENTA_WOOL -> "light_purple";
            case LIGHT_BLUE_WOOL -> "aqua";
            case YELLOW_WOOL -> "yellow";
            case LIME_WOOL -> "green";
            case PINK_WOOL -> "light_purple";
            case GRAY_WOOL -> "gray";
            case LIGHT_GRAY_WOOL -> "gray";
            case CYAN_WOOL -> "aqua";
            case PURPLE_WOOL -> "dark_purple";
            case BLUE_WOOL -> "blue";
            case BROWN_WOOL -> "gold";
            case GREEN_WOOL -> "dark_green";
            case RED_WOOL -> "red";
            case BLACK_WOOL -> "dark_gray";
            default -> "white";
        };
    }

    private List<WoolDefinition> getCapturableWoolsForTeam(ArenaState state, String teamId, int teamIndex) {
        if (state == null || teamId == null || teamId.isBlank()) {
            return List.of();
        }

        String normalizedTeamId = teamId.toLowerCase(Locale.ROOT);
        String numericAlias = String.valueOf(teamIndex);

        List<WoolDefinition> result = new ArrayList<>();
        for (WoolDefinition definition : state.getWoolDefinitions()) {
            if (definition == null) {
                continue;
            }

            Set<String> captureTeams = definition.getCaptureTeamIds();
            if (captureTeams == null || captureTeams.isEmpty()) {
                continue;
            }

            boolean matches = false;
            for (String captureTeam : captureTeams) {
                if (captureTeam == null || captureTeam.isBlank()) {
                    continue;
                }

                String normalizedCaptureTeam = captureTeam.toLowerCase(Locale.ROOT);
                if (normalizedCaptureTeam.equals(normalizedTeamId) || normalizedCaptureTeam.equals(numericAlias)) {
                    matches = true;
                    break;
                }
            }

            if (matches) {
                result.add(definition);
            }
        }

        result.sort(Comparator.comparing(WoolDefinition::getWoolId, this::compareWoolIds));
        return result;
    }

    private String resolvePlayerName(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                     UUID playerId) {
        if (playerId == null) {
            return placeholderService.getModuleConfig().getStringFrom("language.yml", "messages.winner_board.unknown_player");
        }

        for (Player player : context.getPlayers()) {
            if (player != null && player.getUniqueId().equals(playerId)) {
                return player.getName();
            }
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        if (offlinePlayer.getName() != null && !offlinePlayer.getName().isBlank()) {
            return offlinePlayer.getName();
        }

        return placeholderService.getModuleConfig().getStringFrom("language.yml", "messages.winner_board.unknown_player");
    }

    private int compareWoolIds(String first, String second) {
        if (first == null && second == null) {
            return 0;
        }
        if (first == null) {
            return 1;
        }
        if (second == null) {
            return -1;
        }

        try {
            return Integer.compare(Integer.parseInt(first), Integer.parseInt(second));
        } catch (NumberFormatException ignored) {
            return first.compareToIgnoreCase(second);
        }
    }

    private String applyPlaceholders(String template, Map<String, String> placeholders) {
        if (template == null || placeholders == null || placeholders.isEmpty()) {
            return template;
        }

        String value = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String replacement = entry.getValue() == null ? "" : entry.getValue();
            value = value.replace("{" + entry.getKey() + "}", replacement);
        }
        return value;
    }

    private void handleNoWinner(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        List<Player> sortedByKills = placeholderService.getPlayersSortedByKills(
                context, new ArrayList<>(context.getPlayers()), context.getPlayers().size());
        if (sortedByKills.isEmpty()) {
            return;
        }
        context.markSharedFirstPlace(List.of(sortedByKills.get(0)));
    }

    private void handleWinStats(ArenaState state, List<Player> winners) {
        if (statsAPI == null || winners.isEmpty()) {
            return;
        }

        UUID winnerId = state.getWinnerId();
        if (winnerId != null) {
            return;
        }

        state.setWinner(winners.get(0).getUniqueId());
        for (Player winner : winners) {
            statsAPI.addModuleStat(winner, moduleInfo.getId(), "wins", 1);
            statsAPI.addGlobalStat(winner, "wins", 1);
        }
    }
}
