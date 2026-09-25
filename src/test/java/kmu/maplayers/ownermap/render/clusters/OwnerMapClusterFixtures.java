package kmu.maplayers.ownermap.render.clusters;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.maplayers.base.render.clusters.PaintedCell;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.render.clusters.StyledCluster;
import kmu.maplayers.base.render.clusters.StyledClusterGroup;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.base.theme.ThemeFixtures;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.ViewGrouping;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.preferences.FactionNameFormatChoice;
import kmu.maplayers.ownermap.render.style.FactionPaletteSlot;
import kmu.maplayers.ownermap.render.style.OwnerMapCategory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedSystemKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedValues;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared fixtures for the tests that build or read a {@link OwnerMapClusters}: an inert
 * one to hang a test's own state on, the uniform theme a build runs against when the styling is
 * not what is under test, and the two placeholder draw records that stand in wherever a cell or
 * a cluster group only has to exist rather than draw.
 *
 * <p>One home for these because a built clusters is baked under five inputs of which most
 * tests vary one, so each suite that hand-rolled its own was repeating the same four inert
 * placeholders - and a placeholder that drifts between suites is the kind of difference that
 * makes two tests of the same behaviour quietly disagree.
 *
 * <p>Everything comes back live and mutable, so a suite with a specialised need writes its own
 * state on top through the model's own accessors and keeps that variant local to itself.
 */
public final class OwnerMapClusterFixtures {

    /** The shared neutral shade an unowned cell resolves to, wherever a test asserts against it. */
    public static final Color NEUTRAL_COLOUR = Color.GRAY;

    /**
     * That shade as the pair a factionless cell paints in - the neutral in both slots, since such a
     * cell names no faction to take two shades from. Shared so a case that only needs an inert
     * palette says so by naming this rather than by spelling out a grey pair whose two equal slots
     * read as a choice somebody made.
     */
    public static final FactionPalette NEUTRAL_PALETTE =
        new FactionPalette(NEUTRAL_COLOUR, NEUTRAL_COLOUR);

    // Fixtures only; never instantiated.
    private OwnerMapClusterFixtures() {
    }

    /**
     * A clusters carrying the given holders and nothing else: empty draw lists, an inert theme
     * and grouping, and no filter.
     *
     * <p>The view is a Mockito mock rather than a concrete one so a test naming no view stays
     * agnostic about which exist - the model only carries it for an incremental re-shape to read
     * back, and nothing here re-shapes.
     *
     * <p>Every held system counts as inhabited, since a bloc holds one only by having a colony in
     * it: a fixture where the two disagreed would pose a map production cannot build, and the
     * readers that gate on inhabitation - the factionless classifier, the band pass - would answer
     * a held cell as empty space.
     *
     * <p>That makes the two sets identical here, so a case telling them apart - one that must
     * fail if a reader gates on the holding where it should gate on inhabitation - has to state
     * its own through {@link #createClustersSettledIn}.
     *
     * @param ownerBySystemId who holds each system, the one input most callers vary
     * @return a live clusters, ready to have draw records written into it
     */
    public static OwnerMapClusters createClustersOwnedBy(
            Map<String, SystemOwner> ownerBySystemId) {
        return createClustersOwnedByKeys(buildKeyedValues(ownerBySystemId));
    }

    /**
     * The same clusters with its holders stated by key rather than by name, for the one case a
     * name cannot pose: two systems sharing a vanilla ID, which differ only in the entity arms.
     *
     * @param ownerBySystemKey who holds each system, addressed as the build addresses it
     * @return a live clusters, ready to have draw records written into it
     */
    public static OwnerMapClusters createClustersOwnedByKeys(
            Map<SystemKey, SystemOwner> ownerBySystemKey) {

        return createClustersUnder(
            ContentInputsFixtures.createInputsSpotlighting(null), // No bloc spotlighted.
            ownerBySystemKey,
            ownerBySystemKey.keySet(),
            Set.of());
    }

    /**
     * The same clusters with the settled systems stated apart from the held ones, for a case
     * about a system something stands in that this layer's holding does not account for.
     *
     * <p>The distinction is invisible under {@link #createClustersOwnedBy}, where the two sets
     * are equal by construction, so a reader that confused them would answer every case there
     * correctly. Stating a settled system no bloc holds is the only arrangement that tells the
     * two reads apart.
     *
     * @param ownerBySystemId    who holds each system
     * @param inhabitedSystemIds every system something stands in, which the held systems are
     *                           expected to be a subset of
     * @return a live clusters, ready to have draw records written into it
     */
    public static OwnerMapClusters createClustersSettledIn(
            Map<String, SystemOwner> ownerBySystemId,
            Set<String> inhabitedSystemIds) {
        return createClustersSpotlighting(
            null, // No bloc spotlighted.
            ownerBySystemId,
            inhabitedSystemIds,
            Set.of());
    }

    /**
     * The same clusters with a bloc spotlighted, for a case about the recede a spotlight sinks
     * the rest of the sector under - or about the settled systems the pick lives in that spare it.
     *
     * <p>The spotlight state is a value on the built model rather than something a caller can
     * write on top afterwards, so a case that needs one states it here rather than editing a
     * clusters built without.
     *
     * @param selectedBlocId           the spotlighted bloc's ID
     * @param ownerBySystemId          who holds each system
     * @param inhabitedSystemIds       every system something stands in
     * @param spotlitPresenceSystemIds the settled systems the pick lives in that nobody holds
     * @return a live clusters, ready to have draw records written into it
     */
    public static OwnerMapClusters createClustersSpotlighting(
            String selectedBlocId,
            Map<String, SystemOwner> ownerBySystemId,
            Set<String> inhabitedSystemIds,
            Set<String> spotlitPresenceSystemIds) {

        return createClustersUnder(
            ContentInputsFixtures.createInputsSpotlighting(selectedBlocId),
            buildKeyedValues(ownerBySystemId),
            buildKeyedSystemKeys(inhabitedSystemIds),
            buildKeyedSystemKeys(spotlitPresenceSystemIds));
    }

    /**
     * The same clusters built under a stated name format, for a case about what the drawn names
     * cost the map around them - the room a presence band keeps clear of.
     *
     * <p>Stated on the built model rather than through the preference the sidebar writes, because
     * that is where a pass reads it: the format is sampled once per rebuild and carried, so a pass
     * folding into a standing map spells its names the way that map was baked, not the way the save
     * currently holds.
     *
     * @param ownerBySystemId who holds each system
     * @param nameFormat      how this build's cluster labels spell their holders' names
     * @return a live clusters, ready to have draw records written into it
     */
    public static OwnerMapClusters createClustersSpellingNames(
            Map<String, SystemOwner> ownerBySystemId,
            FactionNameFormatChoice nameFormat) {

        var ownerBySystemKey = buildKeyedValues(ownerBySystemId);

        return createClustersUnder(
            ContentInputsFixtures.createInputsSpellingNames(nameFormat),
            ownerBySystemKey,
            ownerBySystemKey.keySet(),
            Set.of());
    }

    // The one construction every builder above lands on, differing only in the picks it was baked
    // under and who is where. Held in one place so a slot no builder varies - the inert theme, the
    // stand-in view - cannot come to read one way through one entry point and another through the
    // next.
    private static OwnerMapClusters createClustersUnder(
            ContentInputs contentInputs,
            Map<SystemKey, SystemOwner> ownerBySystemKey,
            Set<SystemKey> inhabitedSystemKeys,
            Set<SystemKey> spotlitPresenceSystemKeys) {

        return new OwnerMapClusters(
            SystemOccupancy.createCopyOf(
                ownerBySystemKey,
                inhabitedSystemKeys,
                spotlitPresenceSystemKeys),
            new OwnerMapBuildInputs(
                new MapStyling(
                    null, // No render style.
                    NEUTRAL_PALETTE, // Neutral palette.
                    NEUTRAL_PALETTE, // Desaturation palette.
                    NEUTRAL_PALETTE), // Presence palette.
                new ViewGrouping(
                    mockViewStandingNobodyTogether(),
                    HolderGrouping.identity()),
                contentInputs,
                Set.of(), // No unfilled systems.
                Set.of())); // No contested systems.
    }

    /**
     * A clusters under a stated view and theme rather than the inert pair the builders above
     * carry, for a suite whose subject is what the theme says about a bloc that view surfaced.
     *
     * <p>Those two are the only inputs the builders above leave unreachable: a caller can write
     * holders, cells and a spotlight on top through the model's own accessors, but the view and
     * the theme are read-only once built - and a theme left null answers a global-tier read with
     * nothing at all rather than with a tier that paints nothing.
     *
     * @param view        the view the build painted, whose ID names the scope a hover is read under
     * @param renderStyle the theme the build styled against, its global tier included
     * @return a live clusters holding nobody, ready to have draw records written into it
     */
    public static OwnerMapClusters createClustersThemedFor(
            OwnerPaintedView view,
            RenderStyle renderStyle) {

        return new OwnerMapClusters(
            SystemOccupancy.createEmpty(),
            new OwnerMapBuildInputs(
                new MapStyling(
                    renderStyle,
                    NEUTRAL_PALETTE, // Neutral palette.
                    NEUTRAL_PALETTE, // Desaturation palette.
                    NEUTRAL_PALETTE), // Presence palette.
                new ViewGrouping(view, HolderGrouping.identity()),
                ContentInputs.createEmpty(),
                Set.of(),
                Set.of()));
    }

    /**
     * A theme painting every one of this map's categories in the same bundle, over the shared
     * inert global tier.
     *
     * <p>For the suites whose subject is which cells a builder draws rather than what it draws
     * it in: with all four categories identical, a difference in the output can only have come
     * from the geometry or the holding, never from a category the fixture happened to style
     * differently.
     *
     * @param style the bundle every category paints from
     * @return a live theme, keyed by the four owner-map categories
     */
    public static RenderStyle createRenderStyleForEveryCategory(CategoryStyle style) {
        var categories = new LinkedHashMap<MapStyleCategory, CategoryStyle>();
        for (var category : OwnerMapCategory.values()) {
            categories.put(category, style);
        }
        return new RenderStyle(ThemeFixtures.createInertGlobalStyle(), categories);
    }

    /**
     * The bundle {@link #createRenderStyleForEveryCategory} is almost always handed: one element
     * style in every slot, at the primary palette shade and full weight.
     *
     * <p>Paired with {@code ThemeFixtures.createInertGlobalStyle} rather than left to each suite,
     * because a bundle whose slots differed between suites would let two tests of the same build
     * disagree over which slot a difference came from.
     *
     * @return a live bundle painting fill, border and seam alike
     */
    public static CategoryStyle createInertCategoryStyle() {

        var element = new ElementStyle(FactionPaletteSlot.PRIMARY, 1.0);
        return new CategoryStyle(element, element, 1.0, element, 1.0);
    }

    /**
     * That placeholder record against a stated ring, which is what the model is written through.
     *
     * <p>For the suites whose subject is the shape a cell answers with rather than the ink it puts
     * down: the ring is the whole of what they assert on, and the record beside it only has to
     * exist.
     *
     * @param paintedExtent the ring to record the cell as painted on, as {x, y} vertex pairs
     * @return a placeholder painted cell carrying that ring
     */
    public static PaintedCell createPlaceholderPaintedCellOn(List<double[]> paintedExtent) {
        return new PaintedCell(createPlaceholderStyledCell(), paintedExtent);
    }

    /**
     * A cell draw record that exists but puts no ink on the map - an empty run under a hidden
     * paint. Takes the fused form, which is the smaller of the two and the one a cell inside a
     * cluster has, since a caller wanting only a cell's presence cares about neither.
     *
     * @return a placeholder styled cell
     */
    public static StyledCell createPlaceholderStyledCell() {
        return new StyledCell.FusedCell(new float[0], createHiddenPaint(), 0f);
    }

    /**
     * A bloc's bodies carrying only their traced loops, which is all a cursor read looks at; the
     * fills and paints are inert.
     *
     * <p>Takes one loop list per body rather than one flat list, since which body a loop belongs
     * to is what a caller telling an exclave from an enclave has to be able to say. Within a
     * body the first loop is its outer ring and the rest are cut out of it.
     *
     * @param loopsPerCluster each body's loops, outer ring first; empty for a bloc that baked no
     *                        border at all
     * @return a placeholder cluster group standing in for one bloc's cluster group
     */
    public static StyledClusterGroup createClusterGroupWithLoops(
            List<List<float[]>> loopsPerCluster) {

        var clusters = new ArrayList<StyledCluster>(loopsPerCluster.size());
        for (var loops : loopsPerCluster) {
            clusters.add(new StyledCluster(
                new float[0],
                new float[0],
                loops.isEmpty() ? new float[0] : loops.get(0),
                loops.isEmpty() ? List.of() : loops.subList(1, loops.size())));
        }
        return new StyledClusterGroup(
            clusters,
            createHiddenPaint(),
            createHiddenPaint(),
            0f); // Border width.
    }

    // A hidden element paint (null colour) - enough to stand in wherever a draw record only
    // needs to exist, since the draw pass skips it on sight.
    private static UiElementPaint createHiddenPaint() {
        return new UiElementPaint(null, 0f);
    }

    // The view a clusters built above carries, answering nothing a case does not stub - bar who
    // stands together in a contest, which a band baked over it always asks: nobody, so every bloc
    // it meets is a rival.
    private static OwnerPaintedView mockViewStandingNobodyTogether() {

        var viewMock = mock(OwnerPaintedView.class);

        when(viewMock.resolveContestGrouping())
            .thenReturn(HolderGrouping.identity());

        return viewMock;
    }
}
