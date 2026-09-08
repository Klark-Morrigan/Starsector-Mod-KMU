package kmu.maplayers.base.render;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what the frame's section names have to be for a capture to read: one section per name, so
 * two opens of one beat land on one row rather than on two rows spelled alike, and one section per
 * thing named, so two beats or two layers are never quietly the same row.
 *
 * <p>Identity rather than equality throughout, that being what the profiler compares by: a section
 * is found among the children of the open scope by reference, so two instances carrying one name
 * would be two rows however alike they read.
 */
final class MapFrameSectionsTest {

    @Nested
    class ResolveLayerSection {

        @Test
        void resolveLayerSectionAnswersOneSectionForOneLayerId() {
            // The renderer resolves its row once and holds it, but the framework will resolve one
            // per registered layer per sector - so two resolutions of one id have to be the row,
            // not two rows a report shows side by side.
            assertThat(MapFrameSections.resolveLayerSection("political_map"))
                .isSameAs(MapFrameSections.resolveLayerSection("political_map"));
        }

        @Test
        void resolveLayerSectionAnswersASectionPerLayerId() {
            // What a layer costs is only readable if it is its own row: two layers sharing one
            // would report a sum neither of them spent.
            assertThat(MapFrameSections.resolveLayerSection("political_map"))
                .isNotSameAs(MapFrameSections.resolveLayerSection("diplomatic_map"));
        }

        @Test
        void resolveLayerSectionNamesTheRowAfterTheLayerId() {
            // The id is what a reader matches a row back to the layer by, and the prefix is what
            // gathers every layer's row under one place in the report.
            assertThat(MapFrameSections.resolveLayerSection("political_map").getName())
                .isEqualTo("mapLayer.layer.political_map");
        }
    }

    @Nested
    class ResolveRenderSection {

        @ParameterizedTest
        @EnumSource(MapOverlayBand.class)
        void resolveRenderSectionAnswersOneSectionForOneBand(MapOverlayBand band) {
            // Resolved per pass rather than held, so the answer has to be stable across the frames
            // a band is painted in.
            assertThat(MapFrameSections.resolveRenderSection(band))
                .isSameAs(MapFrameSections.resolveRenderSection(band));
        }

        @Test
        void resolveRenderSectionAnswersASectionPerBand() {
            // The bands are separate passes carrying different contents, and one row averaging the
            // two would describe neither - which is the whole reason the paint beat is per band.
            assertThat(
                MapFrameSections.resolveRenderSection(MapOverlayBand.BENEATH_STARSCAPE_NEBULAE))
                .isNotSameAs(
                    MapFrameSections.resolveRenderSection(MapOverlayBand.ABOVE_STARSCAPE_NEBULAE));
        }
    }
}
