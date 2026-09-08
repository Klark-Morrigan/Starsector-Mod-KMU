package kmu.maplayers.base.render;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileOrigin;
import kmlib.profiling.ProfileSection;
import kmlib.profiling.SilentProfiler;
import kmlib.profiling.recording.RecordingProfiler;
import kmlib.profiling.snapshot.ProfileNode;
import kmlib.profiling.snapshot.ProfileOriginTree;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two shapes a frame's rows are opened in: a beat hangs off its sector's origin with no
 * parent whatever else is open, and everything else hangs off whatever is.
 *
 * <p>The first is what keeps two beats of one frame from folding into each other - they are
 * separate calls from separate passes, so a beat opened as a child would report every later beat as
 * part of whichever ran first. The second is what puts a layer's work, and the sections that work
 * opens, under the beat it ran in.
 */
final class MapFrameBeatsTest {

    private static final ProfileOrigin SECTOR_ORIGIN =
        ProfileOrigin.registerOrigin("test.mapFrameBeatsSector");

    private static final ProfileSection LAYER_SECTION =
        ProfileSection.registerSection("test.mapFrameBeatsLayer");

    private static final ProfileSection BEAT = ProfileSection.registerSection("test.beat");

    private static final ProfileSection OTHER_BEAT =
        ProfileSection.registerSection("test.otherBeat");

    private static final ProfileSection STEP = ProfileSection.registerSection("test.step");

    private final RecordingProfiler profiler = new RecordingProfiler();

    private final MapFrameBeats frameBeats = new MapFrameBeats(SECTOR_ORIGIN, LAYER_SECTION);

    // The holder is process-wide, so a case that left a recording profiler bound would go on
    // recording every suite that ran after it.
    @BeforeEach
    void bindTheCapture() {
        ActiveProfiler.bindProfiler(profiler);
    }

    @AfterEach
    void unbindTheCapture() {
        ActiveProfiler.bindProfiler(SilentProfiler.INSTANCE);
    }

    @Nested
    class OpenBeat {

        @Test
        void openBeatAnswersARootOfTheSectorItWasComposedFor() {
            // The origin is what a capture taken across two games groups by, so a beat that named
            // none would leave its rows in the pile a reader cannot take back to a save.
            frameBeats.openBeat(BEAT).close();

            assertThat(readSectionsOfRootsOfThisSector())
                .containsExactly(BEAT);
        }

        @Test
        void openBeatAnswersARootEvenInsideAnOpenScope() {
            // A beat has no parent whatever else is open. Nothing brackets one in play, but the
            // property is what the frame sequence rests on, and only a scope left open around one
            // can show it holding.
            try (var surroundingScope = frameBeats.openStep(STEP)) {
                frameBeats.openBeat(BEAT).close();
            }

            assertThat(readSectionsOfRootsOfThisSector())
                .containsExactly(BEAT);
        }

        @Test
        void openBeatAnswersARootPerBeatInTheOrderTheyRan() {
            // Two beats of one frame are two rows, read in the order the frame spent them.
            frameBeats.openBeat(BEAT).close();
            frameBeats.openBeat(OTHER_BEAT).close();

            assertThat(readSectionsOfRootsOfThisSector())
                .containsExactly(BEAT, OTHER_BEAT);
        }
    }

    @Nested
    class OpenLayerRow {

        @Test
        void openLayerRowAnswersARowInsideTheBeatAlreadyOpen() {
            // The row the framework opens around a layer's callback, so what the layer cost is read
            // against the beat it cost it in.
            try (var beatScope = frameBeats.openBeat(BEAT)) {
                frameBeats.openLayerRow().close();
            }

            assertThat(readSectionsOfChildrenOfTheOnlyRoot())
                .containsExactly(LAYER_SECTION);
        }
    }

    @Nested
    class OpenStep {

        @Test
        void openStepAnswersARowInsideWhateverIsAlreadyOpen() {
            // A part of a beat worth its own row nests like any other section, which is what puts
            // the cache refresh inside the preparation rather than beside it.
            try (var beatScope = frameBeats.openBeat(BEAT)) {
                frameBeats.openStep(STEP).close();
            }

            assertThat(readSectionsOfChildrenOfTheOnlyRoot())
                .containsExactly(STEP);
        }
    }

    private List<ProfileSection> readSectionsOfRootsOfThisSector() {
        return readRootsOfThisSector().stream()
            .map(ProfileNode::getSection)
            .toList();
    }

    private List<ProfileSection> readSectionsOfChildrenOfTheOnlyRoot() {

        var roots = readRootsOfThisSector();

        assertThat(roots)
            .hasSize(1);

        return roots.get(0).getChildren().stream()
            .map(ProfileNode::getSection)
            .toList();
    }

    // Empty where nothing was opened under this sector, which is itself worth failing on: a beat
    // that named another origin would otherwise pass as a beat that opened nothing.
    private List<ProfileNode> readRootsOfThisSector() {
        return profiler.snapshot().stream()
            .filter(originTree -> originTree.getOrigin() == SECTOR_ORIGIN)
            .map(ProfileOriginTree::getRoots)
            .findFirst()
            .orElse(List.of());
    }
}
