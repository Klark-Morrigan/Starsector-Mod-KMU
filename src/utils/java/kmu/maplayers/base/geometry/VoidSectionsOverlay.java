package kmu.maplayers.base.geometry;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

/**
 * The void cut into sections, named.
 *
 * <p>The cuts themselves are not drawn. A line across a pocket saying where it was divided is
 * what the retired first void-pocket construction put on the map, and it is not wanted back:
 * the division is a step on the way to deciding who holds each piece, not a border anybody is
 * meant to read. What the map shows of it is the NAMES, one per piece, which say the pocket
 * came out as more than one thing without drawing a boundary that nothing will ever paint.
 *
 * <p><b>Built whether or not anything is switched on.</b> Everything else here is built only
 * while it is on screen, because the work is per-frame-visible. This is not: the pointer readout
 * names whatever piece of void it is over, and a readout that only worked while the names
 * happened to be drawn would be a readout with a hidden precondition. The cost is one coast
 * trace and one division per rebuild, beside the trace the coast overlay already does.
 *
 * <p>The sections are taken at the cells' own reach whatever the pocket shaping is set to. What
 * a piece of void is and what it is called are facts about the void itself; the shaping decides
 * only how much of it is drawn.
 */
final class VoidSectionsOverlay {

    private final ViewerSettings settings;

    // What the last division produced, held rather than recomputed while painting: a frame that
    // redid it would be drawing marks measured against geometry the rest of the frame is not
    // being drawn from.
    private List<NamedRegion> inland = List.of();
    private List<NamedRegion> coastal = List.of();

    VoidSectionsOverlay(ViewerSettings settings) {
        this.settings = settings;
    }

    /**
     * Cuts the void into sections again and names them.
     *
     * @param fixture the sector to divide, which carries the system ids a section is named from
     *                as well as the sites it is measured against
     */
    void refresh(SectorFixture fixture) {

        var sites = fixture.getSites();
        var systemIds = fixture.getSystemIds();

        // Walls laid, because a wall closes void the cells did not close on their own and those
        // pieces are sections like any other. Traced here rather than taken from the coast
        // overlay so that the sections do not appear and vanish with a toggle about whether the
        // coast LINE is drawn.
        var laid = LaidCoast.layCoast(
            Coastlines.traceSectorCoasts(
                sites, settings.parameters, settings.resolveCoastRules()),
            settings.parameters);

        var foundInland = new ArrayList<NamedRegion>();
        var foundCoastal = new ArrayList<NamedRegion>();

        for (var hole : DiscUnionBoundary.traceHolesAcrossWalls(
                laid.atCells(), laid.walls(), settings.parameters.boundSegments())) {

            var section = VoidSection.buildFromHole(hole);

            var named = NamedRegion.nameRegion(
                VoidSectionIds.nameSection(section, sites, systemIds),
                section.outline());

            if (section.kind() == VoidSection.SectionKind.COASTAL) {
                foundCoastal.add(named);
            } else {
                foundInland.add(named);
            }
        }
        inland = List.copyOf(foundInland);
        coastal = List.copyOf(foundCoastal);
    }

    /**
     * Every section, whichever kind, for a reader asking which one the pointer is over.
     *
     * <p>Both kinds whatever is switched on: the pointer names the piece of void it is over,
     * and a readout that went quiet because the names happened to be hidden would be a readout
     * with a hidden precondition.
     *
     * @return the named sections
     */
    List<NamedRegion> getSections() {

        var all = new ArrayList<NamedRegion>(inland.size() + coastal.size());

        all.addAll(inland);
        all.addAll(coastal);

        return all;
    }

    /**
     * Writes each section's name on it.
     *
     * @param g2            what to draw with, untransformed - this is screen space
     * @param worldToScreen the transform the map was drawn under
     */
    void paintNames(Graphics2D g2, AffineTransform worldToScreen) {

        if (settings.showInlandNames) {
            NamedRegions.paintNames(g2, worldToScreen, inland, settings.regionNameColour);
        }
        if (settings.showCoastalNames) {
            NamedRegions.paintNames(g2, worldToScreen, coastal, settings.regionNameColour);
        }
    }
}
