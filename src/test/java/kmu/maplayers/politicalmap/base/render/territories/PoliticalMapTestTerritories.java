package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.maplayers.base.render.regions.StyledCell;
import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

/**
 * Shared fixtures for the tests that read a built {@link PoliticalMapTerritories}: an inert
 * one to hang a test's own state on, and the two placeholder draw records that stand in
 * wherever a cell or a territory only has to exist rather than draw.
 *
 * <p>One home for these because a built territories takes six constructor arguments of which
 * most tests vary one, so each suite that hand-rolled its own was repeating the same five
 * inert placeholders - and a placeholder that drifts between suites is the kind of difference
 * that makes two tests of the same behaviour quietly disagree.
 *
 * <p>Everything comes back live and mutable, so a suite with a specialised need writes its own
 * state on top through the model's own accessors and keeps that variant local to itself.
 */
public final class PoliticalMapTestTerritories {
    /** The shared neutral shade unowned ground resolves to, wherever a test asserts against it. */
    public static final Color NEUTRAL_COLOUR = Color.GRAY;

    // Fixtures only; never instantiated.
    private PoliticalMapTestTerritories() {
    }

    /**
     * A territories carrying the given owners and nothing else: empty draw lists, an inert theme
     * and grouping, and no filter.
     *
     * <p>The view is a Mockito mock rather than a concrete one so a test naming no view stays
     * agnostic about which exist - the model only carries it for an incremental re-shape to read
     * back, and nothing here re-shapes.
     *
     * @param ownerBySystemId who holds each system, the one input most callers vary
     * @return a live territories, ready to have draw records written into it
     */
    public static PoliticalMapTerritories createTerritoriesOwnedBy(
            Map<String, DominantOwner> ownerBySystemId) {
        return new PoliticalMapTerritories(
                new LinkedHashMap<>(ownerBySystemId),
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                new MapStyling(
                    null,
                    NEUTRAL_COLOUR,
                    new FactionPalette(NEUTRAL_COLOUR, NEUTRAL_COLOUR)),
                new ViewGrouping(
                    mock(PoliticalMapView.class),
                    OwnershipGrouping.identity()),
                new FilterSnapshot(null, BlocStyleAdjustment.NONE, new LinkedHashSet<>()));
    }

    /**
     * A cell draw record that exists but puts no ink on the map - every run empty and every
     * paint hidden.
     *
     * @return a placeholder styled cell
     */
    public static StyledCell createPlaceholderStyledCell() {
        return new StyledCell(
                new float[0], new float[0], new float[0],
                createHiddenPaint(), createHiddenPaint(), createHiddenPaint(), 0f, 0f);
    }

    /**
     * A territory carrying only its traced loops, which is all a cursor read looks at; its fills
     * and paints are inert.
     *
     * @param borderLoops the loops to carry, empty for a territory that baked no border
     * @return a placeholder faction territory
     */
    public static FactionTerritory createTerritoryWithLoops(List<float[]> borderLoops) {
        return new FactionTerritory(
                new float[0], new float[0], createHiddenPaint(),
                borderLoops, createHiddenPaint(), 0f);
    }

    // A hidden element paint (null colour) - enough to stand in wherever a draw record only
    // needs to exist, since the draw pass skips it on sight.
    private static UiElementPaint createHiddenPaint() {
        return new UiElementPaint(null, 0f);
    }
}
