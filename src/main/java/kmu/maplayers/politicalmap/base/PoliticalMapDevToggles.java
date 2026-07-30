package kmu.maplayers.politicalmap.base;

import kmu.maplayers.base.visibility.MapVisibilityOverrides;
import kmu.settings.KmuLunaSettings;

/**
 * The two "Dev" reveal toggles in force for one political-map resolution pass, read once
 * from settings and threaded down beside the {@link DominanceRules} rules.
 *
 * <p>Named for what it holds - the player's checkboxes - rather than for the effect they
 * have downstream, which is what {@link MapVisibilityOverrides} is named for. Only one of
 * these two toggles is purely an admission override anyway: show-all-factions is also a
 * dominance-weighting input, so calling the pair "overrides" would describe half of it.
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
public record PoliticalMapDevToggles(
        boolean isShowingAllFactions,
        boolean isForcingAllSystemsOnMap) {

    /**
     * The no-reveal view: both toggles off, so the map draws exactly what the
     * normal gates admit. The default a caller with no dev toggle to apply passes.
     */
    public static final PoliticalMapDevToggles NONE =
            new PoliticalMapDevToggles(false, false);

    /**
     * Reads the player's current dev reveal toggles from LunaLib into one pass-wide
     * value.
     *
     * <p>Called once per resolution pass at the entry points, so every system in the
     * pass resolves under the same toggles even if the player flips one mid-walk.
     * Isolating the settings read here keeps the domain classes that consume the
     * result free of LunaLib access.
     *
     * @return the toggles the player's live settings describe
     */
    public static PoliticalMapDevToggles readFromLunaSettings() {
        return new PoliticalMapDevToggles(
                KmuLunaSettings.getPoliticalMapShowAllFactions(),
                KmuLunaSettings.shouldForceAllSystemsOnMap());
    }

    /**
     * Restates these toggles as what they mean to the visibility rule, dropping the
     * political reason and keeping the two widenings themselves. A relabelling of the same
     * two bits, not a computation - the settings read that decided them already happened.
     *
     * <p>{@code isShowingAllFactions} is political only in why it widens - undiscovered
     * faction colonies - while what it widens is the generic inhabitation read, so it
     * lands on {@code shouldIncludeUndiscoveredMarkets}. {@code isForcingAllSystemsOnMap}
     * carries no political meaning at all and maps straight across.
     *
     * @return the visibility overrides these toggles describe
     */
    public MapVisibilityOverrides convertToVisibilityOverrides() {
        return new MapVisibilityOverrides(
                isShowingAllFactions,
                isForcingAllSystemsOnMap);
    }
}
