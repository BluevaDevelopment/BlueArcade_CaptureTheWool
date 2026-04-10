package net.blueva.arcade.modules.capture_the_wool.listener;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import net.blueva.arcade.modules.capture_the_wool.game.CaptureTheWoolGame;
import net.blueva.arcade.modules.capture_the_wool.state.ArenaState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

public class CaptureTheWoolListener implements Listener {

    private final CaptureTheWoolGame game;

    public CaptureTheWoolListener(CaptureTheWoolGame game) {
        this.game = game;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(player);

        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        if (game.isInRestrictedZone(context, player, event.getTo())) {
            event.setCancelled(true);
            context.getMessagesAPI().sendRaw(player,
                    game.getModuleConfig().getStringFrom("language.yml", "messages.restricted_zone"));
            return;
        }

        if (!context.isInsideBounds(event.getTo())) {
            game.handleNonCombatDeath(context, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block clickedBlock = event.getClickedBlock();
            org.bukkit.block.BlockState blockState = clickedBlock.getState();
            if (blockState instanceof Container container) {
                event.setCancelled(true);
                game.getArmoryService().openChestClone(player, context, container);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(player);

        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            event.setCancelled(true);
            return;
        }

        Location blockLoc = event.getBlock().getLocation();

        if (game.isWoolSpawnLocation(context, blockLoc)) {
            boolean picked = game.handleWoolPickup(context, player, event.getBlock());
            if (picked) {
                event.setCancelled(false);
                event.setDropItems(false);
            } else {
                event.setCancelled(true);
                String blockedMessage = game.getWoolPickupBlockedMessage(context, player, event.getBlock());
                if (blockedMessage != null && !blockedMessage.isBlank()) {
                    context.getMessagesAPI().sendRaw(player, blockedMessage);
                }
            }
            return;
        }

        if (game.isCaptureLocation(context, blockLoc)) {
            event.setCancelled(true);
            return;
        }

        if (!context.isInsideBounds(blockLoc) || !game.canBreakBlock(context, event.getBlock())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(false);
        game.untrackPlacedBlock(context, blockLoc);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(player);

        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            event.setCancelled(true);
            return;
        }

        Location blockLoc = event.getBlock().getLocation();

        if (!context.isInsideBounds(blockLoc)) {
            event.setCancelled(true);
            return;
        }

        if (game.isCaptureLocation(context, blockLoc)) {
            Material placedMaterial = event.getBlock().getType();
            boolean captured = game.handleWoolCapture(context, player, event.getBlock(), placedMaterial);
            if (!captured) {
                event.setCancelled(true);
            } else {
                event.setCancelled(false);
            }
            return;
        }

        Material placedType = event.getBlock().getType();
        if (game.isCarriedWoolMaterial(context, player, placedType)
                || (game.isPlayerCarryingWool(context, player) && game.isObjectiveWoolMaterial(context, placedType))) {
            event.setCancelled(true);
            return;
        }

        if (game.isWoolSpawnLocation(context, blockLoc)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(false);
        game.trackPlacedBlock(context, blockLoc);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(player);

        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            event.setCancelled(true);
            return;
        }

        Item droppedItem = event.getItemDrop();
        if (droppedItem != null && droppedItem.getItemStack() != null) {
            Material droppedMaterial = droppedItem.getItemStack().getType();
            if (game.isPlayerCarryingWool(context, player)
                    && game.isObjectiveWoolMaterial(context, droppedMaterial)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(target);
        if (context == null || !context.isPlayerPlaying(target)) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            event.setCancelled(true);
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || !context.isPlayerPlaying(attacker)) {
            event.setCancelled(true);
            return;
        }

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI != null && teamsAPI.isEnabled()) {
            TeamInfo<Player, Material> attackerTeam = teamsAPI.getTeam(attacker);
            TeamInfo<Player, Material> targetTeam = teamsAPI.getTeam(target);
            if (attackerTeam != null && targetTeam != null && attackerTeam.getId().equalsIgnoreCase(targetTeam.getId())) {
                event.setCancelled(true);
                return;
            }
        }

        double finalHealth = target.getHealth() - event.getFinalDamage();
        if (finalHealth > 0) {
            return;
        }

        event.setCancelled(true);
        game.handleKill(context, attacker, target);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGenericDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(target);
        if (context == null || !context.isPlayerPlaying(target)) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            event.setCancelled(true);
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            ArenaState state = game.getArenaState(context);
            if (state != null && state.hasFallProtection(target.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
        }

        double finalHealth = target.getHealth() - event.getFinalDamage();
        if (finalHealth > 0) {
            return;
        }

        event.setCancelled(true);
        game.handleNonCombatDeath(context, target);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }

        return null;
    }
}
