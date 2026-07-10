package kmu.maplayers.politicalmap.base.render.model;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;
import kmu.settings.FactionPaletteChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
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
 * first build, that {@link PoliticalMapDrawables#isEmpty} tracks either draw list, and
 * that the constructor threads its twelve inputs into the matching accessors (four of them
 * same-typed {@link MapStyle} bundles a swap would not otherwise catch, and the view and
 * grouping the incremental re-shape classifies against).
 */
final class PoliticalMapDrawablesTest {

    @Nested
    class CreateEmpty {

        @Test
        void createEmptyYieldsAnEmptyNoOpFallback() {
            // A stand-in view so this model test names no concrete view: the drawables only carry
            // the view for the incremental re-shape to read back, so any PoliticalMapView serves.
            PoliticalMapView viewMock = mock(PoliticalMapView.class);
            var drawables = PoliticalMapDrawables.createEmpty(viewMock);

            // Both draw lists empty, so the render is a no-op and isEmpty short-circuits
            // the GL state push; the retained inputs are neutral placeholders the next
            // frame's real build replaces before any incremental pass reads them.
            assertThat(drawables.isEmpty()).isTrue();
            assertThat(drawables.getStyledCellBySystemId()).isEmpty();
            assertThat(drawables.getFactionTerritoryByFactionId()).isEmpty();
            assertThat(drawables.getOwnerBySystemId()).isEmpty();
            assertThat(drawables.getDecivilisedSystemIds()).isEmpty();
            assertThat(drawables.getNeutralColor()).isEqualTo(Color.GRAY);
            assertThat(drawables.getDesaturationPalette())
                    .isEqualTo(new FactionPalette(Color.GRAY, Color.GRAY));
        }
    }

    @Nested
    class IsEmpty {

        @Test
        void isEmptyIsTrueWhenBothDrawListsAreEmpty() {
            var drawables = drawablesWith(Map.of(), Map.of());

            assertThat(drawables.isEmpty()).isTrue();
        }

        @Test
        void isEmptyIsFalseWhenAStyledCellIsPresent() {
            var drawables = drawablesWith(Map.of("system", anyStyledCell()), Map.of());

            assertThat(drawables.isEmpty()).isFalse();
        }

        @Test
        void isEmptyIsFalseWhenAFactionTerritoryIsPresent() {
            var drawables = drawablesWith(Map.of(), Map.of("faction", anyFactionTerritory()));

            assertThat(drawables.isEmpty()).isFalse();
        }
    }

    @Nested
    class Getters {

        @Test
        void gettersReturnEachConstructorInputInItsMatchingSlot() {
            Map<String, StyledCell> styledCells = new LinkedHashMap<>();
            Map<String, FactionTerritory> territories = new LinkedHashMap<>();
            Map<String, DominantOwner> owners = new LinkedHashMap<>();
            Set<String> decivilised = new LinkedHashSet<>();
            var neutral = Color.CYAN;
            var desaturationPalette = new FactionPalette(Color.MAGENTA, Color.ORANGE);
            // Four distinct instances so a swapped style field is caught by identity, not
            // just by the shared MapStyle type the compiler would accept either way.
            var factionStyle = styleMarked(1);
            var independentStyle = styleMarked(2);
            var decivilisedStyle = styleMarked(3);
            var uninhabitedStyle = styleMarked(4);
            PoliticalMapView viewMock = mock(PoliticalMapView.class);
            var grouping = OwnershipGrouping.identity();

            var drawables = new PoliticalMapDrawables(styledCells, territories, owners,
                    decivilised, neutral, desaturationPalette, factionStyle, independentStyle,
                    decivilisedStyle, uninhabitedStyle, viewMock, grouping);

            assertThat(drawables.getStyledCellBySystemId()).isSameAs(styledCells);
            assertThat(drawables.getFactionTerritoryByFactionId()).isSameAs(territories);
            assertThat(drawables.getOwnerBySystemId()).isSameAs(owners);
            assertThat(drawables.getDecivilisedSystemIds()).isSameAs(decivilised);
            assertThat(drawables.getNeutralColor()).isSameAs(neutral);
            assertThat(drawables.getDesaturationPalette()).isSameAs(desaturationPalette);
            assertThat(drawables.getFactionStyle()).isSameAs(factionStyle);
            assertThat(drawables.getIndependentStyle()).isSameAs(independentStyle);
            assertThat(drawables.getDecivilisedStyle()).isSameAs(decivilisedStyle);
            assertThat(drawables.getUninhabitedStyle()).isSameAs(uninhabitedStyle);
            assertThat(drawables.getView()).isSameAs(viewMock);
            assertThat(drawables.getGrouping()).isSameAs(grouping);
        }
    }

    // A drawables whose only varying inputs are the two draw lists; the retained inputs
    // are inert placeholders, since isEmpty reads only the draw lists.
    private static PoliticalMapDrawables drawablesWith(Map<String, StyledCell> styledCells,
            Map<String, FactionTerritory> territories) {
        PoliticalMapView viewMock = mock(PoliticalMapView.class);
        return new PoliticalMapDrawables(new LinkedHashMap<>(styledCells),
                new LinkedHashMap<>(territories), new LinkedHashMap<>(), new LinkedHashSet<>(),
                Color.GRAY, null, null, null, null, null, viewMock,
                OwnershipGrouping.identity());
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
        return new FactionTerritory(new float[0], hiddenPaint(), List.of(), hiddenPaint(), 0f);
    }

    // A MapStyle whose opacities and widths carry one marker value, so four otherwise
    // interchangeable style bundles are distinct instances.
    private static MapStyle styleMarked(double marker) {
        return new MapStyle(FactionPaletteChoice.PRIMARY, marker,
                FactionPaletteChoice.PRIMARY, marker, marker,
                FactionPaletteChoice.PRIMARY, marker, marker);
    }
}
