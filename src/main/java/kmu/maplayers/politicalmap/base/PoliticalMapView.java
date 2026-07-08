package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;
import kmu.settings.FactionNameFormatChoice;

/**
 * The three per-view decisions the shared political-map pipeline reads, gathered into
 * one rules object so each political-map view (faction, alliance, later mixed) supplies
 * its own grouping, cell styling, and labelling while the pipeline stays written once.
 *
 * <p>A view differs from every other only in these three things - how it collapses
 * factions into blocs, which blocs recede to the muted "independent" style, and what a
 * bloc's label reads - so lifting them behind this seam makes a new view an added rules
 * object rather than a fork of the render pipeline. The grouping is resolved once per
 * pass and handed back into the two per-bloc decisions, so the classifier and the name
 * resolver stay pure lookups over that one snapshot rather than re-reading a live set
 * (the alliances view samples Nexerelin) per cell.
 */
public interface PoliticalMapView {

    /**
     * This view's stable id - the string the active-view selection serialises into the save and
     * the view registry resolves a stored pick back to. Frozen once shipped, since renaming it
     * silently resets a save that selected this view to the default.
     *
     * @return the view's save-stable id
     */
    String getId();

    /**
     * The localisation key for this view's label on the view-selector radio - the segment the
     * player clicks to activate it. A key rather than the resolved string so the segment follows
     * the player's language and the resolution stays with the view radio that draws it.
     *
     * @return the {@code KmuStrings} key for this view's radio-segment label
     */
    String getSegmentLabelKey();

    /**
     * A revision counter for the live data this view's grouping is sampled from, folded
     * into the drawables' content token so a change to that data forces a rebuild even
     * when no setting moved. The faction view's grouping is static (identity), so it
     * returns a constant and never triggers a rebuild on its own; the alliances view
     * returns the live alliance-set revision, so a membership change repaints it. This is
     * what lets the shared plugin invalidate on alliance changes without naming any
     * concrete view - each view declares its own live-data revision.
     *
     * @return this view's live-data revision; a constant for a view with a static grouping
     */
    int getGroupingRevision();

    /**
     * The ownership grouping this view resolves its pass under: identity for the
     * faction view (every faction its own bloc), alliance blocs for the alliances
     * view. Resolved once per rebuild and threaded through the pipeline, so a live set
     * is sampled a single time per pass and every stage keys off the same snapshot.
     *
     * @return the grouping that collapses factions into blocs for this pass
     */
    OwnershipGrouping resolveGrouping();

    /**
     * Whether a bloc paints in the muted independent cell style rather than the full
     * faction style. The faction view styles only independent space this way; the
     * alliances view styles every non-alliance bloc this way, so the unaligned recede
     * while alliances stand out in full colour.
     *
     * @param blocId   the winning bloc for a system, as resolved under {@code grouping}
     * @param grouping the grouping this pass resolved, supplied so the test is a pure
     *                 lookup over the once-sampled snapshot rather than a fresh read
     * @return true when the bloc takes the independent style
     */
    boolean shouldUseIndependentStyle(String blocId, OwnershipGrouping grouping);

    /**
     * The label a bloc reads under this view: a faction's display name for a faction
     * bloc, an alliance's name for an alliance bloc. Null when no name resolves, which
     * the label fit treats as an unresolved name and sizes a stand-in band for instead.
     *
     * @param blocId     the winning bloc to name, as resolved under {@code grouping}
     * @param grouping   the grouping this pass resolved
     * @param sector     the sector, from which a faction bloc's display name is read
     * @param nameFormat whether a faction name reads in its short or full form
     * @return the bloc's display name, or null when none resolves
     */
    String resolveName(String blocId, OwnershipGrouping grouping, SectorAPI sector,
            FactionNameFormatChoice nameFormat);
}
