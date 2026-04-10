package net.blueva.arcade.modules.capture_the_wool.support.armory;

import net.blueva.arcade.api.game.GameContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ArmoryService {

    public void openChestClone(Player player,
                               GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                               Container container) {
        if (player == null || context == null || container == null) {
            return;
        }

        List<ItemStack> items = new ArrayList<>();
        for (ItemStack content : container.getInventory().getContents()) {
            if (content != null) {
                items.add(content.clone());
            }
        }

        int containerSize = container.getInventory().getSize();
        if (containerSize % 9 != 0) {
            containerSize = ((containerSize / 9) + 1) * 9;
        }
        int menuSize = Math.max(9, Math.min(54, containerSize));

        String title = context.getItemAPI().formatInventoryTitle("&8Chest");
        Inventory menu = player.getServer().createInventory(null, menuSize, title);

        for (int i = 0; i < Math.min(menu.getSize(), items.size()); i++) {
            menu.setItem(i, items.get(i));
        }

        player.openInventory(menu);
    }
}
