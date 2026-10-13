package kmu.maplayers.base.labels.anchor;

import kmlib.starsector.ui.label.BandFitSpecification;
import kmlib.starsector.ui.label.NameFitSpecification;
import kmlib.testfixtures.logging.LogAppenderFake;

import kmu.maplayers.base.labels.anchor.specifications.AnchorDiagnostics;
import kmu.maplayers.base.labels.anchor.specifications.AnchorSearch;
import kmu.maplayers.base.labels.anchor.specifications.LabelAnchorSpecification;
import kmu.maplayers.base.labels.anchor.specifications.LeanScoring;
import kmu.maplayers.base.render.clusters.ClusterBorderTrace;

import org.apache.log4j.Level;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins when the font precision is announced: once when the tolerance moves, with the halving count
 * it resolves to, never again while it sits still, and not while debug logging is off - without
 * that quiet call counting as said, so the move is still announced once debug is on.
 *
 * <p>What was last announced is held for the process, so each case starts from a tolerance no other
 * case uses: a value shared with a case that ran first would read as unmoved.
 */
final class FontToleranceAnnouncementTests {

    // The font clamp the halving count is taken over. A range no tolerance below divides into a
    // power of two, so every count is a plain ceiling rather than one rounding could tip.
    private static final double MIN_FONT_HEIGHT = 0.0;
    private static final double MAX_FONT_HEIGHT = 1000.0;

    @Nested
    class AnnounceIfMoved {

        @Test
        void statesTheToleranceAndItsHalvingCountWhenItMoves() {

            var logFake = LogAppenderFake.captureLogOf(
                FontToleranceAnnouncement.class,
                () -> FontToleranceAnnouncement.announceIfMoved(buildSpecificationAt(0.37)));

            assertThat(logFake.getMessages())
                .containsExactly("Anchor font search precision; tolerance=0.37 bisections=12");
        }

        @Test
        void staysQuietWhileTheToleranceSitsStill() {

            // The knob is a static setting read on every fit, so restating it each time would only
            // pad the log.
            var logFake = LogAppenderFake.captureLogOf(
                FontToleranceAnnouncement.class,
                () -> {

                    FontToleranceAnnouncement.announceIfMoved(buildSpecificationAt(0.41));
                    FontToleranceAnnouncement.announceIfMoved(buildSpecificationAt(0.41));
                });

            assertThat(logFake.getMessages())
                .containsExactly("Anchor font search precision; tolerance=0.41 bisections=12");
        }

        @Test
        void restatesTheToleranceEachTimeItMovesAgain() {

            var logFake = LogAppenderFake.captureLogOf(
                FontToleranceAnnouncement.class,
                () -> {

                    FontToleranceAnnouncement.announceIfMoved(buildSpecificationAt(0.53));
                    FontToleranceAnnouncement.announceIfMoved(buildSpecificationAt(0.73));
                });

            assertThat(logFake.getMessages())
                .containsExactly(
                    "Anchor font search precision; tolerance=0.53 bisections=11",
                    "Anchor font search precision; tolerance=0.73 bisections=11");
        }

        @Test
        void staysQuietWhileDebugIsOff() {

            var logFake = LogAppenderFake.captureLogOf(
                FontToleranceAnnouncement.class,
                Level.INFO,
                () -> FontToleranceAnnouncement.announceIfMoved(buildSpecificationAt(0.59)));

            assertThat(logFake.getMessages())
                .isEmpty();
        }

        @Test
        void announcesAMoveMadeWhileDebugWasOffOnceDebugIsOn() {

            // A call that said nothing records nothing, so turning debug on mid-session still shows
            // the precision the fits are running at.
            LogAppenderFake.captureLogOf(
                FontToleranceAnnouncement.class,
                Level.INFO,
                () -> FontToleranceAnnouncement.announceIfMoved(buildSpecificationAt(0.61)));

            var logFake = LogAppenderFake.captureLogOf(
                FontToleranceAnnouncement.class,
                () -> FontToleranceAnnouncement.announceIfMoved(buildSpecificationAt(0.61)));

            assertThat(logFake.getMessages())
                .containsExactly("Anchor font search precision; tolerance=0.61 bisections=11");
        }
    }

    // A tuning differing from the next only in its font tolerance, the one input the announcement
    // reads besides the clamp.
    private static LabelAnchorSpecification buildSpecificationAt(double fontHeightTolerance) {

        return new LabelAnchorSpecification(
            new AnchorSearch(new ClusterBorderTrace(1.0, 4.0), 3, 1),
            new LeanScoring(0.0, 2.0, 0.0),
            new AnchorDiagnostics(false, false),
            new BandFitSpecification(0.0, 0.0, fontHeightTolerance),
            new NameFitSpecification(MIN_FONT_HEIGHT, MAX_FONT_HEIGHT, 1, 1.0));
    }
}
