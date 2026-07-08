package kmu.maplayers;

import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.NoLayer;
import kmu.maplayers.politicalmap.base.FactionsView;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.factions.FactionsLayer;

import java.util.List;

/**
 * The composition root that wires KMU's concrete map layers and political-map views into the
 * feature-agnostic registries. It is the one place that names every layer and view, sitting above
 * both the framework ({@code base}) and the layers/views ({@code politicalmap}) so neither depends
 * on the other: the registries stay ignorant of which layers and views exist, and each stays
 * ignorant of the order and the default pick.
 */
public final class MapLayers {

    private MapLayers() {
    }

    /**
     * Registers the layers the on-map bar shows and the political-map views its tab offers. Called
     * once at application load, before any sector map can open. No Layer leads so the "show nothing"
     * pick is the first tab; the political-map tab hosts the view-selector radio, whose only view so
     * far is the faction view - registered as the default so the overlay is up the first time the
     * sector map opens rather than blank.
     */
    public static void registerAll() {
        MapLayerRegistry.registerLayers(
                List.of(NoLayer.INSTANCE, FactionsLayer.INSTANCE), FactionsLayer.INSTANCE);
        PoliticalMapViewRegistry.registerViews(
                List.of(FactionsView.INSTANCE), FactionsView.INSTANCE, FactionsLayer.INSTANCE);
    }
}
