package kmu.starsector.rat;

import com.fs.starfarer.api.ui.UIComponentAPI;

import kmlib.starsector.ui.map.probes.EmbeddedMap;

import kmu.starsector.ui.SingleEmbeddedMapReader;

import java.util.function.Supplier;

/**
 * Which map widget the compatibility mode permits switching off while it is parked off screen, or
 * nothing when this mod has no business switching anything off.
 *
 * <p>The permission half of the suppression, and the only half that names a mod. What being parked
 * means, and what switching a widget off consists of, are stated over any widget at all -
 * {@code OffScreenWidgetSuppressor} reads a box against the screen and writes an opacity, and would
 * read a minimap docked by the next mod - or any other parked widget - exactly the same way. What
 * cannot be stated generally is whether writing into somebody else's widget is wanted at all, which
 * is a per-mod question the player answers with the mode.
 *
 * <p><b>Exactly one embedded map, and none of the reasons the confinement needs one.</b> There the
 * frame carries a single transform and a second map makes a hover unattributable. Here the mode
 * names a mod, the maps found carry no mod-owned class to match on - a minimap built entirely from
 * vanilla API has none - and with two of them there is no telling which one the mode was switched on
 * for. Suppressing both would act on a mod the player never mentioned. The reading itself is
 * {@link SingleEmbeddedMapReader}'s and shared with that confinement, so the two cannot come to
 * differ about which frame carries a surface worth acting on.
 *
 * <p><b>Nothing here reads whether a vanilla map is showing</b>, and the asymmetry with the cover
 * beside it is deliberate. That cover stands down on a frame a vanilla host owns because the cursor
 * on such a frame is the host's business. This is aimed at exactly those frames: a parked minimap
 * renders a whole sector map behind whatever screen the player opened, and the pass it contributes
 * is a second transform in a frame that already carries the host's.
 *
 * <p>Both reads are taken afresh at every ask, since a mode read live is what lets the player see
 * the minimap come back on the next frame rather than at the next rebuild, and the widget a mod
 * built once is not the widget it rebuilds after a location change.
 */
public final class RandomAssortmentOfThingsMinimapSuppression {

    // The single map surface on screen, asked afresh each frame, or null when there is not exactly
    // one. A supplier rather than the reader itself so this class states its question and not where
    // the answer is walked out of.
    private final Supplier<EmbeddedMap> findSingleEmbeddedMap;

    private final RandomAssortmentOfThingsMode mode;

    /**
     * @param mode                  whether the player's compatibility mode is on and there is a
     *                              minimap to suppress
     * @param findSingleEmbeddedMap the one map surface on screen that is not the one the player
     *                              opened, or null when there is not exactly one
     */
    public RandomAssortmentOfThingsMinimapSuppression(
            RandomAssortmentOfThingsMode mode,
            Supplier<EmbeddedMap> findSingleEmbeddedMap) {

        this.findSingleEmbeddedMap = findSingleEmbeddedMap;
        this.mode = mode;
    }

    /**
     * @return the permission over the mode and the shared widget walk a running game has
     */
    public static RandomAssortmentOfThingsMinimapSuppression createForLiveScreen() {
        return new RandomAssortmentOfThingsMinimapSuppression(
            RandomAssortmentOfThingsMode.createForLiveGame(),
            SingleEmbeddedMapReader.INSTANCE::resolveSingleEmbeddedMap);
    }

    /**
     * @return the minimap widget that may be switched off while it is parked, or null for none -
     *         the mode off, no minimap found, more than one found, or one found that is not a
     *         widget at all. Every one of those leaves the screen exactly as its owners drew it
     */
    public UIComponentAPI resolveSuppressibleMinimap() {

        // Asked first, so an install without the mode pays one boolean and no widget walk.
        if (!mode.isEngaged()) {
            return null;
        }
        var minimap = findSingleEmbeddedMap.get();

        return minimap == null ? null : minimap.resolveComponent();
    }
}
