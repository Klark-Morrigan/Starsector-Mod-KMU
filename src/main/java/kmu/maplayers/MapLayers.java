package kmu.maplayers;

import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.NoLayer;
import kmu.maplayers.politicalmap.factions.FactionsLayer;

import java.util.List;

/**
 * The composition root that wires KMU's concrete map layers into the feature-agnostic
 * {@link MapLayerRegistry}. It is the one place that names every layer, sitting above both the
 * framework ({@code base}) and the layers ({@code politicalmap.factions}) so neither depends on
 * the other: the registry stays ignorant of which views exist, and each layer stays ignorant
 * of the tab order and the default pick.
 */
public final class MapLayers {

    private MapLayers() {
    }

    /**
     * Registers the layers the on-map bar shows, left to right, and the pick an untouched save
     * resolves to. Called once at application load, before any sector map can open. No Layer
     * leads so the "show nothing" pick is the first tab; the faction view is the default, so
     * the overlay is up the first time the sector map opens rather than blank.
     */
    public static void registerAll() {
        MapLayerRegistry.registerLayers(
                List.of(NoLayer.INSTANCE, FactionsLayer.INSTANCE), FactionsLayer.INSTANCE);
    }
}
