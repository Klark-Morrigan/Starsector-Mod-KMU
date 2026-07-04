package kmu.politicalmap.render;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.politicalmap.render.model.ClusterAnchor;
import kmu.politicalmap.render.model.ClusterAnchor.AxisSegment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the pure placement-to-label decision - {@link FactionLabelsBuilder#planLabels} -
 * which turns the resolved cluster placements plus the sector's faction names into the
 * name, colour, hang point, and slant each label draws with, before any GL string is
 * minted. The string minting, font load, and rendering only resolve in-engine, so they are
 * not covered here; the decision that drives them is.
 */
final class FactionLabelsBuilderTest {

    private static final Color OWNER_COLOR = Color.RED;

    @Nested
    class PlanLabels {

        @Test
        void planLabelsResolvesTheOwnerLongDisplayNameForAnAcceptedPlacement() {
            var sector = sectorWithFaction("pl", "Persean League");
            var anchors = List.of(acceptedAnchor("pl", 100f, 200f,
                    new AxisSegment(0f, 200f, 200f, 200f)));

            var plans = FactionLabelsBuilder.planLabels(anchors, sector);

            assertThat(plans).singleElement()
                    .satisfies(plan -> assertThat(plan.name()).isEqualTo("Persean League"));
        }

        @Test
        void planLabelsHangsTheLabelAtTheAnchorMidpointInTheOwnerColor() {
            var sector = sectorWithFaction("pl", "Persean League");
            var anchors = List.of(acceptedAnchor("pl", 100f, 200f,
                    new AxisSegment(0f, 200f, 200f, 200f)));

            var plan = FactionLabelsBuilder.planLabels(anchors, sector).get(0);

            assertThat(plan.hangX()).isEqualTo(100f);
            assertThat(plan.hangY()).isEqualTo(200f);
            assertThat(plan.color()).isEqualTo(OWNER_COLOR);
        }

        @Test
        void planLabelsTakesTheSlantFromTheAcceptedAxis() {
            // A line rising 45 degrees to the right: the label leans at +45.
            var sector = sectorWithFaction("pl", "Persean League");
            var anchors = List.of(acceptedAnchor("pl", 50f, 50f,
                    new AxisSegment(0f, 0f, 100f, 100f)));

            var plan = FactionLabelsBuilder.planLabels(anchors, sector).get(0);

            assertThat(plan.slantDegrees()).isCloseTo(45f, within(1e-3f));
        }

        @Test
        void planLabelsFoldsALeftPointingAxisUprightSoTheNameIsNotUpsideDown() {
            // The accepted axis points into the left half-plane (end left of start). Left as
            // is it would render the name upside down (~180 degrees); folded upright it reads
            // left-to-right at the same shallow lean (here dead level, 0).
            var sector = sectorWithFaction("pl", "Persean League");
            var anchors = List.of(acceptedAnchor("pl", 100f, 200f,
                    new AxisSegment(200f, 200f, 0f, 200f)));

            var plan = FactionLabelsBuilder.planLabels(anchors, sector).get(0);

            assertThat(plan.slantDegrees()).isCloseTo(0f, within(1e-3f));
        }

        @Test
        void planLabelsSkipsACollapsedPlacementWithNoAcceptedAxis() {
            // A cluster whose search collapsed to the dot carries no accepted line, so it
            // gets no name rather than an empty box.
            var sector = sectorWithFaction("pl", "Persean League");
            var anchors = List.of(collapsedAnchor("pl", 100f, 200f));

            assertThat(FactionLabelsBuilder.planLabels(anchors, sector)).isEmpty();
        }

        @Test
        void planLabelsSkipsAClusterWhoseFactionDoesNotResolve() {
            // An owner id with no live faction (a mod removed mid-save) resolves no name, so
            // the cluster is skipped rather than the plan throwing.
            var sectorMock = mock(SectorAPI.class);
            when(sectorMock.getFaction("gone")).thenReturn(null);
            var anchors = List.of(acceptedAnchor("gone", 100f, 200f,
                    new AxisSegment(0f, 200f, 200f, 200f)));

            assertThat(FactionLabelsBuilder.planLabels(anchors, sectorMock)).isEmpty();
        }

        @Test
        void planLabelsSkipsAClusterWithABlankFactionName() {
            var sector = sectorWithFaction("pl", "   ");
            var anchors = List.of(acceptedAnchor("pl", 100f, 200f,
                    new AxisSegment(0f, 200f, 200f, 200f)));

            assertThat(FactionLabelsBuilder.planLabels(anchors, sector)).isEmpty();
        }
    }

    // A sector whose one faction id resolves to the given long display name.
    private static SectorAPI sectorWithFaction(String factionId, String displayNameLong) {
        var sectorMock = mock(SectorAPI.class);
        var factionMock = mock(FactionAPI.class);
        when(factionMock.getDisplayNameLong()).thenReturn(displayNameLong);
        when(sectorMock.getFaction(factionId)).thenReturn(factionMock);
        return sectorMock;
    }

    // A placement that accepted a label line, hung at (anchorX, anchorY) with the given
    // accepted axis - the input a label is built from.
    private static ClusterAnchor acceptedAnchor(String factionId, float anchorX, float anchorY,
            AxisSegment acceptedAxis) {
        return new ClusterAnchor(anchorX, anchorY, OWNER_COLOR, factionId, acceptedAxis,
                null, null, 100f, 1);
    }

    // A collapsed placement: only the dot, no accepted line, so no name is drawn.
    private static ClusterAnchor collapsedAnchor(String factionId, float anchorX, float anchorY) {
        return new ClusterAnchor(anchorX, anchorY, OWNER_COLOR, factionId, null, null, null,
                0f, 0);
    }
}
