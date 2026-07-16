package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.render.style.BorderSmoothingStyle;
import kmu.maplayers.politicalmap.base.render.style.CategoryStyle;
import kmu.maplayers.politicalmap.base.render.style.GlobalStyle;
import kmu.maplayers.politicalmap.base.render.style.HatchStyle;
import kmu.maplayers.politicalmap.base.render.style.MapCategory;
import kmu.maplayers.politicalmap.base.render.style.RenderStyle;
import kmu.settings.FactionPaletteChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the built map state the renderer paints and the incremental refresh edits:
 * that the empty fallback is a harmless no-op the render path can lean on after a failed
 * first build, that {@link PoliticalMapTerritories#isEmpty} tracks either draw list, and
 * that the constructor threads its inputs into the matching accessors (the {@link RenderStyle}
 * theme, whose global tier and four same-typed category bundles a swap would not otherwise catch,
 * the view and grouping the incremental re-shape classifies against, and the filter snapshot it
 * recedes by).
 */
final class PoliticalMapTerritoriesTest {

    @Nested
    class CreateEmpty {

        @Test
        void createEmptyYieldsAnEmptyNoOpFallback() {
            // A stand-in view so this model test names no concrete view: the territories only carry
            // the view for the incremental re-shape to read back, so any PoliticalMapView serves.
            PoliticalMapView viewMock = mock(PoliticalMapView.class);
            var territories = PoliticalMapTerritories.createEmpty(viewMock);

            // Both draw lists empty, so the render is a no-op and isEmpty short-circuits
            // the GL state push; the retained inputs are neutral placeholders the next
            // frame's real build replaces before any incremental pass reads them.
            assertThat(territories.isEmpty()).isTrue();
            assertThat(territories.getStyledCellBySystemId()).isEmpty();
            assertThat(territories.getFactionTerritoryByFactionId()).isEmpty();
            assertThat(territories.getOwnerBySystemId()).isEmpty();
            assertThat(territories.getDecivilisedSystemIds()).isEmpty();
            assertThat(territories.getNeutralColor()).isEqualTo(Color.GRAY);
            assertThat(territories.getDesaturationPalette())
                    .isEqualTo(new FactionPalette(Color.GRAY, Color.GRAY));
            // The empty fallback is never a filtered build, so it selects no bloc and recedes
            // nothing.
            assertThat(territories.getSelectedBlocId()).isNull();
            assertThat(territories.isFiltering()).isFalse();
            assertThat(territories.getRecedeAdjustment()).isEqualTo(BlocStyleAdjustment.NONE);
            assertThat(territories.getContestedSystemIds()).isEmpty();
        }
    }

    @Nested
    class IsEmpty {

        @Test
        void isEmptyIsTrueWhenBothDrawListsAreEmpty() {
            var territories = drawablesWith(Map.of(), Map.of());

            assertThat(territories.isEmpty()).isTrue();
        }

        @Test
        void isEmptyIsFalseWhenAStyledCellIsPresent() {
            var territories = drawablesWith(Map.of("system", anyStyledCell()), Map.of());

            assertThat(territories.isEmpty()).isFalse();
        }

        @Test
        void isEmptyIsFalseWhenAFactionTerritoryIsPresent() {
            var territories = drawablesWith(Map.of(), Map.of("faction", anyFactionTerritory()));

            assertThat(territories.isEmpty()).isFalse();
        }
    }

    @Nested
    class Getters {

        @Test
        void gettersReturnEachConstructorInputInItsMatchingSlot() {
            Map<String, DominantOwner> owners = new LinkedHashMap<>();
            Set<String> decivilised = new LinkedHashSet<>();
            var neutral = Color.CYAN;
            var desaturationPalette = new FactionPalette(Color.MAGENTA, Color.ORANGE);
            // Four distinct category instances plus a distinct global tier, all wrapped in one
            // theme, so a swapped style slot is caught by identity, not just by the shared
            // CategoryStyle type the compiler would accept either way.
            var factionStyle = styleMarked(1);
            var independentStyle = styleMarked(2);
            var decivilisedStyle = styleMarked(3);
            var uninhabitedStyle = styleMarked(4);
            Map<MapCategory, CategoryStyle> categories = new EnumMap<>(MapCategory.class);
            categories.put(MapCategory.FACTION, factionStyle);
            categories.put(MapCategory.INDEPENDENT, independentStyle);
            categories.put(MapCategory.DECIVILISED, decivilisedStyle);
            categories.put(MapCategory.UNINHABITED, uninhabitedStyle);
            var globalStyle = new GlobalStyle(new HatchStyle(5, 5, 5),
                    new BorderSmoothingStyle(true, true, 5, 5, 5), 0.3);
            var renderStyle = new RenderStyle(globalStyle, categories);
            PoliticalMapView viewMock = mock(PoliticalMapView.class);
            var grouping = OwnershipGrouping.identity();
            // A distinct, non-identity adjustment so a swapped recede field is caught by value.
            var recedeAdjustment = new BlocStyleAdjustment(0.25, true);
            // A non-null selected bloc so the filter snapshot is caught by value and isFiltering()
            // reads true off it.
            var selectedBlocId = "selected-bloc";
            // A distinct contested set so a swapped filter-snapshot field is caught by identity.
            Set<String> contested = new LinkedHashSet<>(Set.of("contested-system"));

            var territories = new PoliticalMapTerritories(owners, decivilised,
                    new MapStyling(renderStyle, neutral, desaturationPalette),
                    new ViewGrouping(viewMock, grouping),
                    new FilterSnapshot(selectedBlocId, recedeAdjustment, contested));

            // The two draw lists are created internally, not passed, so the build can fill them;
            // they start empty and stay mutable for the incremental refresh to edit in place.
            assertThat(territories.getStyledCellBySystemId()).isEmpty();
            assertThat(territories.getFactionTerritoryByFactionId()).isEmpty();
            assertThat(territories.getOwnerBySystemId()).isSameAs(owners);
            assertThat(territories.getDecivilisedSystemIds()).isSameAs(decivilised);
            assertThat(territories.getNeutralColor()).isSameAs(neutral);
            assertThat(territories.getDesaturationPalette()).isSameAs(desaturationPalette);
            assertThat(territories.getRenderStyle()).isSameAs(renderStyle);
            assertThat(territories.getGlobalStyle()).isSameAs(globalStyle);
            assertThat(territories.getCategoryStyle(MapCategory.FACTION)).isSameAs(factionStyle);
            assertThat(territories.getCategoryStyle(MapCategory.INDEPENDENT))
                    .isSameAs(independentStyle);
            assertThat(territories.getCategoryStyle(MapCategory.DECIVILISED))
                    .isSameAs(decivilisedStyle);
            assertThat(territories.getCategoryStyle(MapCategory.UNINHABITED))
                    .isSameAs(uninhabitedStyle);
            assertThat(territories.getView()).isSameAs(viewMock);
            assertThat(territories.getGrouping()).isSameAs(grouping);
            assertThat(territories.getSelectedBlocId()).isEqualTo(selectedBlocId);
            assertThat(territories.isFiltering()).isTrue();
            assertThat(territories.getRecedeAdjustment()).isSameAs(recedeAdjustment);
            assertThat(territories.getContestedSystemIds()).isSameAs(contested);
        }
    }

    // A territories whose only varying inputs are the two draw lists; the retained inputs
    // are inert placeholders, since isEmpty reads only the draw lists.
    private static PoliticalMapTerritories drawablesWith(Map<String, StyledCell> styledCells,
            Map<String, FactionTerritory> territories) {
        PoliticalMapView viewMock = mock(PoliticalMapView.class);
        var drawables = new PoliticalMapTerritories(new LinkedHashMap<>(), new LinkedHashSet<>(),
                new MapStyling(null, Color.GRAY, null),
                new ViewGrouping(viewMock, OwnershipGrouping.identity()),
                new FilterSnapshot(null, BlocStyleAdjustment.NONE, new LinkedHashSet<>()));
        // The draw lists are no longer constructor inputs; fill the internally-created maps so
        // this fixture's only varying state is what isEmpty reads.
        drawables.getStyledCellBySystemId().putAll(styledCells);
        drawables.getFactionTerritoryByFactionId().putAll(territories);
        return drawables;
    }

    // A hidden element paint (null color) is enough to stand in wherever a StyledCell or
    // FactionTerritory only needs to exist, not draw.
    private static UiElementPaint hiddenPaint() {
        return new UiElementPaint(null, 0f);
    }

    private static StyledCell anyStyledCell() {
        return new StyledCell(new float[0], new float[0], new float[0],
                hiddenPaint(), hiddenPaint(), hiddenPaint(), 0f, 0f);
    }

    private static FactionTerritory anyFactionTerritory() {
        return new FactionTerritory(new float[0], new float[0], hiddenPaint(),
                new float[0], hiddenPaint(), 0f, List.of(), hiddenPaint(), 0f);
    }

    // A CategoryStyle whose opacities and widths carry one marker value, so four otherwise
    // interchangeable style bundles are distinct instances.
    private static CategoryStyle styleMarked(double marker) {
        return new CategoryStyle(FactionPaletteChoice.PRIMARY, marker,
                FactionPaletteChoice.PRIMARY, marker, marker,
                FactionPaletteChoice.PRIMARY, marker, marker);
    }
}
