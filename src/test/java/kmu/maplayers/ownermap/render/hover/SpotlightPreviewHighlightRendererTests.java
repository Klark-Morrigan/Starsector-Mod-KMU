package kmu.maplayers.ownermap.render.hover;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.colour.ColourPair;
import kmlib.starsector.ui.widgets.lists.ListPicker;

import kmu.KmuMod;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.sidebar.FilterHoverSlot;
import kmu.maplayers.base.sidebar.MapLayerStoreNamespaces;
import kmu.maplayers.base.sidebar.PickerScope;
import kmu.maplayers.base.theme.HoverGlowStyle;
import kmu.maplayers.base.theme.HoverHighlightStyle;
import kmu.maplayers.base.theme.HoverWashStyle;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.base.theme.ThemeFixtures;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.picker.OwnerPickerRead;
import kmu.maplayers.ownermap.picker.OwnerPresenceIndex;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.style.OwnerPaletteSlot;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.picker.OwnerPresenceIndexFixtures.buildIndexOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the decision behind the picker preview: what the pointer resting on a row puts on the map,
 * and the ways that comes to nothing. The emission itself is a static GL pass covered where it
 * lives; what is decided here is whether it is reached at all, and in whose colour.
 *
 * <p>The shade is asserted against the previewed owner's own shades in the reading the frame was
 * built under rather than against anything the frame painted, which is the whole of what separates
 * this from the cursor's highlight: the cells lit for an owner are not necessarily cells it holds, so
 * a shade read off the cell under them would answer in a rival's colour or in none.
 */
final class SpotlightPreviewHighlightRendererTests {

    // The layer whose picker is previewed, which names the memo the presence is read off.
    private static final String LAYER_ID = "test_layer";

    // The view the frame painted, whose ID is also the scope a hover is reported under.
    private static final String VIEW_ID = "owners";

    // The owner the pointer rests on, and the one system the walk found it in. The cell is drawn
    // by the frame below, since a preview can only ever light what the map is showing.
    private static final String OWNER_ID = "hegemony";
    private static final String PRESENT_SYSTEM_ID = "corvus";

    // The previewed owner's own two authored shades, distinct so a case can say which slot the tier
    // pointed at rather than only that some colour came back.
    private static final Color OWNER_BRIGHT_COLOUR = Color.RED;
    private static final Color OWNER_DARK_COLOUR = Color.BLUE;

    // A preview tier that actually paints: it points at a palette slot, which is the one thing
    // about a tier that decides whether the pass is reached at all. The weights are inert, the
    // emission they shape being no part of this decision.
    private static final HoverHighlightStyle PAINTING_TIER = new HoverHighlightStyle(
        OwnerPaletteSlot.PRIMARY,
        HoverGlowStyle.NO_GLOW,
        new HoverWashStyle(0, 0, 0));

    // The reading the frame was built under, answering the previewed owner's own shades.
    private static final OwnerReading READING_SHADING_THE_OWNER = OwnerReadingFake
        .createAnsweringNothing()
        .withLooks(Map.of(OWNER_ID, new ColourPair(OWNER_BRIGHT_COLOUR, OWNER_DARK_COLOUR)), Map.of());

    // The sector the picker's presence is read over, and the machinery installed on it - the one
    // sector every case here previews over, since a preview is one sector's throughout.
    private final SectorAPI sectorMock = mock(SectorAPI.class);

    private final SectorMapMachinery machinery = new SectorMapMachinery(sectorMock);

    @Nested
    class ResolvePreviewPaint {

        @Test
        void lightsTheHoveredOwnersSystemsInThatOwnersOwnShade() {

            var view = stubViewFinding(buildIndexOf(OWNER_ID, PRESENT_SYSTEM_ID));
            hover(OWNER_ID);

            var paint = new SpotlightPreviewHighlightRenderer(machinery, LAYER_ID, KmuMod.MAP_STORE_NAMESPACE)
                .resolvePreviewPaint(buildFrameDrawing(view, PAINTING_TIER, PRESENT_SYSTEM_ID));

            assertThat(paint.isPainting())
                .isTrue();
            assertThat(paint.colour())
                .isEqualTo(OWNER_BRIGHT_COLOUR);
        }

        @Test
        void lightsNothingWhenThePointerIsOnNoRow() {
            // The resting state of every frame the player is not on the picker, and so the answer
            // this has to reach without reading a theme or walking a sector.
            var view = stubViewFinding(buildIndexOf(OWNER_ID, PRESENT_SYSTEM_ID));

            var paint = new SpotlightPreviewHighlightRenderer(machinery, LAYER_ID, KmuMod.MAP_STORE_NAMESPACE)
                .resolvePreviewPaint(buildFrameDrawing(view, PAINTING_TIER, PRESENT_SYSTEM_ID));

            assertThat(paint.isPainting())
                .isFalse();
        }

        @Test
        void lightsNothingWhenTheHoveredOwnerIsPresentNowhere() {
            // A row is under the pointer and the tier paints, but the walk found that owner in no
            // system - so there is nothing on the map the preview could trace.
            var view = stubViewFinding(OwnerPresenceIndex.EMPTY);
            hover(OWNER_ID);

            var paint = new SpotlightPreviewHighlightRenderer(machinery, LAYER_ID, KmuMod.MAP_STORE_NAMESPACE)
                .resolvePreviewPaint(buildFrameDrawing(view, PAINTING_TIER, PRESENT_SYSTEM_ID));

            assertThat(paint.isPainting())
                .isFalse();
        }

        @Test
        void lightsNothingForAHoverReportedUnderAnotherViewsScope() {
            // The hover is read under the view the frame painted, not under whichever scope last
            // reported one. An owner ID means what the view that surfaced it says it means, and the
            // systems behind it are that view's answer - so a preview crossing a view switch would
            // light one view's set over another view's map.
            var view = stubViewFinding(buildIndexOf(OWNER_ID, PRESENT_SYSTEM_ID));

            FilterHoverSlot
                .resolveHoverSlotIn(machinery)
                .recordHoveredId(createScopeOfView("other-view"), OWNER_ID);

            var paint = new SpotlightPreviewHighlightRenderer(machinery, LAYER_ID, KmuMod.MAP_STORE_NAMESPACE)
                .resolvePreviewPaint(buildFrameDrawing(view, PAINTING_TIER, PRESENT_SYSTEM_ID));

            assertThat(paint.isPainting())
                .isFalse();
        }

        @Test
        void lightsNothingForAHoverReportedByAnotherModsPicker() {
            // The view ID is opaque and shared by nobody's agreement, so a foreign layer whose own
            // picker lists a view under this one's name is a different list: its hovered row names a
            // owner this view never surfaced, and lighting it would paint that mod's answer here.
            var view = stubViewFinding(buildIndexOf(OWNER_ID, PRESENT_SYSTEM_ID));

            FilterHoverSlot
                .resolveHoverSlotIn(machinery)
                .recordHoveredId(
                    new PickerScope(MapLayerStoreNamespaces.createStandInNamespace(), VIEW_ID),
                    OWNER_ID);

            var paint = new SpotlightPreviewHighlightRenderer(machinery, LAYER_ID, KmuMod.MAP_STORE_NAMESPACE)
                .resolvePreviewPaint(buildFrameDrawing(view, PAINTING_TIER, PRESENT_SYSTEM_ID));

            assertThat(paint.isPainting())
                .isFalse();
        }

        @Test
        void lightsNothingWhenTheReadingHasNoShadesForTheHoveredOwner() {
            // The same answer the presence bands give such an owner, and for the same reason: an owner
            // the layer can no longer colour has no shade, and lighting its cells in a stand-in one
            // would put colour on the map for something the map cannot name.
            var view = stubViewFinding(buildIndexOf(OWNER_ID, PRESENT_SYSTEM_ID));
            hover(OWNER_ID);

            var paint = new SpotlightPreviewHighlightRenderer(machinery, LAYER_ID, KmuMod.MAP_STORE_NAMESPACE)
                .resolvePreviewPaint(buildFrameDrawing(
                    view,
                    PAINTING_TIER,
                    OwnerReadingFake.createAnsweringNothing(),
                    PRESENT_SYSTEM_ID));

            assertThat(paint.isPainting())
                .isFalse();
        }

        @Test
        void lightsNothingOverThePlaceholderAFailedBuildStandsBehind() {
            // The placeholder resolved no reading, so there is no shade to ask it for.
            var view = stubViewFinding(buildIndexOf(OWNER_ID, PRESENT_SYSTEM_ID));
            hover(OWNER_ID);

            var paint = new SpotlightPreviewHighlightRenderer(machinery, LAYER_ID, KmuMod.MAP_STORE_NAMESPACE)
                .resolvePreviewPaint(OwnerMapClusters.createEmpty(view));

            assertThat(paint.isPainting())
                .isFalse();
        }

        @Test
        void lightsNothingWhenTheTierPointsAtNoShade() {
            // The player has set the preview to "No color", which is where this feature is switched
            // off - there being no gate of its own in front of it.
            var view = stubViewFinding(buildIndexOf(OWNER_ID, PRESENT_SYSTEM_ID));
            hover(OWNER_ID);

            var paint = new SpotlightPreviewHighlightRenderer(machinery, LAYER_ID, KmuMod.MAP_STORE_NAMESPACE)
                .resolvePreviewPaint(buildFrameDrawing(
                    view,
                    ThemeFixtures.NO_HIGHLIGHT,
                    PRESENT_SYSTEM_ID));

            assertThat(paint.isPainting())
                .isFalse();
        }
    }

    // The scope the sidebar's picker reports a hovered row under: a view's ID under this mod's own
    // store namespace, which is what the binder composes from the slot the picker was built for.
    private static PickerScope createScopeOfView(String viewId) {
        return new PickerScope(KmuMod.MAP_STORE_NAMESPACE, viewId);
    }

    // Records the pointer resting on one owner's row, through the same slot the sidebar's picker
    // writes into - the scope being the painted view's, as the binder reports it under.
    private void hover(String ownerId) {
        FilterHoverSlot
            .resolveHoverSlotIn(machinery)
            .recordHoveredId(createScopeOfView(VIEW_ID), ownerId);
    }

    // One frame's draw lists: the stated view and preview tier, plus a shape for each cell named,
    // since a set can only light cells the frame actually painted. The cells are held by nobody,
    // which is enough for the decision - which cluster each sits in is the assembly's own question
    // and is pinned where that lives.
    private static OwnerMapClusters buildFrameDrawing(
            OwnerPaintedView view,
            HoverHighlightStyle previewTier,
            String... drawnCellIds) {

        return buildFrameDrawing(view, previewTier, READING_SHADING_THE_OWNER, drawnCellIds);
    }

    // The same frame built under a stated reading, for the case about an owner it cannot colour.
    private static OwnerMapClusters buildFrameDrawing(
            OwnerPaintedView view,
            HoverHighlightStyle previewTier,
            OwnerReading reading,
            String... drawnCellIds) {

        var clusters = OwnerMapClusterFixtures.createClustersThemedFor(
            view,
            new RenderStyle(
                ThemeFixtures.createGlobalStylePreviewingWith(previewTier),
                Map.of()),
            reading);

        for (var cellId : drawnCellIds) {
            clusters.getPaintedCells().putPaintedCell(
                buildCellKey(cellId),
                OwnerMapClusterFixtures.createPlaceholderPaintedCellOn(buildSquare()));
        }
        return clusters;
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

    // A view whose walk found the stated presence, under the ID the hover is scoped by. The picker
    // half of its read is empty: the preview reads only where an owner was found, never the rows.
    //
    // Stubbed through doReturn because the seam answers a wildcarded read, which a when() stub
    // would have to name the captured item type of.
    private OwnerPaintedView stubViewFinding(OwnerPresenceIndex presenceIndex) {

        var viewMock = mock(OwnerPaintedView.class);

        when(viewMock.getId())
            .thenReturn(VIEW_ID);

        doReturn(new OwnerPickerRead<>(ListPicker.empty(), presenceIndex))
            .when(viewMock)
            .resolveOwnerPickerRead(sectorMock);

        return viewMock;
    }
}
