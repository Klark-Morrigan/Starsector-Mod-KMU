package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.maplayers.base.render.regions.StyledCell;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.base.theme.ThemeFixtures;
import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.render.style.PoliticalMapCategory;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

/**
 * Shared fixtures for the tests that build or read a {@link PoliticalMapTerritories}: an inert
 * one to hang a test's own state on, the uniform theme a build runs against when the styling is
 * not what is under test, and the two placeholder draw records that stand in wherever a cell or
 * a territory only has to exist rather than draw.
 *
 * <p>One home for these because a built territories takes six constructor arguments of which
 * most tests vary one, so each suite that hand-rolled its own was repeating the same five
 * inert placeholders - and a placeholder that drifts between suites is the kind of difference
 * that makes two tests of the same behaviour quietly disagree.
 *
 * <p>Everything comes back live and mutable, so a suite with a specialised need writes its own
 * state on top through the model's own accessors and keeps that variant local to itself.
 */
public final class PoliticalMapTerritoryFixtures {
    /** The shared neutral shade unowned ground resolves to, wherever a test asserts against it. */
    public static final Color NEUTRAL_COLOUR = Color.GRAY;

    // Fixtures only; never instantiated.
    private PoliticalMapTerritoryFixtures() {
    }

    /**
     * A territories carrying the given holders and nothing else: empty draw lists, an inert theme
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
            Map<String, DominantHolder> ownerBySystemId) {
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
                    HolderGrouping.identity()),
                new FilterSnapshot(null, BlocStyleAdjustment.NONE, new LinkedHashSet<>()));
    }

    /**
     * A theme painting every one of this map's categories in the same bundle, over the shared
     * inert global tier.
     *
     * <p>For the suites whose subject is which ground a builder draws rather than what it draws
     * it in: with all four categories identical, a difference in the output can only have come
     * from the geometry or the holding, never from a category the fixture happened to style
     * differently.
     *
     * @param style the bundle every category paints from
     * @return a live theme, keyed by the four political categories
     */
    public static RenderStyle createRenderStyleForEveryCategory(CategoryStyle style) {
        Map<MapStyleCategory, CategoryStyle> categories = new LinkedHashMap<>();
        for (var category : PoliticalMapCategory.values()) {
            categories.put(category, style);
        }
        return new RenderStyle(ThemeFixtures.createInertGlobalStyle(), categories);
    }

    /**
     * A cell draw record that exists but puts no ink on the map - an empty run under a hidden
     * paint. Takes the fused form, which is the smaller of the two and the one a cell inside a
     * region has, since a caller wanting only a cell's presence cares about neither.
     *
     * @return a placeholder styled cell
     */
    public static StyledCell createPlaceholderStyledCell() {
        return new StyledCell.FusedCell(new float[0], createHiddenPaint(), 0f);
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
