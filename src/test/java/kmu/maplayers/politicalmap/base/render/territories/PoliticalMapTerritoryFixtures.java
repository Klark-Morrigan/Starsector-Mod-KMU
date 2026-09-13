package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.maplayers.base.geometry.CellKeyFixture;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.render.clusters.StyledCluster;
import kmu.maplayers.base.render.clusters.StyledClusterGroup;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.base.theme.ThemeFixtures;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.ViewGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.render.ContentInputs;
import kmu.maplayers.politicalmap.base.render.ContentInputsFixtures;
import kmu.maplayers.politicalmap.base.render.style.FactionPaletteSlot;
import kmu.maplayers.politicalmap.base.render.style.PoliticalMapCategory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;

/**
 * Shared fixtures for the tests that build or read a {@link PoliticalMapTerritories}: an inert
 * one to hang a test's own state on, the uniform theme a build runs against when the styling is
 * not what is under test, and the two placeholder draw records that stand in wherever a cell or
 * a territory only has to exist rather than draw.
 *
 * <p>One home for these because a built territories takes five constructor arguments of which
 * most tests vary one, so each suite that hand-rolled its own was repeating the same four
 * inert placeholders - and a placeholder that drifts between suites is the kind of difference
 * that makes two tests of the same behaviour quietly disagree.
 *
 * <p>Everything comes back live and mutable, so a suite with a specialised need writes its own
 * state on top through the model's own accessors and keeps that variant local to itself.
 */
public final class PoliticalMapTerritoryFixtures {

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
     * <p>Every held system counts as inhabited, since a bloc holds one only by having a colony in
     * it: a fixture where the two disagreed would pose a map production cannot build, and the
     * readers that gate on inhabitation - the factionless classifier, the band pass - would answer
     * a held cell as empty space.
     *
     * <p>That makes the two sets identical here, so a case telling them apart - one that must
     * fail if a reader gates on the holding where it should gate on inhabitation - has to state
     * its own through {@link #createTerritoriesSettledIn}.
     *
     * @param ownerBySystemId who holds each system, the one input most callers vary
     * @return a live territories, ready to have draw records written into it
     */
    public static PoliticalMapTerritories createTerritoriesOwnedBy(
            Map<String, DominantHolder> ownerBySystemId) {
        return createTerritoriesOwnedByKeys(buildKeyedHolders(ownerBySystemId));
    }

    /**
     * The same territories with its holders stated by key rather than by name, for the one case a
     * name cannot pose: two systems sharing a vanilla id, which differ only in the entity arms.
     *
     * @param ownerBySystemKey who holds each system, addressed as the build addresses it
     * @return a live territories, ready to have draw records written into it
     */
    public static PoliticalMapTerritories createTerritoriesOwnedByKeys(
            Map<SystemKey, DominantHolder> ownerBySystemKey) {

        return createTerritoriesUnder(
            ContentInputsFixtures.createInputsSpotlighting(null), // No bloc spotlighted.
            ownerBySystemKey,
            ownerBySystemKey.keySet(),
            Set.of());
    }

    /**
     * The same territories with the settled systems stated apart from the held ones, for a case
     * about a system something stands in that this layer's holding does not account for - an
     * unclaimed pirate haven on the claims layer.
     *
     * <p>The distinction is invisible under {@link #createTerritoriesOwnedBy}, where the two sets
     * are equal by construction, so a reader that confused them would answer every case there
     * correctly. Stating a settled system no bloc holds is the only arrangement that tells the
     * two reads apart.
     *
     * @param ownerBySystemId    who holds each system
     * @param inhabitedSystemIds every system something stands in, which the held systems are
     *                           expected to be a subset of
     * @return a live territories, ready to have draw records written into it
     */
    public static PoliticalMapTerritories createTerritoriesSettledIn(
            Map<String, DominantHolder> ownerBySystemId,
            Set<String> inhabitedSystemIds) {
        return createTerritoriesSpotlighting(
            null, // No bloc spotlighted.
            ownerBySystemId,
            inhabitedSystemIds,
            Set.of());
    }

    // The holders a case states by name, re-addressed by the key the build holds them under. Every
    // key states the id arm alone, which is what lets a case go on naming its systems "A" and "B"
    // while the model under it is addressed the way a live build is.
    private static Map<SystemKey, DominantHolder> buildKeyedHolders(
            Map<String, DominantHolder> ownerBySystemId) {

        var ownerBySystemKey = new LinkedHashMap<SystemKey, DominantHolder>();

        for (var entry : ownerBySystemId.entrySet()) {
            ownerBySystemKey.put(CellKeyFixture.buildCellKey(entry.getKey()), entry.getValue());
        }
        return ownerBySystemKey;
    }

    // The systems a case names, re-addressed the same way, for the two membership sets beside the
    // holder map.
    private static Set<SystemKey> buildKeyedSystems(Set<String> systemIds) {
        return new LinkedHashSet<>(CellKeyFixture.buildCellKeys(systemIds.toArray(String[]::new)));
    }

    /**
     * The same territories with a bloc spotlighted, for a case about the recede a spotlight sinks
     * the rest of the sector under - or about the settled systems the pick lives in that spare it.
     *
     * <p>The spotlight state is a value on the built model rather than something a caller can
     * write on top afterwards, so a case that needs one states it here rather than editing a
     * territories built without.
     *
     * @param selectedBlocId           the spotlighted bloc's id
     * @param ownerBySystemId          who holds each system
     * @param inhabitedSystemIds       every system something stands in
     * @param spotlitPresenceSystemIds the settled systems the pick lives in that nobody holds
     * @return a live territories, ready to have draw records written into it
     */
    public static PoliticalMapTerritories createTerritoriesSpotlighting(
            String selectedBlocId,
            Map<String, DominantHolder> ownerBySystemId,
            Set<String> inhabitedSystemIds,
            Set<String> spotlitPresenceSystemIds) {

        return createTerritoriesUnder(
            ContentInputsFixtures.createInputsSpotlighting(selectedBlocId),
            buildKeyedHolders(ownerBySystemId),
            buildKeyedSystems(inhabitedSystemIds),
            buildKeyedSystems(spotlitPresenceSystemIds));
    }

    /**
     * The same territories built under a stated name format, for a case about what the drawn names
     * cost the map around them - the room a presence band keeps clear of.
     *
     * <p>Stated on the built model rather than through the preference the sidebar writes, because
     * that is where a pass reads it: the format is sampled once per rebuild and carried, so a pass
     * folding into a standing map spells its names the way that map was baked, not the way the save
     * currently holds.
     *
     * @param ownerBySystemId who holds each system
     * @param nameFormat      how this build's cluster labels spell their holders' names
     * @return a live territories, ready to have draw records written into it
     */
    public static PoliticalMapTerritories createTerritoriesSpellingNames(
            Map<String, DominantHolder> ownerBySystemId,
            FactionNameFormatChoice nameFormat) {

        var ownerBySystemKey = buildKeyedHolders(ownerBySystemId);

        return createTerritoriesUnder(
            ContentInputsFixtures.createInputsSpellingNames(nameFormat),
            ownerBySystemKey,
            ownerBySystemKey.keySet(),
            Set.of());
    }

    // The one construction every builder above lands on, differing only in the picks it was baked
    // under and who is where. Held in one place so a slot no builder varies - the inert theme, the
    // stand-in view - cannot come to read one way through one entry point and another through the
    // next.
    private static PoliticalMapTerritories createTerritoriesUnder(
            ContentInputs contentInputs,
            Map<SystemKey, DominantHolder> ownerBySystemKey,
            Set<SystemKey> inhabitedSystemKeys,
            Set<SystemKey> spotlitPresenceSystemKeys) {

        return new PoliticalMapTerritories(
            SystemOccupancy.createCopyOf(
                ownerBySystemKey,
                inhabitedSystemKeys,
                spotlitPresenceSystemKeys),
            new LinkedHashSet<>(),
            new MapStyling(
                null, // No render style.
                NEUTRAL_PALETTE, // Neutral palette.
                NEUTRAL_PALETTE, // Desaturation palette.
                NEUTRAL_PALETTE), // Presence palette.
            new ViewGrouping(
                mock(PoliticalMapView.class),
                HolderGrouping.identity()),
            contentInputs,
            new LinkedHashSet<>()); // No contested system IDs.
    }

    /**
     * A territories under a stated view and theme rather than the inert pair the builders above
     * carry, for a suite whose subject is what the theme says about a bloc that view surfaced.
     *
     * <p>Those two are the only inputs the builders above leave unreachable: a caller can write
     * holders, cells and a spotlight on top through the model's own accessors, but the view and
     * the theme are read-only once built - and a theme left null answers a global-tier read with
     * nothing at all rather than with a tier that paints nothing.
     *
     * @param view        the view the build painted, whose id names the scope a hover is read under
     * @param renderStyle the theme the build styled against, its global tier included
     * @return a live territories holding nobody, ready to have draw records written into it
     */
    public static PoliticalMapTerritories createTerritoriesThemedFor(
            PoliticalMapView view,
            RenderStyle renderStyle) {

        return new PoliticalMapTerritories(
            SystemOccupancy.createEmpty(),
            new LinkedHashSet<>(),
            new MapStyling(
                renderStyle,
                NEUTRAL_PALETTE, // Neutral palette.
                NEUTRAL_PALETTE, // Desaturation palette.
                NEUTRAL_PALETTE), // Presence palette.
            new ViewGrouping(view, HolderGrouping.identity()),
            ContentInputs.createEmpty(),
            Set.of());
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
     * @return a live theme, keyed by the four political categories
     */
    public static RenderStyle createRenderStyleForEveryCategory(CategoryStyle style) {
        var categories = new LinkedHashMap<MapStyleCategory, CategoryStyle>();
        for (var category : PoliticalMapCategory.values()) {
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
     * @return a placeholder cluster group standing in for one bloc's territory
     */
    public static StyledClusterGroup createTerritoryWithLoops(
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
}
