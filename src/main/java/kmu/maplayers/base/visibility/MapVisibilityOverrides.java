package kmu.maplayers.base.visibility;

import kmu.settings.KmuMapLayerSettings;

/**
 * The visibility overrides in force for one map-layer pass: the two widenings the player may
 * apply to the visibility rule, resolved once up front and threaded down as a single value.
 *
 * <p>Both bypass a gate the rule would otherwise hold.
 * {@code shouldIncludeUndiscoveredMarkets} widens the inhabitation read so a colony the
 * player has not discovered still counts, which admits its system and seeds its cell.
 * {@code isForcedOntoMap} bypasses the rule outright, so a system appears regardless of
 * reachability or inhabitation.
 *
 * <p>Neither is a question about any one layer's subject, which is why they are the
 * framework's: what a colony's discovery hides and what a system's reachability hides are the
 * same for every layer drawn over the sector map, and a second layer asking would otherwise
 * have to reach into the first one's vocabulary to ask it.
 *
 * <p>The pair travels as one value because the coordinators that seed and rebuild a layer
 * need both while the leaves that apply them need one each. Resolving it once at the entry
 * point also fixes it for the whole pass, so every system is admitted under the same
 * overrides even if the player moves a toggle mid-walk.
 *
 * <p>The components are named for what each widening does to the rule, while the settings the
 * read below samples are named for what the player is asking to see. Same two bits, stated
 * from the two ends that care about them.
 *
 * @param shouldIncludeUndiscoveredMarkets whether an undiscovered colony counts as
 *                                         inhabitation; false applies the normal
 *                                         known-to-player filter
 * @param isForcedOntoMap                  whether a star system is admitted to the map
 *                                         regardless of access or inhabitation
 */
public record MapVisibilityOverrides(
    boolean shouldIncludeUndiscoveredMarkets,
    boolean isForcedOntoMap) {

    /**
     * The no-override view: both widenings off, so the map admits exactly what the normal
     * gates admit. The default a caller with no reveal to apply passes.
     */
    public static final MapVisibilityOverrides NONE =
        new MapVisibilityOverrides(false, false);

    /**
     * Reads the player's current overrides into one pass-wide value.
     *
     * <p>Called once per pass at the entry points, so every system in the pass is admitted under
     * the toggles in force when it began even if the player moves one mid-walk. Isolating the
     * settings read here keeps the rule and everything that threads this value free of settings
     * access.
     *
     * @return the overrides the player's live settings describe
     */
    public static MapVisibilityOverrides readFromLunaSettings() {
        return new MapVisibilityOverrides(
            KmuMapLayerSettings.shouldShowUndiscoveredMarkets(),
            KmuMapLayerSettings.shouldShowHiddenSystems());
    }
}
