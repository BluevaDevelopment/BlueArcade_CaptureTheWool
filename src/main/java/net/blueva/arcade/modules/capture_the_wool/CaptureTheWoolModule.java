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
                    moduleConfig.getStringFrom("language.yml", "vote_menu.name"),
                    moduleConfig.getStringListFrom("language.yml", "vote_menu.lore")
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
        moduleConfig.register("language.yml", 4);
        moduleConfig.register("settings.yml", 2);
        moduleConfig.register("achievements.yml", 1);
        moduleConfig.register("menus/java/capture_the_wool_vote_main.yml", 3);
        moduleConfig.register("menus/java/capture_the_wool_vote_hearts.yml", 2);
        moduleConfig.register("menus/java/capture_the_wool_vote_time.yml", 2);
        moduleConfig.register("menus/java/capture_the_wool_vote_weather.yml", 2);
        moduleConfig.register("menus/bedrock/capture_the_wool_vote_main.yml", 2);
        moduleConfig.register("menus/bedrock/capture_the_wool_vote_hearts.yml", 1);
        moduleConfig.register("menus/bedrock/capture_the_wool_vote_time.yml", 1);
        moduleConfig.register("menus/bedrock/capture_the_wool_vote_weather.yml", 1);
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

        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("wins", moduleConfig.getStringFrom("language.yml", "stats.labels.wins", "Wins"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.wins", "CaptureTheWool victories"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("games_played", moduleConfig.getStringFrom("language.yml", "stats.labels.games_played", "Games Played"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.games_played", "CaptureTheWool matches played"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("kills", moduleConfig.getStringFrom("language.yml", "stats.labels.kills", "Eliminations"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.kills", "Opponents eliminated in CaptureTheWool"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("chests_looted", moduleConfig.getStringFrom("language.yml", "stats.labels.chests_looted", "Chests Looted"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.chests_looted", "Chests opened in CaptureTheWool"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("deaths", moduleConfig.getStringFrom("language.yml", "stats.labels.deaths", "Deaths"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.deaths", "Times eliminated in CaptureTheWool"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("wools_stolen", moduleConfig.getStringFrom("language.yml", "stats.labels.wools_stolen", "Wools Stolen"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.wools_stolen", "Enemy wools picked up in CaptureTheWool"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("wools_captured", moduleConfig.getStringFrom("language.yml", "stats.labels.wools_captured", "Wools Captured"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.wools_captured", "Objective wools captured in CaptureTheWool"), StatScope.MODULE));
    }

    private void registerAchievements() {
        AchievementsAPI achievementsAPI = ModuleAPI.getAchievementsAPI();
        if (achievementsAPI != null) {
            achievementsAPI.registerModuleAchievements(moduleInfo.getId(), "achievements.yml");
        }
    }
}
