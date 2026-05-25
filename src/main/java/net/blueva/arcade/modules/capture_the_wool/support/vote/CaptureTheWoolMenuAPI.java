package net.blueva.arcade.modules.capture_the_wool.support.vote;

import net.blueva.arcade.api.ui.MenuAPI;
import net.blueva.arcade.api.ui.ModuleActionHandler;
import net.blueva.arcade.api.ui.menu.DynamicMenuDefinition;
import net.blueva.arcade.api.ui.menu.MenuDefinition;
import net.blueva.arcade.api.ui.menu.MenuEntry;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class CaptureTheWoolMenuAPI implements MenuAPI<Player, Material> {

    private final MenuAPI<Player, Material> delegate;
    private final CaptureTheWoolVoteService voteService;

    public CaptureTheWoolMenuAPI(MenuAPI<Player, Material> delegate, CaptureTheWoolVoteService voteService) {
        this.delegate = delegate;
        this.voteService = voteService;
    }

    @Override
    public boolean openMenu(Player player, MenuDefinition<Material> menu, Map<String, String> placeholders) {
        return delegate.openMenu(player, menu, placeholders);
    }

    @Override
    public boolean openDynamicMenu(Player player, DynamicMenuDefinition<Material> menu, List<MenuEntry<Material>> entries, int page, Map<String, String> placeholders) {
        return delegate.openDynamicMenu(player, menu, entries, page, placeholders);
    }

    @Override
    public boolean isBedrockPlayer(Player player) {
        return delegate.isBedrockPlayer(player);
    }

    @Override
    public void registerModuleActionHandler(String moduleId, ModuleActionHandler<Player> handler) {
        delegate.registerModuleActionHandler(moduleId, handler);
    }

    @Override
    public void unregisterModuleActionHandler(String moduleId) {
        delegate.unregisterModuleActionHandler(moduleId);
    }

    @Override
    public boolean openMenuById(Player player, String menuId) {
        String simpleName = menuId;
        if (menuId.startsWith("capture_the_wool_vote_")) {
            simpleName = menuId.substring("capture_the_wool_vote_".length());
        } else if (menuId.startsWith("vote_")) {
            simpleName = menuId.substring("vote_".length());
        }
        return voteService.openMenuWithDefaults(player, new String[]{"menu", simpleName});
    }
}
