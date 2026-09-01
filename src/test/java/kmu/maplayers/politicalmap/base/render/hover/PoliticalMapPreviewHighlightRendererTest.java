package kmu.maplayers.politicalmap.base.render.hover;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.lists.ListPicker;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.sidebar.FilterHoverSlot;
import kmu.maplayers.base.theme.HoverGlowStyle;
import kmu.maplayers.base.theme.HoverHighlightStyle;
import kmu.maplayers.base.theme.HoverWashStyle;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.base.theme.ThemeFixtures;
import kmu.maplayers.politicalmap.base.BlocPickerRead;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.politics.BlocPresenceIndex;
import kmu.maplayers.politicalmap.base.render.style.FactionPaletteSlot;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritoryFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.politicalmap.base.politics.BlocPresenceIndexFixtures.buildIndexOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the decision behind the picker preview: what the pointer resting on a row puts on the map,
 * and the three ways that comes to nothing. The emission itself is a static GL pass covered where
 * it lives; what is decided here is whether it is reached at all, and in whose colour.
 *
 * <p>The shade is asserted against the previewed bloc's own faction rather than against anything
 * the frame painted, which is the whole of what separates this from the cursor's highlight: the
 * cells lit for a bloc are not necessarily cells it holds, so a shade read off the cell under them
 * would answer in a rival's colour or in none.
 */
final class PoliticalMapPreviewHighlightRendererTest {

    // The view the frame painted, whose id is also the scope a hover is reported under.
    private static final String VIEW_ID = "factions";

    // The bloc the pointer rests on, and the one system the walk found it in. The cell is drawn
    // by the frame below, since a preview can only ever light what the map is showing.
    private static final String BLOC_ID = "hegemony";
    private static final String PRESENT_SYSTEM_ID = "corvus";

    // The previewed bloc's own two authored shades, so a case can tell the shade of the bloc from
    // the neutral grey an unresolved faction falls back to.
    private static final Color BLOC_BRIGHT_COLOUR = Color.RED;
    private static final Color BLOC_DARK_COLOUR = Color.BLUE;

    // A preview tier that actually paints: it points at a palette slot, which is the one thing
    // about a tier that decides whether the pass is reached at all. The weights are inert, the
    // emission they shape being no part of this decision.
    private static final HoverHighlightStyle PAINTING_TIER = new HoverHighlightStyle(
        FactionPaletteSlot.PRIMARY,
        new HoverGlowStyle(0, 0, 0, 0, 0),
        new HoverWashStyle(0, 0, 0));

    // The sector the previewed bloc's shade is read from, and the machinery installed on it - the
    // one sector every case here previews over, since a preview is one sector's throughout.
    private final SectorAPI sectorMock = buildSectorColouring(BLOC_ID);
    private final MapLayerInstallation installation = new MapLayerInstallation(sectorMock);

    @Nested
    class ResolvePreviewPaint {

        @Test
        void resolvePreviewPaintLightsTheHoveredBlocsSystemsInThatBlocsOwnShade() {

            var view = stubViewFinding(buildIndexOf(BLOC_ID, PRESENT_SYSTEM_ID));
            hover(BLOC_ID);

            var paint = new PoliticalMapPreviewHighlightRenderer(installation)
                .resolvePreviewPaint(buildFrameDrawing(view, PAINTING_TIER, PRESENT_SYSTEM_ID));

            assertThat(paint.isPainting())
                .isTrue();
            assertThat(paint.colour())
                .isEqualTo(BLOC_BRIGHT_COLOUR);
        }

        @Test
        void resolvePreviewPaintLightsNothingWhenThePointerIsOnNoRow() {
            // The resting state of every frame the player is not on the picker, and so the answer
            // this has to reach without reading a theme or walking a sector.
            var view = stubViewFinding(buildIndexOf(BLOC_ID, PRESENT_SYSTEM_ID));

            var paint = new PoliticalMapPreviewHighlightRenderer(installation)
                .resolvePreviewPaint(buildFrameDrawing(view, PAINTING_TIER, PRESENT_SYSTEM_ID));

            assertThat(paint.isPainting())
                .isFalse();
        }

        @Test
        void resolvePreviewPaintLightsNothingWhenTheHoveredBlocIsPresentNowhere() {
            // A row is under the pointer and the tier paints, but the walk found that bloc in no
            // system - so there is nothing on the map the preview could trace.
            var view = stubViewFinding(BlocPresenceIndex.EMPTY);
            hover(BLOC_ID);

            var paint = new PoliticalMapPreviewHighlightRenderer(installation)
                .resolvePreviewPaint(buildFrameDrawing(view, PAINTING_TIER, PRESENT_SYSTEM_ID));

            assertThat(paint.isPainting())
                .isFalse();
        }

        @Test
        void resolvePreviewPaintLightsNothingWhenTheTierPointsAtNoShade() {
            // The player has set the preview to "No color", which is where this feature is switched
            // off - there being no gate of its own in front of it.
            var view = stubViewFinding(buildIndexOf(BLOC_ID, PRESENT_SYSTEM_ID));
            hover(BLOC_ID);

            var paint = new PoliticalMapPreviewHighlightRenderer(installation)
                .resolvePreviewPaint(buildFrameDrawing(
                    view,
                    ThemeFixtures.NO_HIGHLIGHT,
                    PRESENT_SYSTEM_ID));

            assertThat(paint.isPainting())
                .isFalse();
        }
    }

    // Records the pointer resting on one bloc's row, through the same slot the sidebar's picker
    // writes into - the scope being the painted view's id, as the binder reports it under.
    private void hover(String blocId) {
        FilterHoverSlot
            .resolveHoverSlotIn(installation)
            .recordHoveredId(VIEW_ID, blocId);
    }

    // One frame's draw lists: the stated view and preview tier, plus a shape for each cell named,
    // since a set can only light cells the frame actually painted. The cells are held by nobody,
    // which is enough for the decision - which cluster each sits in is the assembly's own question
    // and is pinned where that lives.
    private static PoliticalMapTerritories buildFrameDrawing(
            PoliticalMapView view,
            HoverHighlightStyle previewTier,
            String... drawnCellIds) {

        var territories = PoliticalMapTerritoryFixtures.createTerritoriesThemedFor(
            view,
            new RenderStyle(
                ThemeFixtures.createGlobalStylePreviewingWith(previewTier),
                Map.of()));

        for (var cellId : drawnCellIds) {
            territories.putStyledCell(
                cellId,
                PoliticalMapTerritoryFixtures.createPlaceholderStyledCell(),
                buildSquare());
        }
        return territories;
    }

    // A sector whose one faction carries the two shades a previewed bloc is expected to paint in;
    // every other id reads back as absent and so falls to the neutral grey.
    private static SectorAPI buildSectorColouring(String factionId) {

        var factionMock = mock(FactionAPI.class);
        when(factionMock.getBrightUIColor())
            .thenReturn(BLOC_BRIGHT_COLOUR);
        when(factionMock.getDarkUIColor())
            .thenReturn(BLOC_DARK_COLOUR);

        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getFaction(factionId))
            .thenReturn(factionMock);

        return sectorMock;
    }

    // A square standing in for a cell's painted extent, counter-clockwise. Any shape with an area
    // does: what the decision turns on is whether the frame drew the cell at all.
    private static List<double[]> buildSquare() {
        return List.of(
            new double[] {0, 0},
            new double[] {100, 0},
            new double[] {100, 100},
            new double[] {0, 100});
    }

    // A view whose walk found the stated presence, under the id the hover is scoped by. The picker
    // half of its read is empty: the preview reads only where a bloc was found, never the rows.
    //
    // Stubbed through doReturn because the seam answers a wildcarded read, which a when() stub
    // would have to name the captured item type of.
    private PoliticalMapView stubViewFinding(BlocPresenceIndex presenceIndex) {

        var viewMock = mock(PoliticalMapView.class);

        when(viewMock.getId())
            .thenReturn(VIEW_ID);

        doReturn(new BlocPickerRead<>(ListPicker.empty(), presenceIndex))
            .when(viewMock)
            .resolveBlocPickerRead(sectorMock);

        return viewMock;
    }
}
