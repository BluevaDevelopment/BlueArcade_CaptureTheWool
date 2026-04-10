package net.blueva.arcade.modules.capture_the_wool.support.loadout;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.List;

public class PlayerLoadoutService {

    private final ModuleConfigAPI moduleConfig;
    public PlayerLoadoutService(ModuleConfigAPI moduleConfig) {
        this.moduleConfig = moduleConfig;
    }

    public void giveStartingItems(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                  Player player) {
        TeamLoadout teamLoadout = resolveTeamLoadout(context, player);
        List<String> startingItems;
        if (teamLoadout != null) {
            startingItems = teamLoadout.items();
        } else {
            startingItems = moduleConfig.getStringList("items.starting_items");
        }
        giveItems(player, startingItems, null);
    }

    public void applyStartingEffects(Player player) {
        List<String> startingEffects = moduleConfig.getStringList("effects.starting_effects");
        applyEffects(player, startingEffects);
    }

    public void applyRespawnEffects(Player player) {
        List<String> respawnEffects = moduleConfig.getStringList("effects.respawn_effects");
        applyEffects(player, respawnEffects);
    }

    public void restoreVitals(Player player) {
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
    }

    public void handleKillRegeneration(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                       Player killer) {
        double healAmount = moduleConfig.getDouble("combat.kill_regeneration.health", 6.0);
        if (healAmount <= 0 || context == null) {
            return;
        }

        double newHealth = Math.min(killer.getMaxHealth(), killer.getHealth() + healAmount);
        killer.setHealth(newHealth);
    }

    private void applyEffects(Player player, List<String> effects) {
        if (effects == null || effects.isEmpty()) {
            return;
        }

        for (String effectString : effects) {
            try {
                String[] parts = effectString.split(":");
                if (parts.length >= 3) {
                    org.bukkit.potion.PotionEffectType effectType =
                            org.bukkit.potion.PotionEffectType.getByName(parts[0].toUpperCase());
                    int duration = Integer.parseInt(parts[1]);
                    int amplifier = Integer.parseInt(parts[2]);

                    if (effectType != null) {
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                effectType, duration, amplifier, false, false
                        ));
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void giveItems(Player player, List<String> items, Color leatherColor) {
        if (items == null || items.isEmpty()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        for (String itemString : items) {
            try {
                String[] parts = itemString.split(":");
                if (parts.length >= 2) {
                    Material material = Material.valueOf(parts[0].toUpperCase());
                    int amount = Integer.parseInt(parts[1]);
                    int slot = parts.length >= 3 ? Integer.parseInt(parts[2]) : -1;
                    Color itemColor = parts.length >= 4 ? parseColor(parts[3]) : null;

                    ItemStack item = new ItemStack(material, amount);
                    tintLeatherArmor(item, itemColor != null ? itemColor : leatherColor);

                    if (slot == 40) {
                        inventory.setItemInOffHand(item);
                    } else if (slot == 39) {
                        inventory.setHelmet(item);
                    } else if (slot == 38) {
                        inventory.setChestplate(item);
                    } else if (slot == 37) {
                        inventory.setLeggings(item);
                    } else if (slot == 36) {
                        inventory.setBoots(item);
                    } else if (slot >= 0 && slot < 36) {
                        inventory.setItem(slot, item);
                    } else {
                        inventory.addItem(item);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private TeamLoadout resolveTeamLoadout(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                           Player player) {
        int teamSlot = resolveTeamSlot(context, player);
        if (teamSlot <= 0) {
            return null;
        }

        List<String> bySlot = moduleConfig.getStringList("items.starting_items_by_team." + teamSlot);
        if (bySlot == null || bySlot.isEmpty()) {
            return null;
        }

        return new TeamLoadout(bySlot);
    }

    private int resolveTeamSlot(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                Player player) {
        if (context == null || player == null) {
            return -1;
        }

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return -1;
        }

        TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
        if (team == null || team.getId() == null || team.getId().isBlank()) {
            return -1;
        }

        String playerTeamId = team.getId();
        List<TeamInfo<Player, Material>> teams = teamsAPI.getTeams();
        for (int i = 0; i < teams.size(); i++) {
            TeamInfo<Player, Material> current = teams.get(i);
            if (current != null && current.getId() != null && current.getId().equalsIgnoreCase(playerTeamId)) {
                return i + 1;
            }
        }
        return -1;
    }


    private Color parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String value = raw.trim();
        if (!value.startsWith("#") || value.length() != 7) {
            return null;
        }

        try {
            int rgb = Integer.parseInt(value.substring(1), 16);
            return Color.fromRGB(rgb);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void tintLeatherArmor(ItemStack item, Color leatherColor) {
        if (item == null || leatherColor == null) {
            return;
        }
        ItemMeta itemMeta = item.getItemMeta();
        if (!(itemMeta instanceof LeatherArmorMeta leatherMeta)) {
            return;
        }
        leatherMeta.setColor(leatherColor);
        item.setItemMeta(leatherMeta);
    }

    private record TeamLoadout(List<String> items) {
    }

}
