package kmu.maplayers.base.geometry.ui.overlays;

import kmu.maplayers.base.geometry.BridgedContinents;
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
 * <p>One name per piece of void, and the pieces are what the walls left: the spans and the
 * coast's reaches shut a long corridor into separate holes before there is anything to name,
 * so nothing here divides or draws a division.
 *
 * <p>Named off the whole laying rather than off whichever layers are drawn. What a piece of
 * void IS does not change with a switch, so a section keeps its name and its extent while the
 * layers over it come and go - which is what lets a reader turn one off to look underneath it
 * and find the same names still there.
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
     * @param laid    the laying to name the void of, shared with the drawing of the same frame
     */
    public void refresh(SectorFixture fixture, BridgedContinents laid) {

        inland = List.of();
        coastal = List.of();

        // Nothing reads the sections but the readout and the written names, so with every one
        // of those off the walls below would be laid for an answer no one receives.
        if (!isAnySectionWanted()) {
            return;
        }

        // Every wall, because a wall closes void the cells did not close on their own and those
        // pieces are sections like any other - and every set of them, drawn or not, since what
        // divides a piece of void off is not a question about what is on screen.
        var walled = laid.layEveryWall();

        var foundInland = new ArrayList<NamedRegion>();
        var foundCoastal = new ArrayList<NamedRegion>();

        for (var named : VoidSections.collectNamedSections(walled, fixture.getSystemIds())) {

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

        if (!settings.showContinentVoid) {
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
    //
    // Any span and any inland fill rather than one named pair, because this construction lays
    // its walls in four sets and fills the water in five layers, and which of them holds a
    // given section is a fact about that section rather than about the kind. Named to one pair,
    // the pointer would go silent over a lake it is plainly drawing because the switch it was
    // told to watch belongs to the puddles.
    private boolean isInlandPocketShown() {

        return settings.showContinentVoid
            && isAnySpanDrawn()
            && isAnyInlandWaterFilled();
    }

    // The coastal pocket's own answer. Its wall is the coastline where the inland pocket's is
    // a span, which is the whole of the difference between the two.
    private boolean isCoastalPocketShown() {

        return settings.showContinentVoid
            && settings.showContinentCoastline
            && settings.showContinentCoastFill;
    }

    // Whether any of the walls an inland section can close on is being drawn.
    private boolean isAnySpanDrawn() {

        return settings.showContinentBridges
            || settings.showContinentLakeBridges
            || settings.showContinentPuddleBridges
            || settings.showIntercontinentalBridges;
    }

    // Whether any of the layers an inland section's water can be drawn in is filled. The outer
    // shores' own fill is not among them: water behind a coast reach is a coastal section, and
    // counting it here would offer inland names over a map drawing none of their water.
    private boolean isAnyInlandWaterFilled() {

        return settings.showContinentInletFill
            || settings.showContinentLakeFill
            || settings.showContinentLakePocketFill
            || settings.showContinentPuddleFill
            || settings.showIntercontinentalFill;
    }

    // Whether anything will ask for the sections at all: a pointer can name a pocket that is
    // on screen, and either kind's names can be written across it.
    private boolean isAnySectionWanted() {

        return isInlandPocketShown()
            || isCoastalPocketShown()
            || (settings.showContinentVoid
                && (settings.showInlandNames || settings.showCoastalNames));
    }
}
