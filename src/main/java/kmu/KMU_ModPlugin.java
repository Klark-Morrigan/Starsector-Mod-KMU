package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import kmu.settings.KmuLunaSettings;
import kmu.ui.context.StarsectorMarketUiContextTracker;
import org.apache.log4j.Logger;

public class KMU_ModPlugin extends BaseModPlugin {
    public static final String MOD_ID = "kmu";
    public static final String MOD_NAME = "Klark Morrigan's Utilities";

    private static final Logger LOG = Global.getLogger(KMU_ModPlugin.class);

    @Override
    public void onApplicationLoad() {
        // App-scoped, once per launch: register KMU's LunaLib settings
        // bindings before any save loads. LunaLib is a hard dependency, so it
        // has already loaded by the time this runs.
        try {
            KmuLunaSettings.installBindings();
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU LunaLib settings bindings", exception);
        }
    }

    // Terrain type registered in data/campaign/terrain.json that hosts the
    // political map overlay's renderOnMapAbove draw. Serialised into saves;
    // do not rename.
    static final String POLITICAL_MAP_TERRAIN_TYPE = "kmu_political_terrain";

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        try {
            installMarketUiContextTracker(Global.getSector());
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU market UI context tracker", exception);
        }

        try {
            installPoliticalMapTerrain(Global.getSector());
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU political map terrain", exception);
        }
    }

    static void installMarketUiContextTracker(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null || listenerManager.hasListenerOfClass(StarsectorMarketUiContextTracker.class)) {
            return;
        }

        listenerManager.addListener(new StarsectorMarketUiContextTracker(), true);
    }

    static void installPoliticalMapTerrain(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        var hyperspace = sector.getHyperspace();
        if (hyperspace == null) {
            return;
        }

        // One terrain instance per save: a reloaded save already carries it
        // (terrain persists), so skip if a copy is present to avoid stacking.
        for (var terrain : hyperspace.getTerrainCopy()) {
            if (POLITICAL_MAP_TERRAIN_TYPE.equals(terrain.getType())) {
                return;
            }
        }

        // No params: the plugin is purely a map drawer and reads system
        // positions itself, so it needs nothing passed in.
        hyperspace.addTerrain(POLITICAL_MAP_TERRAIN_TYPE, null);

        // One-shot install diagnostic. DEBUG so it stays silent at the WARN
        // default; set KMU log verbosity to DEBUG in LunaLib to see it.
        LOG.debug("Political map terrain installed; star systems="
                + sector.getStarSystems().size());
    }
}
