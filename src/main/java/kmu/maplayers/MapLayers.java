package kmu.maplayers;

import kmlib.starsector.ui.intel.VanillaIntelScreenView;

import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.NoLayer;
import kmu.maplayers.base.visibility.OpenlyKnownColonyRegistry;
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
 * feature-agnostic registries, and names the sector content those layers have to treat specially.
 * It is the one place that names every layer, view and entity, sitting above both the framework
 * ({@code base}) and the layers/views ({@code politicalmap}) so neither depends on the other: the
 * registries stay ignorant of which layers, views and places exist, and each stays ignorant of the
 * order and the default pick.
 */
public final class MapLayers {

    // The concealed colonies the sector openly points at, which a hover box must not call out as
    // hiding. Galatia Academy is the shape and vanilla's only one: a study the tutorial sends the
    // player to that keeps no comm directory, wearing the same flag a pirate base does. The id is
    // written once at world-gen and persists in the save, so a sector built without the Academy -
    // a random one, or a mod that removes it - simply never matches.
    //
    // Named here rather than beside the reading because a literal only ever covers what shipped
    // with the game; another mod's quest hub opts in through the tag the registry publishes.
    private static final List<String> OPENLY_KNOWN_COLONY_ENTITY_IDS =
        List.of("station_galatia_academy");

    private MapLayers() {
    }

    /**
     * Registers the layers the on-map bar shows, the political-map views its tab offers, and the
     * concealed colonies the sector openly points at. Called once at application load, before any
     * sector map can open. No Layer leads so the "show nothing" pick is the first tab; the
     * political-map tab hosts the view-selector radio over the roster {@link #selectPoliticalMapViews}
     * chooses, with the faction view registered as the default so the overlay is up the first time
     * the sector map opens rather than blank.
     */
    public static void registerAll() {

        registerOpenlyKnownColonies();

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
     * Hands the visibility registry the entities whose concealment is public knowledge, so a hover
     * box withholds the word that would put a landmark and a hiding base in the same class. Kept as
     * its own step because naming content is a different decision from wiring layers, and it is the
     * step a test holds the seeded set to.
     */
    static void registerOpenlyKnownColonies() {
        OpenlyKnownColonyRegistry.registerEntityIds(OPENLY_KNOWN_COLONY_ENTITY_IDS);
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
