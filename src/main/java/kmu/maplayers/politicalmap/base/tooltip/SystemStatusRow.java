package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.colonies.ColonyVisibility;
import kmlib.starsector.colonies.SystemColonies;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

import kmu.maplayers.base.tooltip.CellTooltipRows;
import kmu.util.KmuStrings;

import java.util.Optional;

/**
 * The line naming why a hovered system holds nobody: a dead colony the player has already seen reads
 * "Decivilised", any other system with no counted colony "Unpopulated".
 *
 * <p>A shared row rather than each tooltip's own empty state, because the emptiness is a fact about
 * the system, not about the layer looking at it - a layer that says nothing else about a dead system
 * still has to say that much, and two layers wording or placing it differently would read as two
 * different facts. The row resolves empty for a populated system, so a body can offer the status
 * unconditionally and let the system decide whether it appears.
 *
 * <p>It is a banner rather than an entry for the same reason: what the system <em>is</em> holds over
 * everything the box goes on to say about it, so it is centred across the box like the decree that may
 * head it, and neither reads as the opening row of the breakdown below.
 */
public final class SystemStatusRow {

    // An emptiness has no mark to show, so the line is words alone. Named rather than passing a bare
    // null, so the call below reads as a line with no crest rather than as a crest that failed to
    // resolve.
    private static final String NO_CREST = null;

    private SystemStatusRow() {
    }

    /**
     * Resolves the status line for a system holding nobody the player may be told about.
     *
     * <p>Answered off the known listing - everybody the box may name - so the line appears exactly
     * where the breakdown beneath it would have nothing to say, and never over a box that goes on
     * to name somebody. The rule arrives from the caller rather than being read here, which is
     * what keeps the two in step: a status resolved under a rule of its own would eventually call
     * a system empty that the body below it goes on to fill.
     *
     * <p>Withholding a colony never leaks. A colony the rule holds back fails the projection
     * outright, so its system keeps its status line and the absence of one never becomes a tell
     * that something is hiding there.
     *
     * @param sector           the sector whose economy is read
     * @param system           the hovered system
     * @param colonyVisibility what the player may be shown of a colony, passed by the caller so
     *                         the status agrees with whatever that caller's own reads admit
     * @return the Decivilised or Unpopulated row, or empty when the player knows of a colony here
     */
    public static Optional<TooltipRow.CentredRow> resolveStatusRow(
            SectorAPI sector,
            StarSystemAPI system,
            ColonyVisibility colonyVisibility) {

        var colonies = SystemColonies.readColoniesIn(sector, system);

        if (colonies.hasKnownColony(colonyVisibility)) {
            return Optional.empty();
        }
        var statusKey = DecivilisedMarkets.hasRevealedDecivilisedPlanet(system)
            ? KmuStrings.POLITICAL_MAP_TOOLTIP_DECIVILISED
            : KmuStrings.POLITICAL_MAP_TOOLTIP_UNPOPULATED;

        // Set across the box, crestless: the status qualifies the whole system rather than being one
        // entry of a list, so it is spoken for the box the way the decree above it is - laid in the
        // columns instead, it would read as the first row of a breakdown that has none.
        return Optional.of(CellTooltipRows.buildBannerRow(
            NO_CREST,
            KmuStrings.get(statusKey)));
    }
}
