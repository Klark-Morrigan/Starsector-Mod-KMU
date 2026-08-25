package kmu.maplayers;

import kmlib.starsector.ui.intel.VanillaIntelScreenView;

import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.NoLayer;
import kmu.maplayers.politicalmap.base.PoliticalMapLayer;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.claims.ClaimsView;
import kmu.maplayers.politicalmap.dominance.alliances.AlliancesView;
import kmu.maplayers.politicalmap.dominance.factions.FactionsView;
import kmu.starsector.nexerelin.NexerelinAlliances;

import java.util.ArrayList;
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
     * pick is the first tab; the political-map tab hosts the view-selector radio over the roster
     * {@link #selectPoliticalMapViews} chooses, with the faction view registered as the default so
     * the overlay is up the first time the sector map opens rather than blank.
     */
    public static void registerAll() {

        MapLayerRegistry.registerLayers(
            List.of(NoLayer.INSTANCE, PoliticalMapLayer.INSTANCE),
            PoliticalMapLayer.INSTANCE);

        // Each screen keeps its own tab, so the overlay has to follow the tab of the screen being
        // looked at rather than one fixed screen's. This is the live binding that tells the two apart;
        // naming it here keeps the registry ignorant of any concrete screen.
        MapLayerRegistry.registerIntelScreen(new VanillaIntelScreenView());

        PoliticalMapViewRegistry.registerViews(
                selectPoliticalMapViews(),
                FactionsView.INSTANCE,
                PoliticalMapLayer.INSTANCE);
    }

    /**
     * The political-map view roster in radio-segment order: the faction view leads, the alliances
     * view follows only when Nexerelin is present (so the radio grows an Alliances segment exactly
     * when the live alliance set behind it exists), and the claims view always closes the row. The
     * claims view is not Nex-gated - the claim mechanic it paints is vanilla - so it sits after the
     * alliances slot whether or not that slot is filled. Kept as its own step because choosing the
     * roster is a soft-dependency decision, distinct from the wiring that registers it; the alliances
     * view is only referenced past the mod-enabled gate, so a Nex-free install never needs it.
     *
     * @return the ordered views to register, one segment each on the view-selector radio
     */
    static List<PoliticalMapView> selectPoliticalMapViews() {

        var views = new ArrayList<PoliticalMapView>();
        views.add(FactionsView.INSTANCE);

        if (NexerelinAlliances.isAvailable()) {
            views.add(AlliancesView.INSTANCE);
        }

        views.add(ClaimsView.INSTANCE);
        return List.copyOf(views);
    }
}
