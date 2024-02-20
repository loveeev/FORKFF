package org.mineacademy.fo.craft;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.mineacademy.fo.annotation.AutoRegister;

/**
 * This class listens to events related to custom crafts.<br>
 * See {@link SimpleCraft} and {@link CraftingHandler}.
 *
 * @author Rubix327
 */
@AutoRegister
public final class CraftingListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        for (SimpleCraft<?> craft : CraftingHandler.getCrafts()){
            if (craft.isAutoDiscoverEnabled()){
                craft.discover(event.getPlayer());
            }
        }
    }

}
