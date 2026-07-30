package kmu.maplayers.politicalmap.base;

import kmu.maplayers.base.visibility.MapVisibilityOverrides;
import kmu.settings.KmuLunaSettings;

/**
 * The dev reveal overrides in force for one political-map resolution pass: the two
 * "Dev" toggles that widen what the map draws, read once from settings and threaded
 * down beside the {@link DominanceRules} rules.
 *
 * <p>Both bypass a normal gate for inspection. {@code isShowingAllFactions} drops the
 * known-to-player filter in {@link KnownMarketFootprints}, so an undiscovered colony
 * still folds into its system's dominance and inhabitation - and, through inhabitation,
 * its cell geometry. {@code isForcingAllSystemsOnMap} bypasses the visibility rule, so
 * every star system seeds a cell regardless of reachability or inhabitation.
 *
 * <p>What survives here is the political framing of the two reveals: which faction
 * colonies count as discovered, and what a "show everything" toggle means for a map of
 * blocs. Dominance reads {@code isShowingAllFactions} straight off this record, because
 * folding an undiscovered colony into a faction's weight is political in a way that
 * admitting its system to the map is not. The admission half, stripped of that framing,
 * is {@link MapVisibilityOverrides}.
 *
 * <p>The LunaLib read is confined to {@link #readFromLunaSettings()} - the one seam that
 * touches settings - so the domain classes that consume the result stay free of settings
 * access.
 *
 * @param isShowingAllFactions      whether every faction's colonies are drawn, including
 *                                  ones the player has not discovered - the known-to-player
 *                                  footprint filter is bypassed
 * @param isForcingAllSystemsOnMap  whether every star system seeds a cell, not just the
 *                                  reachable, visible, or inhabited ones - the map
 *                                  visibility rule is bypassed
 */
public record PoliticalMapDevOverrides(
        boolean isShowingAllFactions,
        boolean isForcingAllSystemsOnMap) {

    /**
     * The no-override view: both reveals off, so the map draws exactly what the
     * normal gates admit. The default a caller with no dev toggle to apply passes.
     */
    public static final PoliticalMapDevOverrides NONE =
            new PoliticalMapDevOverrides(false, false);

    /**
     * Reads the player's current dev reveal toggles from LunaLib into one pass-wide
     * override.
     *
     * <p>Called once per resolution pass at the entry points, so every system in the
     * pass resolves under the same overrides even if the player flips a toggle
     * mid-walk. Isolating the settings read here keeps the domain classes that
     * consume the result free of LunaLib access.
     *
     * @return the overrides the player's live settings describe
     */
    public static PoliticalMapDevOverrides readFromLunaSettings() {
        return new PoliticalMapDevOverrides(
                KmuLunaSettings.getPoliticalMapShowAllFactions(),
                KmuLunaSettings.shouldForceAllSystemsOnMap());
    }

    /**
     * Answers what these toggles mean to the visibility rule, dropping the political
     * reason and keeping the two widenings themselves.
     *
     * <p>{@code isShowingAllFactions} is political only in why it widens - undiscovered
     * faction colonies - while what it widens is the generic inhabitation read, so it
     * lands on {@code shouldIncludeUndiscoveredMarkets}. {@code isForcingAllSystemsOnMap}
     * carries no political meaning at all and maps straight across.
     *
     * @return the visibility overrides these toggles describe
     */
    public MapVisibilityOverrides resolveVisibilityOverrides() {
        return new MapVisibilityOverrides(
                isShowingAllFactions,
                isForcingAllSystemsOnMap);
    }
}
