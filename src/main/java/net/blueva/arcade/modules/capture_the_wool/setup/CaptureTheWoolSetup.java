package net.blueva.arcade.modules.capture_the_wool.setup;

import net.blueva.arcade.api.setup.GameSetupHandler;
import net.blueva.arcade.api.setup.SetupContext;
import net.blueva.arcade.api.setup.TabCompleteContext;
import net.blueva.arcade.api.setup.TabCompleteResult;
import net.blueva.arcade.modules.capture_the_wool.CaptureTheWoolModule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CaptureTheWoolSetup implements GameSetupHandler {

    private final CaptureTheWoolModule module;
    public CaptureTheWoolSetup(CaptureTheWoolModule module) {
        this.module = module;
    }

    @Override
    public boolean handle(SetupContext context) {
        return handleInternal(castSetupContext(context));
    }

    private boolean handleInternal(SetupContext<Player, CommandSender, Location> context) {
        String subcommand = context.getArg(context.getStartIndex() - 1);
        if ("wool".equalsIgnoreCase(subcommand)) {
            return handleWool(context);
        }
        if ("region".equalsIgnoreCase(subcommand)) {
            return handleRegion(context);
        }
        if ("team".equalsIgnoreCase(subcommand)) {
            String teamSubcommand = context.getHandlerArg(0);
            if ("spawn".equalsIgnoreCase(teamSubcommand)) {
                return handleTeamSpawn(context);
            }
            if ("zone".equalsIgnoreCase(teamSubcommand)) {
                return handleTeamZone(context);
            }
            return handleTeamConfig(context);
        }
        return handleTeamConfig(context);
    }

    @Override
    public TabCompleteResult tabComplete(TabCompleteContext context) {
        return tabCompleteInternal(castTabContext(context));
    }

    private TabCompleteResult tabCompleteInternal(TabCompleteContext<Player, CommandSender> context) {
        if (context.getRelativeArgIndex() == 0
                && "team".equalsIgnoreCase(context.getArg(context.getStartIndex() - 1))) {
            return TabCompleteResult.of("count", "size", "spawn", "zone");
        }
        if (context.getRelativeArgIndex() == 1
                && "team".equalsIgnoreCase(context.getArg(context.getStartIndex() - 1))
                && "zone".equalsIgnoreCase(context.getArg(context.getStartIndex()))) {
            return TabCompleteResult.of("create", "list", "delete");
        }
        if (context.getRelativeArgIndex() == 0 && "region".equalsIgnoreCase(context.getArg(context.getStartIndex() - 1))) {
            return TabCompleteResult.of("set", "clear");
        }
        if (context.getRelativeArgIndex() == 0 && "wool".equalsIgnoreCase(context.getArg(context.getStartIndex() - 1))) {
            return TabCompleteResult.of("create", "capture", "list", "clearcapture");
        }
        return TabCompleteResult.empty();
    }

    @Override
    public List<String> getSubcommands() {
        return List.of("team", "region", "wool");
    }

    private boolean handleTeamConfig(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(2)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    getSetupMessage("team.usage"));
            return true;
        }

        String setting = context.getHandlerArg(0);
        if (setting == null || (!setting.equalsIgnoreCase("count") && !setting.equalsIgnoreCase("size"))) {
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    getSetupMessage("team.usage"));
            return true;
        }

        String valueRaw = context.getHandlerArg(1);
        if (valueRaw == null || !isNumber(valueRaw)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    module.getCoreConfig().getLanguage("admin_commands.errors.invalid_number")
                            .replace("{value}", valueRaw == null ? "" : valueRaw));
            return true;
        }

        int value = Integer.parseInt(valueRaw);
        if (value <= 0) {
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    getSetupMessage("team.invalid_value")
                            .replace("{setting}", setting));
            return true;
        }

        if (setting.equalsIgnoreCase("count") && value < 2) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("team.invalid_count"));
            return true;
        }
        if (setting.equalsIgnoreCase("size") && value < 2) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("team.invalid_size"));
            return true;
        }

        int teamCount = context.getData().getInt("teams.count", 0);
        int teamSize = context.getData().getInt("teams.size", 0);
        if (setting.equalsIgnoreCase("count")) {
            teamCount = value;
        } else {
            teamSize = value;
        }

        int maxPlayers = context.getData().getArenaInt("arena.basic.max_players", 0);
        if (teamCount > 0 && teamSize > 0 && maxPlayers > 0 && teamCount * teamSize > maxPlayers) {
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    getSetupMessage("team.invalid_limit")
                            .replace("{max_players}", String.valueOf(maxPlayers)));
            return true;
        }

        context.getData().setTeamConfig(teamCount, teamSize);
        context.getData().save();

        context.getMessagesAPI().sendRaw(context.getPlayer(),
                getSetupMessage("team.success")
                        .replace("{game}", context.getGameId())
                        .replace("{arena_id}", String.valueOf(context.getArenaId()))
                        .replace("{setting}", setting.toLowerCase())
                        .replace("{value}", String.valueOf(value)));
        return true;
    }


    private boolean handleWool(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(1)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.usage"));
            return true;
        }

        String action = context.getHandlerArg(0);
        if (action == null) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.usage"));
            return true;
        }

        if ("list".equalsIgnoreCase(action)) {
            return handleWoolList(context);
        }

        if ("create".equalsIgnoreCase(action)) {
            if (!context.hasHandlerArgs(4)) {
                context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.create_usage"));
                return true;
            }
            String woolId = normalizeWoolId(context.getHandlerArg(1));
            String materialName = context.getHandlerArg(2);
            String ownerTeam = normalizeTeamId(context.getHandlerArg(3));

            if (woolId == null || materialName == null || ownerTeam == null) {
                context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.create_usage"));
                sendTeamIdRangeMessage(context);
                return true;
            }

            if (!isExistingTeam(context, ownerTeam)) {
                sendTeamIdRangeMessage(context);
                return true;
            }

            Material parsedMaterial;
            try {
                parsedMaterial = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.invalid_material")
                        .replace("{material}", materialName));
                return true;
            }

            String basePath = "game.play_area.wools." + woolId;
            Player player = context.getPlayer();
            if (player == null || !context.getSelection().hasCompleteSelection(player)) {
                context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.must_use_stick"));
                return true;
            }

            Location pos1 = context.getSelection().getPosition1(player);
            Location pos2 = context.getSelection().getPosition2(player);
            if (pos1 == null || pos2 == null || pos1.getBlockX() != pos2.getBlockX() || pos1.getBlockY() != pos2.getBlockY() || pos1.getBlockZ() != pos2.getBlockZ()) {
                context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.single_block_only"));
                return true;
            }

            context.getData().setString(basePath + ".owner_team", ownerTeam);
            context.getData().setString(basePath + ".material", parsedMaterial.name());
            context.getData().setLocation(basePath + ".spawn", pos1);
            addToRegistry(context, woolId);
            context.getData().save();
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.created")
                    .replace("{wool}", woolId)
                    .replace("{material}", parsedMaterial.name())
                    .replace("{team}", ownerTeam));
            return true;
        }

        if ("clearcapture".equalsIgnoreCase(action)) {
            if (!context.hasHandlerArgs(2)) {
                context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.clear_capture_usage"));
                return true;
            }
            String woolId = normalizeWoolId(context.getHandlerArg(1));
            if (woolId == null) {
                context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.clear_capture_usage"));
                return true;
            }
            String basePath = "game.play_area.wools." + woolId;
            if (normalizeTeamId(context.getData().getString(basePath + ".owner_team")) == null) {
                context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.not_found")
                        .replace("{wool}", woolId));
                return true;
            }
            context.getData().remove(basePath + ".capture_team");
            context.getData().remove(basePath + ".capture_teams");
            context.getData().remove(basePath + ".capture");
            context.getData().save();
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.capture_cleared")
                    .replace("{wool}", woolId));
            return true;
        }

        if (!"capture".equalsIgnoreCase(action)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.usage"));
            return true;
        }

        if (!context.hasHandlerArgs(3)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.capture_usage"));
            return true;
        }

        String woolId = normalizeWoolId(context.getHandlerArg(1));
        String captureTeam = normalizeTeamId(context.getHandlerArg(2));
        if (woolId == null || captureTeam == null) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.capture_usage"));
            sendTeamIdRangeMessage(context);
            return true;
        }

        String basePath = "game.play_area.wools." + woolId;
        String ownerTeam = normalizeTeamId(context.getData().getString(basePath + ".owner_team"));
        if (ownerTeam == null) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.not_found")
                    .replace("{wool}", woolId));
            return true;
        }

        if (!isExistingTeam(context, captureTeam)) {
            sendTeamIdRangeMessage(context);
            return true;
        }

        if (ownerTeam.equalsIgnoreCase(captureTeam)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.capture_owner_forbidden")
                    .replace("{wool}", woolId)
                    .replace("{team}", ownerTeam));
            return true;
        }

        Player player = context.getPlayer();
        if (player == null || !context.getSelection().hasCompleteSelection(player)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.must_use_stick"));
            return true;
        }

        Location pos1 = context.getSelection().getPosition1(player);
        Location pos2 = context.getSelection().getPosition2(player);
        if (pos1 == null || pos2 == null || pos1.getBlockX() != pos2.getBlockX() || pos1.getBlockY() != pos2.getBlockY() || pos1.getBlockZ() != pos2.getBlockZ()) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.single_block_only"));
            return true;
        }

        Set<String> captureTeams = parseTeamList(context.getData().getString(basePath + ".capture_teams"));
        String legacyCaptureTeam = normalizeTeamId(context.getData().getString(basePath + ".capture_team"));
        if (legacyCaptureTeam != null) {
            captureTeams.add(legacyCaptureTeam);
        }
        captureTeams.add(captureTeam);

        context.getData().setString(basePath + ".capture_team", captureTeam);
        context.getData().setString(basePath + ".capture_teams", String.join(",", captureTeams));
        context.getData().setLocation(basePath + ".capture", pos1);
        context.getData().save();
        context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.capture_set")
                .replace("{owner_team}", ownerTeam)
                .replace("{capture_team}", String.join(",", captureTeams))
                .replace("{wool}", woolId));
        return true;
    }

    private boolean handleWoolList(SetupContext<Player, CommandSender, Location> context) {
        String registryRaw = context.getData().getString("game.play_area.wool_registry");
        if (registryRaw == null || registryRaw.isBlank()) {
            registryRaw = context.getData().getString("game.wool_registry");
        }
        if (registryRaw == null || registryRaw.isBlank()) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.list_empty"));
            return true;
        }

        Set<String> entries = parseRegistry(registryRaw);
        if (entries.isEmpty()) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.list_empty"));
            return true;
        }

        context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("wool.list_header"));
        for (String woolId : entries) {
            String basePath = "game.play_area.wools." + woolId;
            String ownerTeam = context.getData().getString(basePath + ".owner_team");
            if (ownerTeam == null || ownerTeam.isBlank()) {
                basePath = "game.wools." + woolId;
                ownerTeam = context.getData().getString(basePath + ".owner_team");
            }
            String captureTeam = context.getData().getString(basePath + ".capture_teams");
            if (captureTeam == null || captureTeam.isBlank()) {
                captureTeam = context.getData().getString(basePath + ".capture_team");
            }
            String material = context.getData().getString(basePath + ".material");
            if (ownerTeam == null || ownerTeam.isBlank()) {
                continue;
            }
            String line = getSetupMessage("wool.list_line")
                    .replace("{team}", ownerTeam)
                    .replace("{wool}", woolId)
                    .replace("{capture_team}", captureTeam == null ? "-" : captureTeam)
                    .replace("{material}", material == null ? "-" : material);
            context.getMessagesAPI().sendRaw(context.getPlayer(), line);
        }
        return true;
    }
    private boolean isExistingTeam(SetupContext<Player, CommandSender, Location> context, String teamId) {
        int teamCount = context.getData().getInt("teams.count", 0);
        if (teamCount <= 0) {
            return false;
        }
        for (int i = 1; i <= teamCount; i++) {
            if (String.valueOf(i).equalsIgnoreCase(teamId)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeWoolId(String woolIdRaw) {
        if (woolIdRaw == null) {
            return null;
        }
        String value = woolIdRaw.trim().toLowerCase(Locale.ROOT);
        return isNumericId(value) ? value : null;
    }

    private String normalizeTeamId(String teamRaw) {
        if (teamRaw == null) {
            return null;
        }
        String value = teamRaw.trim().toLowerCase(Locale.ROOT);
        return isNumericId(value) ? value : null;
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
        try {
            return Integer.parseInt(raw) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private Set<String> parseTeamList(String raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String split : raw.split(",")) {
            String value = normalizeTeamId(split);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    private void addToRegistry(SetupContext<Player, CommandSender, Location> context, String woolId) {
        String registryRaw = context.getData().getString("game.play_area.wool_registry");
        if (registryRaw == null) {
            registryRaw = context.getData().getString("game.wool_registry");
        }
        Set<String> registry = parseRegistry(registryRaw);
        registry.add(woolId);
        context.getData().setString("game.play_area.wool_registry", String.join(",", registry));
    }

    private Set<String> parseRegistry(String raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        String[] split = raw.split(",");
        for (String entry : split) {
            String parsed = normalizeWoolId(entry);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    private boolean handleTeamSpawn(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(2)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("team_spawn.usage"));
            return true;
        }

        String teamId = normalizeTeamId(context.getHandlerArg(1));
        if (teamId == null) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("team_spawn.usage"));
            sendTeamIdRangeMessage(context);
            return true;
        }

        if (!isExistingTeam(context, teamId)) {
            sendTeamIdRangeMessage(context);
            return true;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        Location location = player.getLocation();
        String path = "game.play_area.team_spawns." + teamId.toLowerCase();
        context.getData().setLocation(path, location);
        context.getData().save();

        context.getMessagesAPI().sendRaw(player, getSetupMessage("team_spawn.set")
                .replace("{team}", teamId));
        return true;
    }

    private boolean handleTeamZone(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(2)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("team_zone.usage"));
            return true;
        }

        String actionOrTeam = context.getHandlerArg(1);
        String action = actionOrTeam == null ? "" : actionOrTeam.toLowerCase(Locale.ROOT);

        if ("list".equals(action)) {
            return handleTeamZoneList(context);
        }

        if ("delete".equals(action)) {
            if (!context.hasHandlerArgs(4)) {
                context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("team_zone.delete_usage"));
                return true;
            }
            String teamId = normalizeTeamId(context.getHandlerArg(2));
            String indexRaw = context.getHandlerArg(3);
            if (teamId == null || !isNumber(indexRaw)) {
                context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("team_zone.delete_usage"));
                sendTeamIdRangeMessage(context);
                return true;
            }
            if (!isExistingTeam(context, teamId)) {
                sendTeamIdRangeMessage(context);
                return true;
            }
            int index = Integer.parseInt(indexRaw);
            String zonePath = "game.play_area.restricted_zones." + teamId.toLowerCase() + "." + index;
            if (!context.getData().has(zonePath + ".min")) {
                context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("team_zone.not_found")
                        .replace("{team}", teamId)
                        .replace("{index}", String.valueOf(index)));
                return true;
            }
            context.getData().remove(zonePath);
            context.getData().save();
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("team_zone.deleted")
                    .replace("{team}", teamId)
                    .replace("{index}", String.valueOf(index)));
            return true;
        }

        String teamArg = "create".equals(action) ? context.getHandlerArg(2) : context.getHandlerArg(1);
        String teamId = normalizeTeamId(teamArg);
        if (teamId == null) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("team_zone.create_usage"));
            sendTeamIdRangeMessage(context);
            return true;
        }

        if (!isExistingTeam(context, teamId)) {
            sendTeamIdRangeMessage(context);
            return true;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        if (!context.getSelection().hasCompleteSelection(player)) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage("team_zone.must_use_stick"));
            return true;
        }

        Location pos1 = context.getSelection().getPosition1(player);
        Location pos2 = context.getSelection().getPosition2(player);

        int index = 1;
        String basePath = "game.play_area.restricted_zones." + teamId.toLowerCase();
        while (context.getData().has(basePath + "." + index + ".min")) {
            index++;
        }

        context.getData().setLocation(basePath + "." + index + ".min", pos1);
        context.getData().setLocation(basePath + "." + index + ".max", pos2);
        context.getData().save();

        context.getMessagesAPI().sendRaw(player, getSetupMessage("team_zone.set")
                .replace("{team}", teamId)
                .replace("{index}", String.valueOf(index)));
        return true;
    }

    private boolean handleTeamZoneList(SetupContext<Player, CommandSender, Location> context) {
        context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("team_zone.list_header"));
        int teamCount = context.getData().getInt("teams.count", 0);
        int listed = 0;
        for (int team = 1; team <= teamCount; team++) {
            String teamId = String.valueOf(team);
            String basePath = "game.play_area.restricted_zones." + teamId;
            for (int index = 1; index <= 64; index++) {
                if (!context.getData().has(basePath + "." + index + ".min")) {
                    continue;
                }
                context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("team_zone.list_line")
                        .replace("{team}", teamId)
                        .replace("{index}", String.valueOf(index)));
                listed++;
            }
        }
        if (listed == 0) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage("team_zone.list_empty"));
        }
        return true;
    }

    private boolean handleRegion(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(1)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    getSetupMessage("region.usage"));
            return true;
        }

        String action = context.getHandlerArg(0);
        if (action == null) {
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    getSetupMessage("region.usage"));
            return true;
        }

        if ("clear".equalsIgnoreCase(action)) {
            context.getData().remove("game.play_area");
            context.getData().remove("regeneration.regions");
            context.getData().save();
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    getSetupMessage("region.cleared"));
            return true;
        }

        if (!"set".equalsIgnoreCase(action)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    getSetupMessage("region.usage"));
            return true;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        if (!context.getSelection().hasCompleteSelection(player)) {
            context.getMessagesAPI().sendRaw(player,
                    getSetupMessage("region.must_use_stick"));
            return true;
        }

        Location pos1 = context.getSelection().getPosition1(player);
        Location pos2 = context.getSelection().getPosition2(player);

        context.getData().registerRegenerationRegion("game.play_area", pos1, pos2);
        context.getData().save();

        int x = (int) Math.abs(pos2.getX() - pos1.getX()) + 1;
        int y = (int) Math.abs(pos2.getY() - pos1.getY()) + 1;
        int z = (int) Math.abs(pos2.getZ() - pos1.getZ()) + 1;
        int blocks = x * y * z;

        context.getMessagesAPI().sendRaw(player,
                getSetupMessage("region.set")
                        .replace("{blocks}", String.valueOf(blocks))
                        .replace("{x}", String.valueOf(x))
                        .replace("{y}", String.valueOf(y))
                        .replace("{z}", String.valueOf(z)));
        return true;
    }

    private String getSetupMessage(String key) {
        String message = module.getModuleConfig().getStringFrom("language.yml", "setup_messages." + key);
        if (message == null) {
            return "";
        }
        return message;
    }

    private boolean isNumber(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void sendTeamIdRangeMessage(SetupContext<Player, CommandSender, Location> context) {
        int teamCount = context.getData().getInt("teams.count", 0);
        String max = teamCount > 0 ? String.valueOf(teamCount) : "N";
        context.getMessagesAPI().sendRaw(context.getPlayer(),
                getSetupMessage("team.numeric_ids_only")
                        .replace("{min}", "1")
                        .replace("{max}", max));
    }

    @SuppressWarnings("unchecked")
    private SetupContext<Player, CommandSender, Location> castSetupContext(SetupContext context) {
        return (SetupContext<Player, CommandSender, Location>) context;
    }

    @SuppressWarnings("unchecked")
    private TabCompleteContext<Player, CommandSender> castTabContext(TabCompleteContext context) {
        return (TabCompleteContext<Player, CommandSender>) context;
    }
}
