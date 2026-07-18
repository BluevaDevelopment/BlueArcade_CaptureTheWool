package net.blueva.arcade.modules.capture_the_wool.support.vote;

import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.arena.ArenaAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.api.ui.ItemAPI;
import net.blueva.arcade.api.ui.LobbyItemDefinition;
import net.blueva.arcade.api.ui.MenuAPI;
import net.blueva.arcade.api.ui.MessageAPI;
import net.blueva.arcade.api.ui.menu.MenuDefinition;
import net.blueva.arcade.api.utils.PlayerUtil;
import net.blueva.arcade.modules.capture_the_wool.game.CaptureTheWoolGame;
import net.blueva.arcade.modules.capture_the_wool.state.ArenaState;
import net.blueva.arcade.modules.capture_the_wool.state.VoteState;
import org.bukkit.Bukkit;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CaptureTheWoolVoteService {

    private static final String VOTE_PERMISSION_BASE = "bluearcade.capture_the_wool.votes";
    private static final String WAITING_ITEM_ID = "capture_the_wool_vote_settings";
    public static final String COMMAND = "capture_the_woolvote";
    public static final String MENU_MAIN = "vote_main";
    public static final String MENU_HEARTS = "vote_hearts";
    public static final String MENU_TIME = "vote_time";
    public static final String MENU_WEATHER = "vote_weather";

    private static final Set<String> HEART_OPTIONS = Set.of("10", "20", "30");
    private static final Set<String> TIME_OPTIONS = Set.of("day", "night", "sunset", "sunrise");
    private static final Set<String> WEATHER_OPTIONS = Set.of("sunny", "rainy");

    private final ModuleConfigAPI moduleConfig;
    private final MenuAPI<Player, Material> menuAPI;
    private final ItemAPI<Player, ItemStack, Material> itemAPI;
    private final String moduleId;
    private final CaptureTheWoolVoteMenuRepository menuRepository;
    private final Map<Integer, VoteState> waitingVoteStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> voteCooldowns = new ConcurrentHashMap<>();
    private CaptureTheWoolGame game;

    public CaptureTheWoolVoteService(ModuleConfigAPI moduleConfig,
                              MenuAPI<Player, Material> menuAPI,
                              ItemAPI<Player, ItemStack, Material> itemAPI,
                              String moduleId) {
        this.moduleConfig = moduleConfig;
        this.menuAPI = menuAPI;
        this.itemAPI = itemAPI;
        this.moduleId = moduleId;
        this.menuRepository = new CaptureTheWoolVoteMenuRepository(moduleConfig);
        this.menuRepository.loadMenus();
        registerMenusWithCore();
    }

    private void registerMenusWithCore() {
        if (menuAPI != null) {
            CaptureTheWoolMenuAPI captureTheWoolMenuAPI = new CaptureTheWoolMenuAPI(menuAPI, this);
            menuAPI.registerModuleMenuAPI(moduleId, captureTheWoolMenuAPI);
            menuAPI.registerModuleMenuAPI("capture", captureTheWoolMenuAPI);
        }
    }

    public VoteState createVoteState() {
        Map<VoteCategory, String> defaults = new EnumMap<>(VoteCategory.class);
        defaults.put(VoteCategory.HEARTS, normalizeOption(moduleConfig.getString("votes.defaults.hearts", "10"), HEART_OPTIONS, "10"));
        defaults.put(VoteCategory.TIME, normalizeOption(moduleConfig.getString("votes.defaults.time", "day"), TIME_OPTIONS, "day"));
        defaults.put(VoteCategory.WEATHER, normalizeOption(moduleConfig.getString("votes.defaults.weather", "sunny"), WEATHER_OPTIONS, "sunny"));
        return new VoteState(defaults);
    }

    public void setGame(CaptureTheWoolGame game) {
        this.game = game;
    }

    public VoteState getWaitingVoteState(int arenaId) {
        return waitingVoteStates.computeIfAbsent(arenaId, id -> createVoteState());
    }

    public void clearWaitingVote(int arenaId, UUID playerId) {
        voteCooldowns.remove(playerId);
        VoteState state = waitingVoteStates.get(arenaId);
        if (state == null) {
            return;
        }
        state.clearPlayerVotes(playerId);
        if (state.getVoterIds().isEmpty()) {
            waitingVoteStates.remove(arenaId);
        }
    }

    public void cleanStaleVotes() {
        @SuppressWarnings("unchecked")
        PlayerUtil<Player> playerUtil = (PlayerUtil<Player>) ModuleAPI.getPlayerUtil();
        if (playerUtil == null) {
            return;
        }

        for (Map.Entry<Integer, VoteState> entry : new ArrayList<>(waitingVoteStates.entrySet())) {
            cleanStaleVotesForArena(entry.getValue(), entry.getKey());
            if (entry.getValue().getVoterIds().isEmpty()) {
                waitingVoteStates.remove(entry.getKey());
            }
        }
    }

    private void cleanStaleVotesForArena(VoteState state, int arenaId) {
        @SuppressWarnings("unchecked")
        PlayerUtil<Player> playerUtil = (PlayerUtil<Player>) ModuleAPI.getPlayerUtil();
        if (playerUtil == null || state == null) {
            return;
        }

        for (UUID playerId : new ArrayList<>(state.getVoterIds())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                state.clearPlayerVotes(playerId);
                continue;
            }
            Integer playerArena = playerUtil.getPlayerArena(player);
            if (playerArena == null || playerArena != arenaId) {
                state.clearPlayerVotes(playerId);
            }
        }
    }

    private Integer getPlayerArenaId(Player player) {
        if (player == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        PlayerUtil<Player> playerUtil = (PlayerUtil<Player>) ModuleAPI.getPlayerUtil();
        if (playerUtil == null) {
            return null;
        }
        return playerUtil.getPlayerArena(player);
    }

    public void applyPendingVotes(ArenaState state, List<Player> players) {
        if (state == null || players == null || players.isEmpty()) {
            return;
        }
        VoteState voteState = state.getVoteState();
        if (voteState == null) {
            return;
        }

        int arenaId = state.getContext().getArenaId();
        VoteState waiting = getWaitingVoteState(arenaId);
        cleanStaleVotesForArena(waiting, arenaId);

        for (Player player : players) {
            if (player == null) {
                continue;
            }
            for (VoteCategory category : VoteCategory.values()) {
                String option = waiting.getPlayerVote(player.getUniqueId(), category);
                if (option != null) {
                    voteState.castVote(player.getUniqueId(), category, option);
                }
            }
            voteCooldowns.remove(player.getUniqueId());
        }
        waitingVoteStates.remove(arenaId);
    }

    public void registerWaitingItem() {
        if (itemAPI == null || moduleConfig == null) {
            return;
        }
        if (!isWaitingItemEnabled()) {
            unregisterWaitingItem();
            return;
        }

        String materialName = moduleConfig.getString("waiting_items.vote_settings.material", "NAME_TAG");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            material = Material.NAME_TAG;
        }
        LobbyItemDefinition<Material> definition = new LobbyItemDefinition<>(
                WAITING_ITEM_ID,
                material,
                moduleConfig.getInt("waiting_items.vote_settings.slot"),
                moduleConfig.getString("waiting_items.vote_settings.display_name"),
                moduleConfig.getStringList("waiting_items.vote_settings.lore"),
                List.of(),
                true
        );
        itemAPI.registerWaitingItem(moduleId, definition);
    }

    public void registerClickHandler(CaptureTheWoolGame game) {
        if (itemAPI == null) {
            return;
        }
        if (!isWaitingItemEnabled()) {
            itemAPI.unregisterClickHandler(WAITING_ITEM_ID);
            return;
        }
        itemAPI.registerClickHandler(WAITING_ITEM_ID,
                player -> game.handleVoteCommand(player, new String[]{"menu", "main"}));
    }

    public void unregisterWaitingItem() {
        if (itemAPI == null) {
            return;
        }
        itemAPI.unregisterWaitingItem(WAITING_ITEM_ID);
        itemAPI.unregisterClickHandler(WAITING_ITEM_ID);
    }

    private boolean isWaitingItemEnabled() {
        return moduleConfig != null && moduleConfig.getBoolean("waiting_items.vote_settings.enabled", true);
    }

    public boolean handleVoteCommand(Player player,
                                     GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                     ArenaState state,
                                     String[] args) {
        if (player == null || context == null || state == null) {
            return false;
        }

        GamePhase phase = context.getPhase();
        if (phase == GamePhase.PLAYING || phase == GamePhase.ENDING || phase == GamePhase.FINISHED) {
            sendMessage(context, player, "votes.messages.not_available");
            return true;
        }

        String[] safeArgs = args != null ? args : new String[0];
        if (safeArgs.length == 0) {
            return openMenu(player, state, MENU_MAIN);
        }

        String action = safeArgs[0].toLowerCase(Locale.ROOT);
        if (action.equals("menu")) {
            return openMenu(player, state, mapMenuId(safeArgs.length > 1 ? safeArgs[1] : "main"));
        }
        if (action.equals("vote")) {
            return castVote(player, context, state.getVoteState(), safeArgs);
        }
        return openMenu(player, state, MENU_MAIN);
    }

    public boolean handleVoteCommandWithoutContext(Player player, String[] args) {
        if (player == null) {
            return false;
        }

        Integer arenaId = getPlayerArenaId(player);
        if (arenaId == null) {
            return true;
        }

        VoteState waiting = getWaitingVoteState(arenaId);
        cleanStaleVotesForArena(waiting, arenaId);

        String[] safeArgs = args != null ? args : new String[0];
        if (safeArgs.length == 0) {
            return openMenuWaiting(player);
        }

        String action = safeArgs[0].toLowerCase(Locale.ROOT);
        if (action.equals("menu")) {
            return openMenuWaiting(player);
        }
        if (action.equals("vote")) {
            if (!castWaitingVote(player, safeArgs, waiting)) {
                return true;
            }
            return openMenuWaiting(player);
        }
        return false;
    }

    public void applyVotes(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                           ArenaState state) {
        if (context == null || state == null || state.getVoteState() == null) {
            return;
        }

        VoteState voteState = state.getVoteState();
        String hearts = voteState.resolveWinner(VoteCategory.HEARTS);
        String time = voteState.resolveWinner(VoteCategory.TIME);
        String weather = voteState.resolveWinner(VoteCategory.WEATHER);

        state.setSelectedHearts(resolveHearts(hearts));
        state.setSelectedTime(time);
        state.setSelectedWeather(weather);

        applyHearts(context, state.getSelectedHearts());
        applyWorldTime(context, time);
        applyWeather(context, weather);
    }

    public void broadcastVoteResults(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                     ArenaState state) {
        if (context == null || state == null || state.getVoteState() == null) {
            return;
        }
        VoteState voteState = state.getVoteState();
        broadcastResultForCategory(context, voteState, VoteCategory.HEARTS, "votes.messages.selected.hearts");
        broadcastResultForCategory(context, voteState, VoteCategory.TIME, "votes.messages.selected.time");
        broadcastResultForCategory(context, voteState, VoteCategory.WEATHER, "votes.messages.selected.weather");
    }

    public boolean openMenuWithDefaults(Player player, String[] args) {
        Integer arenaId = getPlayerArenaId(player);
        VoteState voteState;
        if (arenaId == null) {
            voteState = createVoteState();
        } else {
            voteState = getWaitingVoteState(arenaId);
            cleanStaleVotesForArena(voteState, arenaId);
        }
        return openMenuWithDefaults(player, voteState, args);
    }

    public boolean openMenuWithDefaults(Player player, VoteState voteState, String[] args) {
        if (menuAPI == null || player == null) {
            return false;
        }
        String menuId = MENU_MAIN;
        if (args != null && args.length > 1 && args[0].equalsIgnoreCase("menu")) {
            menuId = mapMenuId(args[1]);
        }
        return openMenu(player, voteState, menuId);
    }

    private boolean openMenuWaiting(Player player) {
        Integer arenaId = getPlayerArenaId(player);
        if (arenaId == null) {
            return openMenu(player, createVoteState(), MENU_MAIN);
        }
        VoteState waiting = getWaitingVoteState(arenaId);
        cleanStaleVotesForArena(waiting, arenaId);
        return openMenu(player, waiting, MENU_MAIN);
    }

    public boolean openMenu(Player player, ArenaState state, String menuId) {
        return openMenu(player, state != null ? state.getVoteState() : null, menuId);
    }

    public boolean openMenu(Player player, VoteState voteState, String menuId) {
        if (menuAPI == null) {
            return false;
        }
        MenuDefinition<Material> menu = menuRepository.getMenu(menuId);
        if (menu == null) {
            return false;
        }
        return menuAPI.openMenu(player, menu, buildPlaceholders(player, voteState));
    }

    public Map<String, String> buildPlaceholders(Player player, VoteState voteState) {
        Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("{selected_hearts}", resolveWinningLabel(voteState, VoteCategory.HEARTS, HEART_OPTIONS));
        placeholders.put("{selected_time}", resolveWinningLabel(voteState, VoteCategory.TIME, TIME_OPTIONS));
        placeholders.put("{selected_weather}", resolveWinningLabel(voteState, VoteCategory.WEATHER, WEATHER_OPTIONS));
        placeholders.put("{player_vote_hearts}", resolvePlayerVoteLabel(player, voteState, VoteCategory.HEARTS));
        placeholders.put("{player_vote_time}", resolvePlayerVoteLabel(player, voteState, VoteCategory.TIME));
        placeholders.put("{player_vote_weather}", resolvePlayerVoteLabel(player, voteState, VoteCategory.WEATHER));
        for (String option : HEART_OPTIONS) {
            placeholders.put("{votes_hearts_" + option + "}", String.valueOf(voteState != null ? voteState.getVotes(VoteCategory.HEARTS, option) : 0));
        }
        for (String option : TIME_OPTIONS) {
            placeholders.put("{votes_time_" + option + "}", String.valueOf(voteState != null ? voteState.getVotes(VoteCategory.TIME, option) : 0));
        }
        for (String option : WEATHER_OPTIONS) {
            placeholders.put("{votes_weather_" + option + "}", String.valueOf(voteState != null ? voteState.getVotes(VoteCategory.WEATHER, option) : 0));
        }
        return placeholders;
    }

    private boolean castVote(Player player,
                             GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                             VoteState voteState,
                             String[] args) {
        VoteOption option = parseVoteOption(args);
        if (option == null) {
            sendMessage(context, player, "votes.messages.invalid");
            return true;
        }
        if (!hasVotePermission(player, option.category(), option.option())) {
            sendMessage(context, player, "votes.messages.no_permission", option.category(), option.option());
            return true;
        }
        if (voteState != null) {
            long cooldownRemaining = getRemainingVoteCooldownSeconds(player.getUniqueId());
            if (cooldownRemaining > 0) {
                String message = formatVoteMessage("votes.messages.cooldown", null, null)
                        .replace("{time}", String.valueOf(cooldownRemaining));
                if (!message.isBlank()) {
                    context.getMessagesAPI().sendRaw(player, message);
                }
                return true;
            }
            voteState.castVote(player.getUniqueId(), option.category(), option.option());
            voteCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            broadcastVote(player, option.category(), option.option(), context, voteState);
        }
        return true;
    }

    private boolean castWaitingVote(Player player, String[] args, VoteState waiting) {
        VoteOption option = parseVoteOption(args);
        if (option == null) {
            sendWaitingMessage(player, "votes.messages.invalid");
            return false;
        }
        if (!hasVotePermission(player, option.category(), option.option())) {
            sendWaitingMessage(player, "votes.messages.no_permission", option.category(), option.option());
            return false;
        }
        long cooldownRemaining = getRemainingVoteCooldownSeconds(player.getUniqueId());
        if (cooldownRemaining > 0) {
            String message = formatVoteMessage("votes.messages.cooldown", null, null)
                    .replace("{time}", String.valueOf(cooldownRemaining));
            if (!message.isBlank()) {
                sendWaitingMessageRaw(player, message);
            }
            return true;
        }
        waiting.castVote(player.getUniqueId(), option.category(), option.option());
        voteCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        broadcastWaitingVote(player, option.category(), option.option(), waiting);
        return true;
    }

    private VoteOption parseVoteOption(String[] args) {
        if (args == null || args.length < 3) {
            return null;
        }
        VoteCategory category = VoteCategory.fromId(args[1]);
        String option = args[2].toLowerCase(Locale.ROOT);
        return category != null && isOptionValid(category, option) ? new VoteOption(category, option) : null;
    }

    private void sendMessage(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                             Player player,
                             String path) {
        sendMessage(context, player, path, null, null);
    }

    private void sendMessage(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                             Player player,
                             String path,
                             VoteCategory category,
                             String option) {
        String message = formatVoteMessage(path, category, option);
        if (message.isBlank()) {
            return;
        }
        context.getMessagesAPI().sendRaw(player, message);
    }

    private void sendWaitingMessage(Player player, String path) {
        sendWaitingMessage(player, path, null, null);
    }

    private void sendWaitingMessage(Player player, String path, VoteCategory category, String option) {
        String message = formatVoteMessage(path, category, option);
        if (message.isBlank()) {
            return;
        }
        @SuppressWarnings("unchecked")
        MessageAPI<Player> messagesAPI = (MessageAPI<Player>) ModuleAPI.getMessagesAPI();
        if (messagesAPI != null) {
            messagesAPI.sendRaw(player, message);
        } else {
            player.sendMessage(message);
        }
    }

    private String formatVoteMessage(String path, VoteCategory category, String option) {
        String message = moduleConfig.getTranslation(null, path);
        if (message == null || message.isBlank()) {
            return "";
        }
        if (category != null) {
            message = message.replace("{category}", getCategoryLabel(category));
        }
        if (option != null) {
            message = message.replace("{option}", getOptionLabel(category, option));
        }
        return message;
    }

    private void broadcastVote(Player player,
                               VoteCategory category,
                               String option,
                               GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                               VoteState voteState) {
        String message = voteBroadcastMessage(player, category, option, voteState);
        if (message.isBlank()) {
            return;
        }
        MessageAPI<Player> messagesAPI = context.getMessagesAPI();
        for (Player target : context.getPlayers()) {
            if (target != null) {
                messagesAPI.sendRaw(target, message);
            }
        }
    }

    private void broadcastWaitingVote(Player player, VoteCategory category, String option, VoteState voteState) {
        String message = voteBroadcastMessage(player, category, option, voteState);
        if (message.isBlank()) {
            return;
        }
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game != null ? game.getContext(player) : null;
        if (context != null) {
            broadcastVote(player, category, option, context, voteState);
            return;
        }
        if (isPlayerInWaitingArena(player)) {
            broadcastToWaitingArena(player, message);
        }
    }

    private String voteBroadcastMessage(Player player, VoteCategory category, String option, VoteState voteState) {
        String message = moduleConfig.getTranslation(player, "votes.messages.broadcast");
        if (message == null || message.isBlank()) {
            return "";
        }
        int voteCount = voteState != null ? voteState.getVotes(category, option) : 0;
        return message.replace("{player}", player.getName())
                .replace("{category}", getCategoryLabel(category))
                .replace("{option}", getOptionLabel(category, option))
                .replace("{votes}", String.valueOf(voteCount));
    }

    private void sendWaitingMessageRaw(Player player, String message) {
        @SuppressWarnings("unchecked")
        MessageAPI<Player> messagesAPI = (MessageAPI<Player>) ModuleAPI.getMessagesAPI();
        if (messagesAPI != null) {
            messagesAPI.sendRaw(player, message);
        } else {
            player.sendMessage(message);
        }
    }

    private void broadcastToWaitingArena(Player sender, String message) {
        if (sender == null || message == null || message.isBlank()) {
            return;
        }
        @SuppressWarnings("unchecked")
        PlayerUtil<Player> playerUtil = (PlayerUtil<Player>) ModuleAPI.getPlayerUtil();
        if (playerUtil == null) {
            return;
        }
        Integer senderArenaId = playerUtil.getPlayerArena(sender);
        if (senderArenaId == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        MessageAPI<Player> messagesAPI = (MessageAPI<Player>) ModuleAPI.getMessagesAPI();
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (online == null || !online.isOnline()) {
                continue;
            }
            Integer onlineArenaId = playerUtil.getPlayerArena(online);
            if (!senderArenaId.equals(onlineArenaId)) {
                continue;
            }
            if (messagesAPI != null) {
                messagesAPI.sendRaw(online, message);
            } else {
                online.sendMessage(message);
            }
        }
    }

    private boolean isPlayerInWaitingArena(Player player) {
        @SuppressWarnings("unchecked")
        PlayerUtil<Player> playerUtil = (PlayerUtil<Player>) ModuleAPI.getPlayerUtil();
        return playerUtil != null && playerUtil.isInWaitingArena(player);
    }

    private void broadcastResultForCategory(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                            VoteState voteState,
                                            VoteCategory category,
                                            String messagePath) {
        String option = voteState.resolveWinner(category);
        String sourceKey = voteState.hasVotes(category)
                ? "votes.messages.selected.sources.popular"
                : "votes.messages.selected.sources.default";
        String source = moduleConfig.getTranslation(null, sourceKey);
        String message = moduleConfig.getTranslation(null, messagePath);
        if (message == null || message.isBlank()) {
            return;
        }
        message = message.replace("{option}", getOptionLabel(category, option).toUpperCase(Locale.ROOT))
                .replace("{source}", source);
        MessageAPI<Player> messagesAPI = context.getMessagesAPI();
        for (Player player : context.getPlayers()) {
            if (player != null) {
                messagesAPI.sendRaw(player, message);
            }
        }
    }

    private String mapMenuId(String menuId) {
        if (menuId == null) {
            return MENU_MAIN;
        }
        return switch (menuId.toLowerCase(Locale.ROOT)) {
            case "hearts" -> MENU_HEARTS;
            case "time" -> MENU_TIME;
            case "weather" -> MENU_WEATHER;
            default -> MENU_MAIN;
        };
    }

    private boolean isOptionValid(VoteCategory category, String option) {
        return switch (category) {
            case HEARTS -> HEART_OPTIONS.contains(option);
            case TIME -> TIME_OPTIONS.contains(option);
            case WEATHER -> WEATHER_OPTIONS.contains(option);
        };
    }

    private long getVoteCooldownMillis() {
        if (moduleConfig == null) {
            return 0;
        }
        int seconds = moduleConfig.getInt("votes.cooldown_seconds", 5);
        return seconds <= 0 ? 0 : seconds * 1000L;
    }

    private long getRemainingVoteCooldownSeconds(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        long cooldownMillis = getVoteCooldownMillis();
        if (cooldownMillis <= 0) {
            return 0;
        }
        Long lastVote = voteCooldowns.get(playerId);
        if (lastVote == null) {
            return 0;
        }
        long remainingMillis = cooldownMillis - (System.currentTimeMillis() - lastVote);
        if (remainingMillis <= 0) {
            return 0;
        }
        return (remainingMillis + 999) / 1000;
    }

    private boolean hasVotePermission(Player player, VoteCategory category, String option) {
        if (player == null || category == null || option == null) {
            return false;
        }
        String categoryId = category.getId();
        return player.hasPermission(VOTE_PERMISSION_BASE + ".*")
                || player.hasPermission(VOTE_PERMISSION_BASE + "." + categoryId + ".*")
                || player.hasPermission(VOTE_PERMISSION_BASE + "." + categoryId + "." + option);
    }

    private String normalizeOption(String value, Set<String> options, String fallback) {
        String normalized = value == null ? fallback : value.trim().toLowerCase(Locale.ROOT);
        return options.contains(normalized) ? normalized : fallback;
    }

    private int resolveHearts(String hearts) {
        return switch (hearts == null ? "" : hearts) {
            case "20" -> 20;
            case "30" -> 30;
            default -> 10;
        };
    }

    private void applyHearts(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, int hearts) {
        double maxHealth = Math.max(2.0, hearts * 2.0);
        Attribute attribute = maxHealthAttribute();
        for (Player player : context.getPlayers()) {
            if (player == null) {
                continue;
            }
            if (attribute != null && player.getAttribute(attribute) != null) {
                player.getAttribute(attribute).setBaseValue(maxHealth);
            }
            player.setHealth(Math.min(maxHealth, player.getMaxHealth()));
        }
    }

    private void applyWorldTime(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, String time) {
        World world = resolveRuntimeWorld(context);
        if (world == null) {
            return;
        }
        long ticks = switch (time == null ? "" : time) {
            case "night" -> 13000L;
            case "sunset" -> 12000L;
            case "sunrise" -> 23000L;
            default -> 1000L;
        };
        world.setTime(ticks);
    }

    private void applyWeather(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, String weather) {
        World world = resolveRuntimeWorld(context);
        if (world == null) {
            return;
        }
        boolean rainy = "rainy".equalsIgnoreCase(weather);
        world.setStorm(rainy);
        world.setThundering(false);
        world.setWeatherDuration(rainy ? 20 * 60 * 20 : 0);
        world.setThunderDuration(0);
    }

    private World resolveRuntimeWorld(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        if (context == null) {
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

    private String resolveWinningLabel(VoteState voteState, VoteCategory category, Set<String> options) {
        String resolved = voteState != null ? voteState.resolveWinner(category) : null;
        if (resolved == null || !options.contains(resolved)) {
            resolved = defaultOption(category);
        }
        return getOptionLabel(category, resolved);
    }

    private String resolvePlayerVoteLabel(Player player, VoteState voteState, VoteCategory category) {
        if (player == null || voteState == null) {
            return "-";
        }
        String vote = voteState.getPlayerVote(player.getUniqueId(), category);
        String label = vote == null ? null : getOptionLabel(category, vote);
        return label == null || label.isBlank() ? "-" : label;
    }

    private String defaultOption(VoteCategory category) {
        return switch (category) {
            case HEARTS -> normalizeOption(moduleConfig.getString("votes.defaults.hearts", "10"), HEART_OPTIONS, "10");
            case TIME -> normalizeOption(moduleConfig.getString("votes.defaults.time", "day"), TIME_OPTIONS, "day");
            case WEATHER -> normalizeOption(moduleConfig.getString("votes.defaults.weather", "sunny"), WEATHER_OPTIONS, "sunny");
        };
    }

    private String getCategoryLabel(VoteCategory category) {
        String label = category == null ? null : moduleConfig.getTranslation(null, "votes.labels.categories." + category.getId());
        return label == null ? "" : label;
    }

    private String getOptionLabel(VoteCategory category, String option) {
        String label = category == null || option == null ? null : moduleConfig.getTranslation(null, "votes.labels.options." + category.getId() + "." + option);
        return label == null ? "" : label;
    }

    private Attribute maxHealthAttribute() {
        Attribute attribute = attributeConstant("MAX_HEALTH");
        return attribute != null ? attribute : attributeConstant("GENERIC_MAX_HEALTH");
    }

    private Attribute attributeConstant(String fieldName) {
        try {
            Object value = Attribute.class.getField(fieldName).get(null);
            return value instanceof Attribute attribute ? attribute : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private record VoteOption(VoteCategory category, String option) {
    }
}
