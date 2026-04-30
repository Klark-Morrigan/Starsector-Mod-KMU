package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import kmu.ui.context.StarsectorMarketUiContextTracker;

public class KMU_ModPlugin extends BaseModPlugin {
    public static final String MOD_ID = "klark_morrigans_utilities";
    public static final String MOD_NAME = "Klark Morrigan's Utilities";

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        try {
            installMarketUiContextTracker(Global.getSector());
        } catch (RuntimeException exception) {
            System.err.println("Failed to install KMU market UI context tracker: " + exception.getMessage());
        }
    }

    static void installMarketUiContextTracker(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        ListenerManagerAPI listenerManager = sector.getListenerManager();
        if (listenerManager == null || listenerManager.hasListenerOfClass(StarsectorMarketUiContextTracker.class)) {
            return;
        }

        listenerManager.addListener(new StarsectorMarketUiContextTracker(), true);
    }
}
