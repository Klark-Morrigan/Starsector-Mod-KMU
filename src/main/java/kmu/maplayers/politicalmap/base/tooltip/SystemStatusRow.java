package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

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
     * Resolves the status line for a system the player knows of nobody living in.
     *
     * <p>Answered off the same known projection the cell beneath the box is classified on, so a
     * system drawn as settled cannot be called unpopulated by the box over it. What parts that
     * projection from a narrower "has the player been here" is the colony surfaced into the open
     * ahead of being reached - publicly listed and unvisited. The cell counts it, so a narrower
     * gate here would report two readings of one system inside a single hover.
     *
     * <p>The wider arm leaks nothing. A base that is concealed <em>and</em> unfound fails the
     * projection too, so its system keeps its status line and the absence of one never becomes a
     * tell that something is hiding there. All a discovery gate withholds beyond that is a colony
     * the game itself lists on the star's own tooltip.
     *
     * @param sector                           the sector whose economy is read
     * @param system                           the hovered system
     * @param shouldIncludeUndiscoveredMarkets whether an undiscovered colony still counts as
     *                                         populating the system (the "show undiscovered
     *                                         markets" dev reveal); passed by the caller so the
     *                                         status agrees with whatever that caller's own reads
     *                                         admit, rather than calling a system empty that the
     *                                         body below it fills
     * @return the Decivilised or Unpopulated row, or empty when the player knows of a colony here
     */
    public static Optional<TooltipRow.CentredRow> resolveStatusRow(
            SectorAPI sector,
            StarSystemAPI system,
            boolean shouldIncludeUndiscoveredMarkets) {

        var colonies = SystemColonies.readColoniesIn(sector, system);

        if (colonies.hasKnownColony(shouldIncludeUndiscoveredMarkets)) {
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
