package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.text.KmlibStrings;

import kmu.maplayers.base.tooltip.layout.CellTooltipRows;
import kmu.util.KmuStringKeys;

import java.util.List;

/**
 * What heads a political-map box for a system held as core territory - a system handed to a faction by
 * decree rather than won by the markets in it.
 *
 * <p>A core is a different kind of fact from a score, so it is not laid as one: it is a verdict about
 * the whole system, drawn centred directly under the system's name where it reads as part of the
 * heading, rather than as an entry in the breakdown of who holds what. The faction's crest travels
 * inside the line as one of its runs, so crest, name, and decree centre together as one sentence
 * instead of the crest sitting out in the gutter the entries below align to.
 *
 * <p>"core territory" is the qualifier run, so the shade it reads in stays the vocabulary's decision
 * rather than this heading's.
 *
 * <p>Both reasons a box goes without the heading are settled here, and both answer as no rows at all: a
 * system under no decree has nothing to head with, and a box whose body already states the decree would
 * otherwise say it twice in one hover. Keeping the second beside the first is what stops a caller
 * claiming there is no decree in order to drop a line it merely does not want repeated.
 */
public final class CoreTerritoryHeading {

    private CoreTerritoryHeading() {
    }

    /**
     * Resolves what heads a box over a system held by decree.
     *
     * @param sector                 the sector the faction's name and crest are read from
     * @param coreFactionId          the ID of the faction holding the system as core territory, or null
     *                               (or blank) when no decree holds it
     * @param isDecreeStatedInBody   whether the box's own body already names the decree, in which case
     *                               heading with it would state one fact twice
     * @return the rows heading the box, empty when there is nothing left for it to say
     */
    public static List<TooltipRow> resolveHeadingRows(
            SectorAPI sector,
            String coreFactionId,
            boolean isDecreeStatedInBody) {

        if (isDecreeStatedInBody || !KmlibStrings.hasText(coreFactionId)) {
            return List.of();
        }
        // The faction named as any other line names it, then the decree called out beside it as the
        // point of the line.
        return List.of(FactionTooltipBanner
            .buildFactionBanner(sector, coreFactionId)
            .continuesWith(CellTooltipRows.buildQualifierSpan(
                KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_TOOLTIP_CORE_TERRITORY))));
    }
}
