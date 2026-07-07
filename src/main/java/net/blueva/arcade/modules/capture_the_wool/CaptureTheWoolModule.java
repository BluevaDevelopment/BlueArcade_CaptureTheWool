package net.blueva.arcade.modules.capture_the_wool;

import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.achievements.AchievementsAPI;
import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.events.CustomEventRegistry;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GameModule;
import net.blueva.arcade.api.game.GameResult;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.setup.SetupRequirement;
import net.blueva.arcade.api.stats.StatDefinition;
import net.blueva.arcade.api.stats.StatScope;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.ui.ItemAPI;
import net.blueva.arcade.api.ui.MenuAPI;
import net.blueva.arcade.api.ui.VoteMenuAPI;
import net.blueva.arcade.modules.capture_the_wool.game.CaptureTheWoolGame;
import net.blueva.arcade.modules.capture_the_wool.listener.CaptureTheWoolListener;
import net.blueva.arcade.modules.capture_the_wool.listener.CaptureTheWoolVoteListener;
import net.blueva.arcade.modules.capture_the_wool.setup.CaptureTheWoolSetup;
import net.blueva.arcade.modules.capture_the_wool.support.vote.CaptureTheWoolVoteService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import net.blueva.arcade.api.setup.ModuleSetupCommand;
import net.blueva.arcade.api.setup.ModuleSetupMetadata;
import net.blueva.arcade.api.setup.ModuleSetupStep;
import net.blueva.arcade.api.setup.ModuleSetupStatusCheck;
import java.util.List;

public class CaptureTheWoolModule implements GameModule<Player, Location, World, Material, ItemStack, Sound, Block, Entity, Listener, EventPriority> {

    private ModuleConfigAPI moduleConfig;
    private CoreConfigAPI coreConfig;
    private ModuleInfo moduleInfo;
    private StatsAPI statsAPI;
    private MenuAPI<Player, Material> menuAPI;
    private ItemAPI<Player, ItemStack, Material> itemAPI;
    private CaptureTheWoolGame game;

    @Override
    public void onLoad() {
        moduleInfo = ModuleAPI.getModuleInfo("capture_the_wool");
        if (moduleInfo == null) {
            throw new IllegalStateException("ModuleInfo not available for CaptureTheWool module");
        }

        moduleConfig = ModuleAPI.getModuleConfig(moduleInfo.getId());
        coreConfig = ModuleAPI.getCoreConfig();
        statsAPI = ModuleAPI.getStatsAPI();
        menuAPI = ModuleAPI.getMenuAPI();
        @SuppressWarnings("unchecked")
        ItemAPI<Player, ItemStack, Material> resolvedItemAPI = (ItemAPI<Player, ItemStack, Material>) ModuleAPI.getItemAPI();
        itemAPI = resolvedItemAPI;

        registerConfigs();
        registerStats();
        registerAchievements();

        CaptureTheWoolVoteService voteService = new CaptureTheWoolVoteService(moduleConfig, menuAPI, itemAPI, moduleInfo.getId());
        game = new CaptureTheWoolGame(moduleInfo, moduleConfig, coreConfig, statsAPI, voteService);
        voteService.setGame(game);
        registerMenuActions();
        voteService.registerWaitingItem();
        voteService.registerClickHandler(game);
        ModuleAPI.getSetupAPI().registerHandler(moduleInfo.getId(), new CaptureTheWoolSetup(this));

        VoteMenuAPI voteMenu = ModuleAPI.getVoteMenuAPI();
        if (voteMenu != null) {
            voteMenu.registerGame(
                    moduleInfo.getId(),
                    Material.valueOf(moduleConfig.getString("menus.vote.item")),
                    moduleConfig.getTranslation(null, "vote_menu.name"),
                    moduleConfig.getTranslationList(null, "vote_menu.lore")
            );
        }
    }

    @Override
    public void onStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        game.startGame(context);
    }

    @Override
    public void onCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                int secondsLeft) {
        game.handleCountdownTick(context, secondsLeft);
    }

    @Override
    public void onCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        game.handleCountdownFinish(context);
    }

    @Override
    public boolean freezePlayersOnCountdown() {
        return false;
    }

    @Override
    public boolean allowJoinInProgress() {
        return true;
    }

    @Override
    public Set<SetupRequirement> getDisabledRequirements() {
        return Set.of(SetupRequirement.SPAWNS, SetupRequirement.TIME);
    }

    @Override
    public void onGameStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        game.beginPlaying(context);
    }

    @Override
    public void onEnd(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                      GameResult<Player> result) {
        game.finishGame(context);
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.shutdown();
        }
        if (menuAPI != null && moduleInfo != null) {
            menuAPI.unregisterModuleMenuAPI(moduleInfo.getId());
            menuAPI.unregisterModuleMenuAPI("capture");
        }
        if (itemAPI != null) {
            itemAPI.unregisterWaitingItem("capture_the_wool_vote_settings");
            itemAPI.unregisterClickHandler("capture_the_wool_vote_settings");
        }
    }

    @Override
    public void registerEvents(CustomEventRegistry<Listener, EventPriority> registry) {
        registry.register(new CaptureTheWoolListener(game));
        registry.register(new CaptureTheWoolVoteListener(game));
    }

    @Override
    public Map<String, String> getCustomPlaceholders(Player player) {
        return game.getPlaceholders(player);
    }

    public ModuleConfigAPI getModuleConfig() {
        return moduleConfig;
    }

    public CoreConfigAPI getCoreConfig() {
        return coreConfig;
    }

    public ModuleInfo getModuleInfo() {
        return moduleInfo;
    }

    private void registerConfigs() {
        moduleConfig.register("settings.yml");
        moduleConfig.register("achievements.yml");
        moduleConfig.register("menus/java/capture_the_wool_vote_main.yml");
        moduleConfig.register("menus/java/capture_the_wool_vote_hearts.yml");
        moduleConfig.register("menus/java/capture_the_wool_vote_time.yml");
        moduleConfig.register("menus/java/capture_the_wool_vote_weather.yml");
        moduleConfig.register("menus/bedrock/capture_the_wool_vote_main.yml");
        moduleConfig.register("menus/bedrock/capture_the_wool_vote_hearts.yml");
        moduleConfig.register("menus/bedrock/capture_the_wool_vote_time.yml");
        moduleConfig.register("menus/bedrock/capture_the_wool_vote_weather.yml");
    }

    private void registerMenuActions() {
        if (menuAPI == null) {
            return;
        }
        menuAPI.registerModuleActionHandler(moduleInfo.getId(), (player, payload) -> {
            if (payload == null || payload.isBlank()) {
                return false;
            }
            String[] args = payload.trim().split("\\s+");
            return game.handleVoteCommand(player, args);
        });
    }

    private void registerStats() {
        if (statsAPI == null) {
            return;
        }

        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("wins", moduleConfig.getTranslation(null, "stats.labels.wins"), moduleConfig.getTranslation(null, "stats.descriptions.wins"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("games_played", moduleConfig.getTranslation(null, "stats.labels.games_played"), moduleConfig.getTranslation(null, "stats.descriptions.games_played"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("kills", moduleConfig.getTranslation(null, "stats.labels.kills"), moduleConfig.getTranslation(null, "stats.descriptions.kills"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("chests_looted", moduleConfig.getTranslation(null, "stats.labels.chests_looted"), moduleConfig.getTranslation(null, "stats.descriptions.chests_looted"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("deaths", moduleConfig.getTranslation(null, "stats.labels.deaths"), moduleConfig.getTranslation(null, "stats.descriptions.deaths"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("wools_stolen", moduleConfig.getTranslation(null, "stats.labels.wools_stolen"), moduleConfig.getTranslation(null, "stats.descriptions.wools_stolen"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("wools_captured", moduleConfig.getTranslation(null, "stats.labels.wools_captured"), moduleConfig.getTranslation(null, "stats.descriptions.wools_captured"), StatScope.MODULE));
    }

    private void registerAchievements() {
        AchievementsAPI achievementsAPI = ModuleAPI.getAchievementsAPI();
        if (achievementsAPI != null) {
            achievementsAPI.registerModuleAchievements(moduleInfo.getId(), "achievements.yml");
        }
    }

    @Override
    public ModuleSetupMetadata getSetupMetadata() {
        return new ModuleSetupMetadata() {

            @Override
            public List<ModuleSetupStep> getSetupSteps() {
                return List.of(
                        new ModuleSetupStep("region", true, "Configure Region", "Configure the module-specific region setup data.", List.of("/baa game <arena> capture_the_wool region"), "selection region"),
                        new ModuleSetupStep("team", true, "Configure Team", "Configure the module-specific team setup data.", List.of("/baa game <arena> capture_the_wool team"), "team count and team size"),
                        new ModuleSetupStep("wool", true, "Configure Wool", "Configure the module-specific wool setup data.", List.of("/baa game <arena> capture_the_wool wool"), "wool block and capture data")
                );
            }

            @Override
            public List<ModuleSetupCommand> getSetupCommands() {
                return List.of(
                        new ModuleSetupCommand("region", "/baa game <arena> capture_the_wool region", "Configure region setup data.", true),
                        new ModuleSetupCommand("team", "/baa game <arena> capture_the_wool team", "Configure team setup data.", true),
                        new ModuleSetupCommand("wool", "/baa game <arena> capture_the_wool wool", "Configure wool setup data.", true)
                );
            }

            @Override
            public List<ModuleSetupStatusCheck<?, ?, ?>> getStatusChecks() {
                return List.of(
                        new ModuleSetupStatusCheck<>("region", true, "Select the play area region.", context -> (context.getData().has("game.play_area.bounds.min.x") && context.getData().has("game.play_area.bounds.max.x")) || (context.getData().has("game.region.bounds.min.x") && context.getData().has("game.region.bounds.max.x"))),
                        new ModuleSetupStatusCheck<>("team", true, "Set team count and team size.", context -> context.getData().getInt("teams.count", 0) > 0 && context.getData().getInt("teams.size", 0) > 0),
                        new ModuleSetupStatusCheck<>("wool", true, "Create at least one wool objective.", context -> context.getData().has("game.play_area.wool_registry"))
                );
            }
        };
    }

}
