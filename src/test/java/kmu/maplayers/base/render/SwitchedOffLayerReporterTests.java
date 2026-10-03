package kmu.maplayers.base.render;

import kmlib.starsector.compatibility.CompatibilityConsumer;
import kmlib.starsector.compatibility.CompatibilityFailures;
import kmlib.starsector.compatibility.FeatureFailure;
import kmlib.testfixtures.logging.LogAppenderFake;
import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake;

import kmu.maplayers.base.layer.MapLayer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what a switched-off layer is reported as - KMU's own feature, named by the layer's tab - and
 * that a report which cannot be composed never reaches the render pass that switched it off.
 */
final class SwitchedOffLayerReporterTests {

    private static final IllegalStateException LAYER_FAILURE = new IllegalStateException("draw list half built");

    private final CompatibilityFailures failureRecord = new CompatibilityFailures();

    private final MapLayer switchedOffLayerMock = mock(MapLayer.class);

    // Each record the reporter asked the screen to raise a notice from.
    private final List<CompatibilityFailures> raisedFrom = new ArrayList<>();

    private final SwitchedOffLayerReporter reporter = new SwitchedOffLayerReporter(failureRecord, raisedFrom::add);

    @BeforeEach
    void standInForTheLayerAndItsWording() {

        when(switchedOffLayerMock.getId())
            .thenReturn("political_map");
        when(switchedOffLayerMock.resolveTabLabelText())
            .thenReturn("Political Map");

        StarsectorSettingsFake.installSettings((category, key) -> "the %s sentence for " + key);
    }

    @AfterEach
    void clearSettings() {

        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class ReportLayerSwitchedOff {

        @Test
        void recordsTheLayerAsOneOfKmusFeaturesNamedByItsTab() {

            reporter.reportLayerSwitchedOff(switchedOffLayerMock, LAYER_FAILURE);

            assertThat(failureRecord.takeNextUnreported())
                .isEqualTo(new FeatureFailure(
                    new CompatibilityConsumer(
                        "kmu",
                        "map-layer-political_map",
                        "the Political Map sentence for compatibility_lost_map_layer",
                        "the %s sentence for compatibility_unaffected_map_layer"),
                    LAYER_FAILURE));
        }

        @Test
        void raisesTheNoticeOnScreenFromTheRecordItFiledInto() {
            // On the map at once, rather than only once the player has left it for the campaign.
            reporter.reportLayerSwitchedOff(switchedOffLayerMock, LAYER_FAILURE);

            assertThat(raisedFrom)
                .containsExactly(failureRecord);
        }

        @Test
        void neverThrowsWhereTheReportCannotBeComposed() {
            // The render pass that switched the layer off has nothing under it that catches.
            StarsectorSettingsFake.clearSettings();

            assertThatNoException()
                .isThrownBy(() -> reporter.reportLayerSwitchedOff(switchedOffLayerMock, LAYER_FAILURE));
        }

        @Test
        void logsAReportThatCannotBeComposed() {

            StarsectorSettingsFake.clearSettings();

            var capture = LogAppenderFake.captureLogOf(
                SwitchedOffLayerReporter.class,
                () -> reporter.reportLayerSwitchedOff(switchedOffLayerMock, LAYER_FAILURE));

            assertThat(capture.getMessages())
                .containsExactly("Map layer 'political_map' was switched off but could not be reported to the"
                    + " player. The failure it was for is logged above.");
        }
    }
}
