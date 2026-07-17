package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;

import kmlib.testfixtures.starsector.ui.map.ModelviewMatrixReaderFake;

import kmu.maplayers.politicalmap.base.geometry.SystemClusterIndex;
import kmu.maplayers.politicalmap.base.hover.PoliticalMapHover;
import kmu.maplayers.politicalmap.base.hover.PoliticalMapHoverState;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.mockito.MockedStatic;

import java.nio.IntBuffer;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins what the cursor read publishes for a frame: the cell under the cursor with the territory
 * around it, or a parked hover whenever any input the read depends on is missing. Parking is the
 * whole point of the guards - a wrong hover would wash the wrong cell and answer the tooltip with
 * the wrong system, so every reason the read cannot be trusted has to mean "no hover" and never
 * "a guess".
 *
 * <p>The map transform is built out of hand-chosen matrices rather than a live pass, so the cursor
 * pixels below map to world points that can be read off by eye: the cell polygon spans a known
 * square and the cursor is placed inside or outside it deliberately.
 */
final class PoliticalMapHoverPublisherTest {

    // A screen the two axes disagree on, so an axis swap anywhere in the read cannot pass by
    // coincidence. The viewport matches it 1:1 - the pixel-scaled case is CampaignMapTransform's
    // to pin, not this publisher's.
    private static final float SCREEN_WIDTH = 800f;
    private static final float SCREEN_HEIGHT = 600f;
    private static final int[] VIEWPORT = {0, 0, (int) SCREEN_WIDTH, (int) SCREEN_HEIGHT};

    // A pan and a zoom baked into the pass, both non-trivial: at zero pan or a zoom of 1 the
    // arithmetic that undoes them is invisible, so a publisher that dropped either would pass.
    private static final float PAN_X = 10f;
    private static final float PAN_Y = 20f;
    private static final float MAP_ZOOM = 2f;

    // The zoom a snapshot cannot be used at: it is what the world point would be divided by, so
    // CampaignMapTransform reports no point rather than one at infinity.
    private static final float UNUSABLE_ZOOM = 0f;

    private static final String HOVERED_SYSTEM_ID = "corvus";
    private static final String NEIGHBOUR_SYSTEM_ID = "yma";

    // The hovered system's painted cell: a square spanning 100..300 by 50..250 in world
    // coordinates, wide enough that the cursor below lands well inside it rather than on an edge,
    // where two abutting cells could both claim the point.
    private static final List<double[]> CELL_POLYGON = List.of(
            new double[] {100d, 50d},
            new double[] {300d, 50d},
            new double[] {300d, 250d},
            new double[] {100d, 250d});

    // Unprojects to world (200, 150) - the cell's centre - once the pan comes off and the zoom
    // divides out: ((410 - 10) / 2, (320 - 20) / 2).
    private static final int CURSOR_X_ON_CELL = 410;
    private static final int CURSOR_Y_ON_CELL = 320;

    // Unprojects to world (0, 0), outside the cell: the empty space beyond the map, or the
    // channel between two cells, where the map draws nobody's territory.
    private static final int CURSOR_X_OFF_CELL = 10;
    private static final int CURSOR_Y_OFF_CELL = 20;

    // Column-major, the layout gluUnProject expects. The map's pass composes a translation, so a
    // translation is what a readable modelview looks like here - identity is refused as a reading
    // that cannot have come from the map.
    private static float[] buildTranslationMatrix(float translateX, float translateY) {
        var matrix = new float[] {
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        };
        matrix[12] = translateX;
        matrix[13] = translateY;
        return matrix;
    }

    private static PoliticalMapHoverPublisher buildPublisherReading(float[] modelviewMatrix) {
        return new PoliticalMapHoverPublisher(new ModelviewMatrixReaderFake(modelviewMatrix));
    }

    // A publisher whose transform reads back exactly as the map's pass left it, so a test that is
    // not about the transform gets one that resolves.
    private static PoliticalMapHoverPublisher buildPublisherOnALiveMap() {
        return buildPublisherReading(buildTranslationMatrix(PAN_X, PAN_Y));
    }

    // The frame's draw lists with one drawn cell, clustered with a neighbour so a published hover
    // proves it carries the whole territory and not just the cell it resolved.
    private static PoliticalMapCache buildCacheWithOneCell() {
        var territoriesMock = mock(PoliticalMapTerritories.class);
        when(territoriesMock.getFillPolygonBySystemId())
                .thenReturn(Map.of(HOVERED_SYSTEM_ID, CELL_POLYGON));
        when(territoriesMock.getClusterIndex()).thenReturn(SystemClusterIndex.indexClusters(
                List.of(List.of(HOVERED_SYSTEM_ID, NEIGHBOUR_SYSTEM_ID))));
        var cacheMock = mock(PoliticalMapCache.class);
        when(cacheMock.getTerritories()).thenReturn(territoriesMock);
        return cacheMock;
    }

    @Nested
    class PublishHoverFrom {

        private MockedStatic<GL11> glMock;
        private MockedStatic<Mouse> mouseMock;
        private PoliticalMapHoverState hoverState;

        @BeforeEach
        void setUp() {
            var settingsMock = mock(SettingsAPI.class);
            when(settingsMock.getScreenWidth()).thenReturn(SCREEN_WIDTH);
            when(settingsMock.getScreenHeight()).thenReturn(SCREEN_HEIGHT);
            // The real static seam rather than a mocked Global: the publisher resolves its logger
            // through the same class, and stubbing all of Global would hand it a null one.
            Global.setSettings(settingsMock);
            // glGetInteger reports through the buffer it is handed and leaves its position alone,
            // so the stub writes absolutely, the way the capture reads it back.
            glMock = mockStatic(GL11.class);
            glMock.when(() -> GL11.glGetInteger(eq(GL11.GL_VIEWPORT), any(IntBuffer.class)))
                    .thenAnswer(invocation -> {
                        IntBuffer buffer = invocation.getArgument(1);
                        for (var slot = 0; slot < VIEWPORT.length; slot++) {
                            buffer.put(slot, VIEWPORT[slot]);
                        }
                        return null;
                    });
            mouseMock = mockStatic(Mouse.class);
            mouseMock.when(Mouse::isInsideWindow).thenReturn(true);
            mouseMock.when(Mouse::getX).thenReturn(CURSOR_X_ON_CELL);
            mouseMock.when(Mouse::getY).thenReturn(CURSOR_Y_ON_CELL);
            // A standing hover from an earlier frame, so a parking assertion distinguishes "parked"
            // from "left alone": both publish nothing new, only the first clears.
            hoverState = PoliticalMapHoverState.getInstance();
            hoverState.publishHover(new PoliticalMapHover("stale", List.of("stale")));
        }

        @AfterEach
        void tearDown() {
            // The holder and the settings are shared, so what this test planted must not reach
            // another.
            hoverState.clearHover();
            Global.setSettings(null);
            mouseMock.close();
            glMock.close();
        }

        @Test
        void publishHoverFromPublishesTheHoveredCellWithItsCluster() {
            buildPublisherOnALiveMap().publishHoverFrom(buildCacheWithOneCell(), MAP_ZOOM);

            // Every input the read threads shows up in this one hover: the cursor resolves to the
            // cell only if the viewport, the pan and the zoom all reached the transform, and the
            // neighbour rides along only if the cluster was looked up off the resolved cell.
            assertThat(hoverState.getHover().hoveredSystemId()).isEqualTo(HOVERED_SYSTEM_ID);
            assertThat(hoverState.getHover().clusterMemberSystemIds())
                    .containsExactly(HOVERED_SYSTEM_ID, NEIGHBOUR_SYSTEM_ID);
        }

        @Test
        void publishHoverFromParksTheHoverWhenTheCursorIsOverNoCell() {
            mouseMock.when(Mouse::getX).thenReturn(CURSOR_X_OFF_CELL);
            mouseMock.when(Mouse::getY).thenReturn(CURSOR_Y_OFF_CELL);

            buildPublisherOnALiveMap().publishHoverFrom(buildCacheWithOneCell(), MAP_ZOOM);

            assertThat(hoverState.getHover()).isSameAs(PoliticalMapHover.NONE);
        }

        @Test
        void publishHoverFromParksTheHoverWhenNothingWasPainted() {
            // No production draw lists: the debug overlay replaced them, or the first build has yet
            // to succeed. There are no cell shapes to test the cursor against at all.
            var cacheMock = mock(PoliticalMapCache.class);
            when(cacheMock.getTerritories()).thenReturn(null);

            buildPublisherOnALiveMap().publishHoverFrom(cacheMock, MAP_ZOOM);

            assertThat(hoverState.getHover()).isSameAs(PoliticalMapHover.NONE);
        }

        @Test
        void publishHoverFromParksTheHoverWhenTheCursorLeftTheWindow() {
            // The cursor still reports its last position inside the window, so a publisher that
            // skipped this check would happily keep the cell it was last over lit up.
            mouseMock.when(Mouse::isInsideWindow).thenReturn(false);

            buildPublisherOnALiveMap().publishHoverFrom(buildCacheWithOneCell(), MAP_ZOOM);

            assertThat(hoverState.getHover()).isSameAs(PoliticalMapHover.NONE);
        }

        @Test
        void publishHoverFromParksTheHoverWhenTheTransformIsUnusable() {
            // A reader that serves no matrix is the reading a degraded binding gives; the transform
            // is unusable and no cell may be resolved from it.
            buildPublisherReading(null).publishHoverFrom(buildCacheWithOneCell(), MAP_ZOOM);

            assertThat(hoverState.getHover()).isSameAs(PoliticalMapHover.NONE);
        }

        @Test
        void publishHoverFromParksTheHoverWhenTheCursorCannotBeUnprojected() {
            // A zoom of zero leaves the snapshot with no world point to report, so the cell the
            // cursor sits over must not be published on the strength of a point that never came.
            buildPublisherOnALiveMap().publishHoverFrom(buildCacheWithOneCell(), UNUSABLE_ZOOM);

            assertThat(hoverState.getHover()).isSameAs(PoliticalMapHover.NONE);
        }
    }
}
