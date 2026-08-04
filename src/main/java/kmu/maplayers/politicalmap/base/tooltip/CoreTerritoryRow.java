package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.TooltipRow;
import kmlib.text.KmlibStrings;

import kmu.maplayers.base.tooltip.CellTooltipRows;
import kmu.util.KmuStrings;

import java.util.Optional;

/**
 * The line naming the faction that holds a hovered system as core territory - a system handed to it
 * by decree rather than won by the markets in it.
 *
 * <p>A core is a different kind of fact from a score, so it is not laid as one: it is a verdict about
 * the whole system, drawn centred directly under the system's name where it reads as part of the
 * heading, rather than as an entry in the breakdown of who holds what. The faction's crest travels
 * inside the line as one of its runs, so crest, name, and decree centre together as one sentence
 * instead of the crest sitting out in the gutter the entries below align to.
 *
 * <p>"core territory" is the qualifier run, so the shade it reads in stays the vocabulary's decision
 * rather than this row's. The row resolves empty for a system under no decree, so a body can offer it
 * unconditionally - and it is offered whatever else the system holds, since a decree stands over a
 * populated system exactly as it does over an empty one.
 */
public final class CoreTerritoryRow {

    private CoreTerritoryRow() {
    }

    /**
     * Resolves the core-territory line for a system held by decree.
     *
     * @param sector        the sector the faction's name and crest are read from
     * @param coreFactionId the id of the faction holding the system as core territory, or null (or
     *                      blank) when no decree holds it
     * @return the core-territory row, or empty when there is no core faction
     */
    public static Optional<TooltipRow.CentredRow> resolveCoreTerritoryRow(
            SectorAPI sector,
            String coreFactionId) {

        if (!KmlibStrings.hasText(coreFactionId)) {
            return Optional.empty();
        }
        // The faction named as any other line names it, then the decree called out beside it as the
        // point of the line.
        return Optional.of(FactionTooltipRow
            .buildFactionBannerRow(sector, coreFactionId)
            .continuesWith(CellTooltipRows.buildQualifierSpan(
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CORE_TERRITORY))));
    }
}
