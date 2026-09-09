package kmu.maplayers.base.geometry.ui.overlays;

import kmu.maplayers.base.geometry.Coastlines;
import kmu.maplayers.base.geometry.LaidCoast;
import kmu.maplayers.base.geometry.NamedRegion;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.VoidSection;
import kmu.maplayers.base.geometry.VoidSections;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

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
 * <p><b>A pointer names only what the map drew.</b> A pocket is offered to the readout when it
 * is on screen - its wall drawn AND its water filled - and not otherwise. Both, because either
 * alone leaves nothing a reader could have been pointing at: a fill with no wall is water whose
 * edge the map never drew, and a wall with no fill is a line with nothing behind it. Named
 * regardless, the readout answers about void the reader cannot see, cannot point at a second
 * time, and cannot check the answer against.
 *
 * <p>Separate from whether each name is WRITTEN on its pocket, which is its own switch: one
 * decides what the map says when asked, the other what it says unprompted.
 *
 * <p>The sections are taken at the cells' own reach whatever the pocket shaping is set to. What
 * a piece of void is and what it is called are facts about the void itself; the shaping decides
 * only how much of it is drawn.
 */
public final class VoidSectionsOverlay {

    private final ViewerSettings settings;

    // What the last naming produced, held rather than recomputed while painting: a frame that
    // redid it would be drawing marks measured against geometry the rest of the frame is not
    // being drawn from.
    private List<NamedRegion> inland = List.of();
    private List<NamedRegion> coastal = List.of();

    public VoidSectionsOverlay(ViewerSettings settings) {
        this.settings = settings;
    }

    /**
     * Finds the sections again and names them.
     *
     * @param fixture the sector to name in, which carries the system ids a section is named
     *                from as well as the sites it is measured against
     */
    public void refresh(SectorFixture fixture) {

        inland = List.of();
        coastal = List.of();

        // Nothing reads the sections but the readout and the written names, so with every one
        // of those off the trace below would be paid for an answer no one receives.
        if (!isAnySectionWanted()) {
            return;
        }

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
     * The sections a reader could be pointing at: those whose pocket is on screen.
     *
     * <p>Each kind on its own terms, so a reader who has put one construction aside is not
     * told about it by the pointer while looking at the other.
     *
     * @return the named sections of every pocket the map is drawing
     */
    public List<NamedRegion> collectShownSections() {

        var shown = new ArrayList<NamedRegion>(inland.size() + coastal.size());

        if (isInlandPocketShown()) {
            shown.addAll(inland);
        }
        if (isCoastalPocketShown()) {
            shown.addAll(coastal);
        }
        return shown;
    }

    /**
     * Writes each section's name on it.
     *
     * @param g2            what to draw with, untransformed - this is screen space
     * @param worldToScreen the transform the map was drawn under
     */
    public void paintNames(Graphics2D g2, AffineTransform worldToScreen) {

        if (!settings.showSectorVoid) {
            return;
        }

        if (settings.showInlandNames) {
            NamedRegions.paintNames(g2, worldToScreen, inland, settings.regionNameColour);
        }
        if (settings.showCoastalNames) {
            NamedRegions.paintNames(g2, worldToScreen, coastal, settings.regionNameColour);
        }
    }

    // Whether an inland pocket is on screen, which is what makes it something to point at.
    private boolean isInlandPocketShown() {

        return settings.showSectorVoid
            && settings.showInlandBridges
            && settings.showInlandFill;
    }

    // The coastal pocket's own answer. Its wall is the coastline where the inland pocket's is
    // a bridge, which is the whole of the difference between the two.
    private boolean isCoastalPocketShown() {

        return settings.showSectorVoid
            && settings.showCoastline
            && settings.showCoastalFill;
    }

    // Whether anything will ask for the sections at all: a pointer can name a pocket that is
    // on screen, and either kind's names can be written across it.
    private boolean isAnySectionWanted() {

        return isInlandPocketShown()
            || isCoastalPocketShown()
            || (settings.showSectorVoid
                && (settings.showInlandNames || settings.showCoastalNames));
    }
}
