package kmu.maplayers.base.geometry.v4.ui;

import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.render.FillLook;
import kmu.maplayers.base.geometry.render.MapPainting;
import kmu.maplayers.base.geometry.settings.ViewerSettings;
import kmu.maplayers.base.geometry.v4.BareVoid;

import java.awt.Graphics2D;
import java.util.List;

/**
 * What v4 has to show: the void the cells close around, before any line divides it.
 *
 * <p>v4's whole surface on screen for now, and deliberately the least it can be. The
 * constructions either side of the switch have to be comparable from the first frame, and a
 * layer that draws an unfinished division would be compared against a finished one.
 *
 * <p>This is also the picture the face walk is judged by. The outlines drawn here come from the
 * disc sweep with nothing laid across it, which is the one reading of the bare void with no
 * construction's opinion in it - so when v4 divides the void for itself, these are the pieces it
 * has to still agree with.
 *
 * <p>The void is read on each refresh rather than held from startup, for the reason every other
 * overlay reads its own: the reach and the flattening are knobs, and a copy taken when the
 * window opened would go on drawing the sector those knobs used to describe.
 */
public final class BareVoidOverlay {

    private final ViewerSettings settings;

    // What the last refresh read, kept so a frame paints the void the rest of the frame was
    // drawn from rather than a reading taken while painting.
    private List<List<double[]>> outlines = List.of();

    public BareVoidOverlay(ViewerSettings settings) {
        this.settings = settings;
    }

    /**
     * Reads the bare void again.
     *
     * @param fixture the sector to read, for the sites the void lies between
     */
    public void refresh(SectorFixture fixture) {

        // Nothing else reads this, so with the layer off the trace would be paid for on every
        // rebuild to answer no one.
        outlines = settings.isBareVoidShown()
            ? BareVoid
                .readBareVoid(fixture.getSites(), settings.parameters)
                .collectOutlines()
            : List.of();
    }

    /**
     * Fills each piece of void.
     *
     * @param g2 where to draw, in world space
     */
    public void paintPieces(Graphics2D g2) {

        if (!settings.isBareVoidShown()) {
            return;
        }
        MapPainting.paintRingFills(
            g2,
            outlines,
            new FillLook(
                settings.bareVoidColour, settings.voidFillOpacity, settings.bareVoidColour));
    }
}
