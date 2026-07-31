package kmu.maplayers.base.visibility;

/**
 * The reveal overrides in force for one map-layer resolution pass: the two widenings a
 * caller may apply to the visibility rule, resolved once up front and threaded down as a
 * single value.
 *
 * <p>Both bypass a gate the rule would otherwise hold.
 * {@code shouldIncludeUndiscoveredMarkets} widens the inhabitation read so a colony the
 * player has not discovered still counts, which admits its system and seeds its cell.
 * {@code isForcedOntoMap} bypasses the rule outright, so a system appears regardless of
 * reachability or inhabitation.
 *
 * <p>The framework declares the shape and not the reason: whatever makes a caller want
 * either widening - a dev toggle, a debug view, a setting - is the caller's own business,
 * and keeping that out of here is what lets the visibility rule stay free of any one
 * layer's vocabulary.
 *
 * <p>The pair travels as one value because the coordinators that seed and rebuild a layer
 * need both while the leaves that apply them need one each. Resolving it once at the entry
 * point also fixes it for the whole pass, so every system is admitted under the same
 * overrides even if whatever stands behind them changes mid-walk.
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
}
