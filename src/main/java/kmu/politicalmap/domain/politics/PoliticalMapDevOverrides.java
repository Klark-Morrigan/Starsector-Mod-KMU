package kmu.politicalmap.domain.politics;

import kmu.settings.KmuLunaSettings;

/**
 * The dev reveal overrides in force for one political-map resolution pass: the two
 * "Dev" toggles that widen what the map draws, read once from settings and threaded
 * down beside the {@link DominanceWeighting} rules.
 *
 * <p>Both bypass a normal gate for inspection. {@code isShowingAllFactions} drops the
 * known-to-player filter in {@link KnownMarketFootprints}, so an undiscovered colony
 * still folds into its system's dominance and inhabitation - and, through inhabitation,
 * its cell geometry. {@code isForcingAllSystemsOnMap} bypasses the visibility rule, so
 * every star system seeds a cell regardless of reachability or inhabitation. Bundling
 * the two into one value lets a pass read the player's toggles once up front and thread
 * a single argument through the coordinators that need both (the sector snapshot, the
 * drawn-position walk, the geometry cache), while a leaf that needs only one reads the
 * matching component. The LunaLib read is confined to {@link #readFromSettings()} - the
 * one seam that touches settings - so the domain classes that consume the result stay
 * free of settings access.
 *
 * @param isShowingAllFactions      whether every faction's colonies are drawn, including
 *                                  ones the player has not discovered - the known-to-player
 *                                  footprint filter is bypassed
 * @param isForcingAllSystemsOnMap  whether every star system seeds a cell, not just the
 *                                  reachable, visible, or inhabited ones - the map
 *                                  visibility rule is bypassed
 */
public record PoliticalMapDevOverrides(boolean isShowingAllFactions,
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
    public static PoliticalMapDevOverrides readFromSettings() {
        return new PoliticalMapDevOverrides(
                KmuLunaSettings.getPoliticalMapShowAllFactions(),
                KmuLunaSettings.shouldForceAllSystemsOnMap());
    }
}
