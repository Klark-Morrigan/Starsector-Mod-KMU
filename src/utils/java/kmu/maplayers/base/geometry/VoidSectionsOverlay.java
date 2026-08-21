package kmu.maplayers.base.geometry;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

/**
 * The void, named.
 *
 * <p>One name per piece of void, and the pieces are what the walls left: the bridges and the
 * coast's reaches shut a long corridor into separate holes before there is anything to name,
 * so nothing here divides or draws a division.
 *
 * <p><b>Built whether or not anything is switched on.</b> Everything else here is built only
 * while it is on screen, because the work is per-frame-visible. This is not: the pointer readout
 * names whatever piece of void it is over, and a readout that only worked while the names
 * happened to be drawn would be a readout with a hidden precondition. The cost is one coast
 * trace per rebuild, beside the trace the coast overlay already does.
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
     * Finds the sections again and names them.
     *
     * @param fixture the sector to name in, which carries the system ids a section is named
     *                from as well as the sites it is measured against
     */
    void refresh(SectorFixture fixture) {

        // Walls laid, because a wall closes void the cells did not close on their own and those
        // pieces are sections like any other. Traced here rather than taken from the coast
        // overlay so that the sections do not appear and vanish with a toggle about whether the
        // coast LINE is drawn.
        var laid = LaidCoast.layCoast(
            Coastlines.traceSectorCoasts(
                fixture.getSites(), settings.parameters, settings.resolveCoastRules()),
            settings.parameters);

        var foundInland = new ArrayList<NamedRegion>();
        var foundCoastal = new ArrayList<NamedRegion>();

        for (var named : VoidSections.collectNamedSections(laid, fixture.getSystemIds())) {

            if (named.section().kind() == VoidSection.SectionKind.COASTAL) {
                foundCoastal.add(named.region());
            } else {
                foundInland.add(named.region());
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
