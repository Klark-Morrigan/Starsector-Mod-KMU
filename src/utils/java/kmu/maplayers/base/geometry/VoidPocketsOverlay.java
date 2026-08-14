package kmu.maplayers.base.geometry;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * The first construction over the void, drawn: pockets the cells close around, cut into
 * sections.
 *
 * <p>Its own class so it can be switched off, drawn, and judged beside
 * {@link VoidBridgesOverlay} without either being able to disturb the other. The two are
 * rivals over the same emptiness and one of them is meant to lose, so nothing they share
 * beyond {@link ViewerPainting} is worth arranging - a shared base would have to be unpicked
 * again the moment one is deleted.
 *
 * <p>It draws in two passes because it has two kinds of thing to say. The fills go down
 * before the cells, so a stray edge reads as the mistake it is rather than painting over the
 * shape it got wrong; the cuts go over the top, because whether one lands where a corridor
 * is actually pinched can only be judged against the pocket it crosses.
 */
final class VoidPocketsOverlay {

    // The mark standing in for a pocket with no room to draw. Fixed in world units like the
    // site dots, so it reads as a pin on the map rather than as a shape of that size.
    private static final float MARK_RADIUS = 200f;

    private final ViewerSettings settings;

    private List<VoidPockets.VoidPocket> pockets = List.of();

    VoidPocketsOverlay(ViewerSettings settings) {
        this.settings = settings;
    }

    /**
     * Finds the pockets again, or drops them when the overlay is switched off.
     *
     * <p>Emptied rather than skipped at paint time, so every pass over them reads one list
     * and nothing has to remember to check the toggle a second time.
     *
     * @param fixture the sector to find them in
     */
    void refresh(SectorFixture fixture) {

        pockets = settings.showVoidPockets
            ? VoidPockets.findVoidPockets(
                fixture.getSites(),
                fixture.getOwnerBySite(),
                settings.parameters,
                buildSectionRules())
            : List.of();
    }

    /**
     * Draws the pockets themselves, beneath the cells.
     *
     * @param g2 what to draw with
     */
    void paintFills(Graphics2D g2) {

        // The same length the pocket was divided into sections of, so a pocket cannot be
        // coloured as too long to be one thing while holding one section, or the reverse.
        var wideEnough = measureSectionLength();

        for (var pocket : pockets) {

            var isWide = pocket.span() > wideEnough;

            // Nothing to draw means the channel closed the pocket over, and drawing it
            // flush against the cells instead would break the one rule every other shape
            // here keeps. The mark says it is there without claiming an extent it has
            // not got.
            if (pocket.outlines().isEmpty()) {

                paintPocketMark(
                    g2,
                    pocket.centre(),
                    isWide ? settings.wideVoidColour : settings.voidCellColour);

                continue;
            }

            for (var outline : pocket.outlines()) {

                var path = ViewerPainting.buildPath(outline);

                if (pocket.absorbingOwner() != null) {

                    var fill = ViewerPainting.resolveOwnedColour(settings, pocket.absorbingOwner());
                    ViewerPainting.paintFilledShape(g2, path, fill, settings.ownedCellOpacity, fill);
                    continue;
                }
                ViewerPainting.paintFilledShape(
                    g2,
                    path,
                    isWide ? settings.wideVoidColour : settings.voidCellColour,
                    settings.voidCellOpacity,
                    isWide ? settings.wideVoidEdge : settings.voidCellEdge);
            }
        }
    }

    /**
     * Draws where each pocket is cut into sections, over the top of everything.
     *
     * @param g2 what to draw with
     */
    void paintSpans(Graphics2D g2) {

        g2.setStroke(new BasicStroke(ViewerPainting.SPAN_STROKE));
        g2.setColor(ViewerPainting.applyAlpha(
            settings.sectionCutColour, ViewerPainting.OPAQUE_ALPHA));

        for (var pocket : pockets) {

            // Away from the cells when a pocket is pushed out to meet one owner's fills,
            // towards them when it is pulled in to leave a border.
            var trim = pocket.absorbingOwner() == null
                ? settings.parameters.borderInset()
                : -settings.parameters.borderInset();

            for (var cut : pocket.division().cuts()) {

                g2.draw(ViewerPainting.buildTrimmedSpan(cut, trim));
            }
        }
    }

    private VoidSections.SectionRules buildSectionRules() {

        return new VoidSections.SectionRules(measureSectionLength(), settings.minSectionShare);
    }

    // The same length twice over: the span past which a pocket is too long to be one thing is
    // also the length the pieces it is cut into should be, so the slider that decides one
    // decides the other and a pocket can never be called too long while being cut into
    // sections of some other size.
    private double measureSectionLength() {
        return settings.voidSpanMultiple * settings.parameters.cellRadius();
    }

    private void paintPocketMark(Graphics2D g2, double[] centre, Color colour) {

        var mark = new Path2D.Double();

        mark.moveTo(centre[0], centre[1] + MARK_RADIUS);
        mark.lineTo(centre[0] + MARK_RADIUS, centre[1]);
        mark.lineTo(centre[0], centre[1] - MARK_RADIUS);
        mark.lineTo(centre[0] - MARK_RADIUS, centre[1]);
        mark.closePath();

        g2.setColor(ViewerPainting.applyAlpha(colour, settings.voidCellOpacity));
        g2.fill(mark);
    }
}
