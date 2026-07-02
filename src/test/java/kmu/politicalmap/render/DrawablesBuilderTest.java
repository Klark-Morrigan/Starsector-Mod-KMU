package kmu.politicalmap.render;

import kmu.politicalmap.domain.politics.DominantOwner;
import kmu.settings.FactionPaletteChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the builder's off-engine, deterministic pieces: the palette-color pick that maps
 * a player's color choice to a palette shade, and the cluster-anchor fit that turns a
 * cluster's system positions into a label anchor. The rest shapes cells and reads
 * settings that only resolve in-engine.
 */
final class DrawablesBuilderTest {

    // Two distinct shades so a pick can be told apart from its counterpart.
    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    // A faction whose bright shade the anchor marker should carry.
    private static final DominantOwner FACTION_F =
            new DominantOwner("F", PRIMARY, SECONDARY);

    @Nested
    class PickPaletteColor {

        @Test
        void pickPaletteColorReturnsThePrimaryShadeForAPrimaryChoice() {
            assertThat(DrawablesBuilder.pickPaletteColor(
                    FactionPaletteChoice.PRIMARY, PRIMARY, SECONDARY)).isEqualTo(PRIMARY);
        }

        @Test
        void pickPaletteColorReturnsTheSecondaryShadeForASecondaryChoice() {
            assertThat(DrawablesBuilder.pickPaletteColor(
                    FactionPaletteChoice.SECONDARY, PRIMARY, SECONDARY)).isEqualTo(SECONDARY);
        }

        @Test
        void pickPaletteColorReturnsNullForNoColor() {
            // NONE is the player's "No color" choice; a null color signals the render
            // layer to skip that element.
            assertThat(DrawablesBuilder.pickPaletteColor(
                    FactionPaletteChoice.NONE, PRIMARY, SECONDARY)).isNull();
        }
    }

    @Nested
    class ComputeClusterAnchors {

        @Test
        void computeClusterAnchorsCollapsesASingleSystemClusterToItsSite() {
            // One system has no spread, so its axis segment collapses onto the site and
            // only the centroid (the site itself) is meaningful.
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A")),
                    Map.of("A", new double[] {5, 3}),
                    Map.of("A", FACTION_F));

            assertThat(anchors).hasSize(1);
            var anchor = anchors.get(0);
            assertThat(anchor.centroidX()).isEqualTo(5f);
            assertThat(anchor.centroidY()).isEqualTo(3f);
            assertThat(anchor.axisStartX()).isEqualTo(5f);
            assertThat(anchor.axisEndX()).isEqualTo(5f);
        }

        @Test
        void computeClusterAnchorsSpansTheAxisAcrossTheClusterCentredOnTheCentroid() {
            // Two systems 20 apart along x: the centroid is their midpoint and the axis
            // segment reaches half the span each way, so it runs the full width.
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A", "B")),
                    Map.of("A", new double[] {-10, 0}, "B", new double[] {10, 0}),
                    Map.of("A", FACTION_F, "B", FACTION_F));

            var anchor = anchors.get(0);
            assertThat(anchor.centroidX()).isCloseTo(0f, within(1e-4f));
            assertThat(Math.min(anchor.axisStartX(), anchor.axisEndX())).isCloseTo(-10f,
                    within(1e-4f));
            assertThat(Math.max(anchor.axisStartX(), anchor.axisEndX())).isCloseTo(10f,
                    within(1e-4f));
        }

        @Test
        void computeClusterAnchorsColorsTheMarkerInTheOwningFactionsBrightShade() {
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A")),
                    Map.of("A", new double[] {0, 0}),
                    Map.of("A", FACTION_F));

            assertThat(anchors.get(0).color()).isEqualTo(PRIMARY);
        }

        @Test
        void computeClusterAnchorsSkipsAClusterWhoseSitesAreAllMissing() {
            // A cluster whose members have no site (none in the site map) has no point
            // cloud to fit, so it contributes no anchor rather than an empty fit.
            var anchors = DrawablesBuilder.computeClusterAnchors(
                    List.of(List.of("A")),
                    Map.of(),
                    Map.of("A", FACTION_F));

            assertThat(anchors).isEmpty();
        }
    }
}
