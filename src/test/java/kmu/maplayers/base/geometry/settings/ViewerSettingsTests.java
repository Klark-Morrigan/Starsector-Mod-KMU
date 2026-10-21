package kmu.maplayers.base.geometry.settings;

import kmu.maplayers.base.geometry.v3.StraightRuns;
import kmu.maplayers.base.geometry.v3.VoidSection;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins what {@link ViewerSettings} answers from its fields: which layers each predicate reads as
 * on screen, and the rule records the constructions are handed.
 */
final class ViewerSettingsTests {

    private static final double ANGLE_TOLERANCE = 1e-12;

    private final ViewerSettings settings = new ViewerSettings();

    @Nested
    class IsAnyContinentLayerShown {

        @Test
        void isFalseWithEveryLayerOff() {

            assertThat(settings.isAnyContinentLayerShown())
                .isFalse();
        }

        @Test
        void isTrueWithOneLayerOn() {

            settings.showIntercontinentalEnclosedFill = true;

            assertThat(settings.isAnyContinentLayerShown())
                .isTrue();
        }

        @Test
        void isFalseWhileTheConstructionIsSetDown() {

            settings.showContinentCoastline = true;
            settings.showContinentVoid = false;

            assertThat(settings.isAnyContinentLayerShown())
                .isFalse();
        }

        // The names are written on what the layers draw, and draw nothing of their own.
        @Test
        void isFalseWithOnlyANameSwitchOn() {

            settings.showContinentLakeNames = true;

            assertThat(settings.isAnyContinentLayerShown())
                .isFalse();
        }
    }

    @Nested
    class IsSectionOnScreen {

        @Test
        void needsBothTheWallAndTheFill() {

            settings.showContinentLakeCoastline = true;

            assertThat(settings.isSectionOnScreen(VoidSection.SectionKind.LAKE))
                .isFalse();

            settings.showContinentLakeFill = true;

            assertThat(settings.isSectionOnScreen(VoidSection.SectionKind.LAKE))
                .isTrue();
        }

        // A puddle's edge is the cells', which are always drawn.
        @Test
        void takesAPuddlesFillAloneAsOnScreen() {

            settings.showContinentPuddleFill = true;

            assertThat(settings.isSectionOnScreen(VoidSection.SectionKind.PUDDLE))
                .isTrue();
        }

        @Test
        void isFalseWhileTheConstructionIsSetDown() {

            settings.showContinentBridges = true;
            settings.showContinentInletFill = true;
            settings.showContinentVoid = false;

            assertThat(settings.isSectionOnScreen(VoidSection.SectionKind.INLET))
                .isFalse();
        }
    }

    @Nested
    class ShouldWriteSectionNames {

        @Test
        void readsTheKindsOwnNameSwitch() {

            settings.showIntercontinentalNames = true;

            assertThat(settings.shouldWriteSectionNames(VoidSection.SectionKind.INTERCONTINENTAL))
                .isTrue();
            assertThat(settings.shouldWriteSectionNames(VoidSection.SectionKind.COASTAL))
                .isFalse();
        }

        @Test
        void isFalseWhileTheConstructionIsSetDown() {

            settings.showContinentCoastNames = true;
            settings.showContinentVoid = false;

            assertThat(settings.shouldWriteSectionNames(VoidSection.SectionKind.COASTAL))
                .isFalse();
        }
    }

    @Nested
    class IsVoidPiecesV4Shown {

        @Test
        void isFalseUntilTheConstructionIsSwitchedOn() {

            assertThat(settings.isVoidPiecesV4Shown())
                .isFalse();

            settings.showVoidV4 = true;

            assertThat(settings.isVoidPiecesV4Shown())
                .isTrue();
        }
    }

    @Nested
    class IsLakeCoastV4Shown {

        @Test
        void needsTheMasterAndTheLeaf() {

            settings.showLakeCoastV4 = true;

            assertThat(settings.isLakeCoastV4Shown())
                .isFalse();

            settings.showVoidV4 = true;

            assertThat(settings.isLakeCoastV4Shown())
                .isTrue();
        }
    }

    @Nested
    class ShouldWalkVoidV4 {

        // A tier's lines are drawn from the tier rather than read off the walk.
        @Test
        void isFalseWithOnlyTheLinesOn() {

            settings.showVoidV4 = true;
            settings.showVoidPiecesV4 = false;
            settings.showLakeCoastV4 = true;
            settings.showLakeBridgesV4 = true;

            assertThat(settings.shouldWalkVoidV4())
                .isFalse();
        }

        @Test
        void isTrueWithAFillOn() {

            settings.showVoidV4 = true;
            settings.showVoidPiecesV4 = false;
            settings.showLakeMarginV4 = true;

            assertThat(settings.shouldWalkVoidV4())
                .isTrue();
        }
    }

    @Nested
    class ResolveReachAnchor {

        @Test
        void landsAtTheStretchStartByDefault() {

            assertThat(settings.resolveReachAnchor())
                .isEqualTo(StraightRuns.ReachAnchor.AT_THE_STRETCH_START);
        }

        @Test
        void landsNearestTheMiddleWhereOnlyVisibleLandingIsAsked() {

            settings.shouldLandWhereVisible = true;

            assertThat(settings.resolveReachAnchor())
                .isEqualTo(StraightRuns.ReachAnchor.NEAREST_THE_STRETCH_MIDDLE);
        }
    }

    @Nested
    class ResolveLineRounding {

        @Test
        void carriesTheKnobsAndNeverChamfers() {

            settings.roundingRadius = 250;
            settings.roundingSegments = 5;
            settings.roundBelowDegrees = 90;

            var rounding = settings.resolveLineRounding();

            assertThat(rounding.radius())
                .isEqualTo(250);
            assertThat(rounding.segmentsPerCorner())
                .isEqualTo(5);
            assertThat(rounding.bevelBelowAngleRadians())
                .isZero();
            assertThat(rounding.roundBelowAngleRadians())
                .isCloseTo(Math.PI / 2, within(ANGLE_TOLERANCE));
        }
    }

    @Nested
    class ResolveBorderSmoothing {

        // Both gates held on: the knobs alone switch each pass off, at zero.
        @Test
        void holdsBothPassesOnWithTheKnobsItCarries() {

            settings.spikeHeight = 0;
            settings.spikeBelowDegrees = 45;
            settings.roundingRadius = 0;

            var smoothing = settings.resolveBorderSmoothing();

            assertThat(smoothing.spikeSanding().shouldSandSpikes())
                .isTrue();
            assertThat(smoothing.spikeSanding().spikeHeight())
                .isZero();
            assertThat(smoothing.spikeSanding().spikeAngleRadians())
                .isCloseTo(Math.PI / 4, within(ANGLE_TOLERANCE));
            assertThat(smoothing.cornerRounding().shouldRoundCorners())
                .isTrue();
            assertThat(smoothing.cornerRounding().cornerRadius())
                .isZero();
        }
    }

    @Nested
    class ResolveContinentBridgeRules {

        @Test
        void carriesTheBridgeKnobs() {

            settings.continentBridgeReachMultiple = 3.5;
            settings.continentBridgeCoastSlack = 60;
            settings.shouldThinSpanFormations = false;
            settings.spanAnchorSeparation = 400;

            var rules = settings.resolveContinentBridgeRules();

            assertThat(rules.reachMultiple())
                .isEqualTo(3.5);
            assertThat(rules.coastSlack())
                .isEqualTo(60);
            assertThat(rules.shouldThinFormations())
                .isFalse();
            assertThat(rules.anchorSeparation())
                .isEqualTo(400);
        }
    }

    @Nested
    class ResolveContinentCoastRules {

        // The bare search's reach, not the coast-laid spans' own.
        @Test
        void carriesTheCoastKnobsAndTheBareSearchsReach() {

            settings.bridgeReachMultiple = 2.5;
            settings.continentBridgeReachMultiple = 7;
            settings.continentMinFrontageShare = 0.05;
            settings.minLakeShare = 0.01;
            settings.shouldLandWhereVisible = true;

            var rules = settings.resolveContinentCoastRules();

            assertThat(rules.bridgeReachMultiple())
                .isEqualTo(2.5);
            assertThat(rules.minFrontageShare())
                .isEqualTo(0.05);
            assertThat(rules.minLakeShare())
                .isEqualTo(0.01);
            assertThat(rules.reachAnchor())
                .isEqualTo(StraightRuns.ReachAnchor.NEAREST_THE_STRETCH_MIDDLE);
        }
    }

    @Nested
    class ResolveWaterLook {

        @Test
        void pairsTheColoursWithTheVoidFillOpacity() {

            settings.voidFillOpacity = 140;

            var look = settings.resolveWaterLook(Color.RED, Color.BLUE);

            assertThat(look.fill())
                .isEqualTo(Color.RED);
            assertThat(look.alpha())
                .isEqualTo(140);
            assertThat(look.edge())
                .isEqualTo(Color.BLUE);
        }
    }

    @Nested
    class ResolveOwnedColour {

        @Test
        void answersTheChosenColourWithJitterOff() {

            settings.jitterOwned = false;
            settings.ownedCellColour = Color.ORANGE;

            assertThat(settings.resolveOwnedColour("hegemony"))
                .isEqualTo(Color.ORANGE);
        }
    }
}
