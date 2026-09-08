package kmu.maplayers.base.render;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.BudgetBreach;
import kmlib.profiling.SilentProfiler;
import kmlib.profiling.recording.RecordingProfiler;
import kmlib.profiling.snapshot.ProfileNode;
import kmlib.starsector.SectorWalkCounters;

import kmu.settings.KmuMapLayerSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins what the frame's section names have to be for a capture to read: one section per name, so
 * two opens of one beat land on one row rather than on two rows spelled alike, and one section per
 * thing named, so two beats or two layers are never quietly the same row - and what each of them
 * allows one call, a beat being held to the duration the settings state and the refresh to one
 * traversal of the sector.
 *
 * <p>Identity rather than equality throughout, that being what the profiler compares by: a section
 * is found among the children of the open scope by reference, so two instances carrying one name
 * would be two rows however alike they read.
 */
final class MapFrameSectionsTest {

    // Two knob settings and the two calls that sit either side of them, in the units each is
    // stated in - the tighter setting being what the case about a knob moved mid-session turns to.
    private static final double FOUR_MILLISECOND_BUDGET = 4.0;
    private static final double HALF_MILLISECOND_BUDGET = 0.5;
    private static final long ONE_MILLISECOND_IN_NANOS = 1_000_000L;
    private static final long TEN_MILLISECONDS_IN_NANOS = 10_000_000L;

    // A refresh that kept the framework's rule, and one that broke it.
    private static final int ONE_WALK = 1;
    private static final int TWO_WALKS = 2;

    // Every span a capture below takes is nothing, since what those cases are about is what a call
    // counted rather than how long it ran.
    private static final long FIXED_CLOCK_NANOS = 0L;

    private static final long SYSTEMS_IN_A_WALK = 40L;

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

    @Nested
    class GetBudget {

        @Test
        void holdsAFrameBeatToTheDurationTheSettingsState() {
            // What the framework promises the frame it draws in: a beat is one part of the layers'
            // share of it, so a beat over the whole share is the finding.
            var breach = findBreachInABeatAllowed(
                FOUR_MILLISECOND_BUDGET, TEN_MILLISECONDS_IN_NANOS);

            assertThat(breach.hasBreached())
                .isTrue();
            assertThat(breach.describeBreach())
                .isEqualTo("4.00ms allowed per call");
        }

        @Test
        void reportsNothingForAFrameBeatInsideTheDurationTheSettingsState() {

            assertThat(findBreachInABeatAllowed(FOUR_MILLISECOND_BUDGET, ONE_MILLISECOND_IN_NANOS))
                .isSameAs(BudgetBreach.NO_BREACH);
        }

        @Test
        void asksTheSettingsForTheAllowanceAsEachBeatEnds() {
            // The bound is a knob a player moves mid-session, so the same call is judged by
            // whatever the knob says when it closes rather than by what it said at load.
            var allowingTheCall = findBreachInABeatAllowed(
                FOUR_MILLISECOND_BUDGET, ONE_MILLISECOND_IN_NANOS);

            var refusingIt = findBreachInABeatAllowed(
                HALF_MILLISECOND_BUDGET, ONE_MILLISECOND_IN_NANOS);

            assertThat(allowingTheCall.hasBreached())
                .isFalse();
            assertThat(refusingIt.hasBreached())
                .isTrue();
        }

        @Test
        void holdsTheRefreshToOneTraversalOfTheSector() {
            // The rule the framework's indexes exist to keep, read off a capture rather than off
            // the declaration: a second walk in one refresh is a pass that went looking for the
            // sector instead of asking for what had already been gathered.
            var refresh = captureARefreshWalking(TWO_WALKS);

            assertThat(refresh.getBudgetBreach().describeBreach())
                .isEqualTo("2 walks, 1 allowed per call");
        }

        @Test
        void reportsNothingForARefreshThatWalkedTheSectorOnce() {

            assertThat(captureARefreshWalking(ONE_WALK).getBudgetBreach())
                .isSameAs(BudgetBreach.NO_BREACH);
        }
    }

    // What one beat's bound makes of a call of that length, with the knob behind it reading
    // millisecondsAllowed. Stated through the settings the bound actually reads, so a knob that
    // stopped reaching the budget would fail this rather than pass it.
    private static BudgetBreach findBreachInABeatAllowed(
            double millisecondsAllowed,
            long elapsedNanos) {

        try (var settingsMock = mockStatic(KmuMapLayerSettings.class)) {

            settingsMock
                .when(KmuMapLayerSettings::getMapFrameBeatBudgetMillis)
                .thenReturn(millisecondsAllowed);

            return MapFrameSections.PREPARE
                .getBudget()
                .findBreachInCall(elapsedNanos, counter -> 0L);
        }
    }

    // One refresh that walked the sector as often as asked, captured the way a frame would produce
    // it: the counters arrive from the shared reads through the holder, not from the case.
    private static ProfileNode captureARefreshWalking(int walks) {

        return captureRefreshWhile(() -> {
            for (var walk = 0; walk < walks; walk++) {
                SectorWalkCounters.countSectorWalk(SYSTEMS_IN_A_WALK);
            }
        });
    }

    // Runs the walking inside one open refresh against a recording profiler, leaving the
    // process-wide holder silent however it ended.
    private static ProfileNode captureRefreshWhile(Runnable walking) {

        var profiler = new RecordingProfiler(() -> FIXED_CLOCK_NANOS);

        ActiveProfiler.bindProfiler(profiler);
        try (var refresh = profiler.open(MapFrameSections.REFRESH)) {
            walking.run();
        } finally {
            ActiveProfiler.bindProfiler(SilentProfiler.INSTANCE);
        }
        return profiler.snapshot().get(0).getRoots().get(0);
    }
}
