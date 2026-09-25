package kmu.maplayers.politicalmap.render;

import kmu.maplayers.base.render.MapOverlayBand;
import kmu.maplayers.ownermap.render.OwnerMapBandLayout;
import kmu.settings.KmuPoliticalMapDrawOrderSettings;
import kmu.settings.NebulaDrawOrderChoice;

/**
 * Reads the player's four draw-order choices as one {@link OwnerMapBandLayout}.
 *
 * <p>Apart from the layout itself because the two answer to different owners: which sub-layers
 * there are to place is the compositor's, and where the player put each of them is this layer's.
 * A compositor handed a layout needs no settings reader behind it, and a layer that offers no such
 * choice hands back a fixed one.
 *
 * <p>Read per band pass rather than held: four map lookups against LunaLib, the same order of cost
 * as the band style the presence pass already reads every frame, where a cached copy would have to
 * watch the settings revision and be reset for no measurable gain.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class PoliticalMapBandLayoutReader {

    private PoliticalMapBandLayoutReader() {
        // utility class, no instances.
    }

    /**
     * Reads the player's four draw-order choices as one layout.
     *
     * @return where each choosable sub-layer paints this pass
     */
    public static OwnerMapBandLayout readChosenLayout() {
        return new OwnerMapBandLayout(
            resolveBand(KmuPoliticalMapDrawOrderSettings.getPoliticalMapFillNebulaDrawOrder()),
            resolveBand(KmuPoliticalMapDrawOrderSettings.getPoliticalMapBorderNebulaDrawOrder()),
            resolveBand(KmuPoliticalMapDrawOrderSettings.getPoliticalMapRibbonNebulaDrawOrder()),
            resolveBand(KmuPoliticalMapDrawOrderSettings.getPoliticalMapLabelNebulaDrawOrder()));
    }

    // The whole of the translation: the map draws its nebulae between two of its terrain passes, so
    // a draw order the player picked is a band and there is no third answer either could have. A switch
    // expression rather than a comparison, so a choice added later fails to compile here instead of
    // quietly resolving to whichever band the else branch named.
    private static MapOverlayBand resolveBand(NebulaDrawOrderChoice drawOrder) {
        return switch (drawOrder) {
            case BELOW -> MapOverlayBand.BENEATH_STARSCAPE_NEBULAE;
            case ABOVE -> MapOverlayBand.ABOVE_STARSCAPE_NEBULAE;
        };
    }
}
