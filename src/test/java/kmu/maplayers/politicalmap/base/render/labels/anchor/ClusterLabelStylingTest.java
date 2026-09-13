package kmu.maplayers.politicalmap.base.render.labels.anchor;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.base.theme.ElementPaintSelection;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.render.style.BlocStyleDecision;
import kmu.maplayers.politicalmap.base.render.style.FactionPaletteSlot;
import kmu.maplayers.politicalmap.base.render.style.MapPalettes;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what shade a cluster name draws in - the half of a label the political map decides
 * before the placement search ever sees it. The rule is that a name inherits the outer border
 * of the group its bloc was classified into, resolved against the same palette the fill and
 * border read, so a name can never drift from the space it labels; these tests hold that
 * against each branch of the classification, each colour choice, and each recede. The name
 * half needs a view and a font, so it only resolves in-engine and is not covered here.
 */
final class ClusterLabelStylingTest {

    // Two distinct shades so a name that followed the wrong palette slot is visible.
    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    // The bloc whose shades a resolved name should carry.
    private static final DominantHolder FACTION_F =
        new DominantHolder("F", PRIMARY, SECONDARY);

    // A garish pair no test with an identity adjustment ever reads, so a name that
    // desaturated when it should not stands out.
    private static final FactionPalette UNUSED_PALETTE =
        new FactionPalette(Color.MAGENTA, Color.MAGENTA);

    // Full opacity leaves scaleAlpha an identity, so the colour tests read the resolved shade
    // unfaded; the fade tests dim one group at a time.
    private static final double FULL_OPACITY = 1.0;
    private static final double HALF_OPACITY = 0.5;

    // The two classifications under an identity adjustment, so a colour test names only the
    // branch it exercises.
    private static final BlocStyleDecision FACTION_STYLED = buildFactionStyled(ElementStyleAdjustment.NONE);
    private static final BlocStyleDecision INDEPENDENT_STYLED =
        new BlocStyleDecision(true, ElementStyleAdjustment.NONE);

    // A faction-styled bloc under a given recede, for the tests that vary the adjustment
    // rather than the classification.
    private static BlocStyleDecision buildFactionStyled(ElementStyleAdjustment adjustment) {
        return new BlocStyleDecision(false, adjustment);
    }

    // Both groups pointed at the same slot and opacity, so a test that varies neither reads
    // one shade whichever branch the classification took.
    private static BlocNameStyles nameStyles(ElementPaintSelection factionOuterSelection) {
        return nameStyles(
            factionOuterSelection,
            FactionPaletteSlot.PRIMARY,
            FULL_OPACITY,
            FULL_OPACITY);
    }

    // The two groups' styling spelled out in full, so a test can point the faction and
    // independent branches at different slots or opacities and prove which one was read.
    // Takes the selections a style actually holds rather than the settings-side choices they
    // are stored as, so these tests pin the label rule and not the crossing between the two -
    // which FactionPaletteSlotTest owns. A null selection is a hidden element.
    private static BlocNameStyles nameStyles(
            ElementPaintSelection factionOuterSelection,
            ElementPaintSelection independentOuterSelection,
            double factionNameOpacity,
            double independentNameOpacity) {
        return new BlocNameStyles(
            new ElementStyle(factionOuterSelection, factionNameOpacity),
            new ElementStyle(independentOuterSelection, independentNameOpacity));
    }

    @Nested
    class ResolveLabelColour {

        @Test
        void resolveLabelColourTakesThePrimaryShadeWhenTheOuterBorderIsPrimary() {
            // The default outer-border choice is the bright primary shade, so the name
            // (and its debug dot) inherits it - RED here.
            var colour = ClusterLabelStyling.resolveLabelColour(
                FACTION_F,
                nameStyles(FactionPaletteSlot.PRIMARY),
                FACTION_STYLED,
                UNUSED_PALETTE);

            assertThat(colour).isEqualTo(PRIMARY);
        }

        @Test
        void resolveLabelColourInheritsTheSecondaryShadeWhenTheOuterBorderIsSecondary() {
            // Point the outer border at the secondary (dark) shade and the name follows
            // it - BLUE - so the label reads as the border's own colour, not a fixed pick.
            var colour = ClusterLabelStyling.resolveLabelColour(
                FACTION_F,
                nameStyles(FactionPaletteSlot.SECONDARY),
                FACTION_STYLED,
                UNUSED_PALETTE);

            assertThat(colour).isEqualTo(SECONDARY);
        }

        @Test
        void resolveLabelColourFallsBackToThePrimaryShadeWhenTheOuterBorderIsHidden() {
            // A hidden outer border ("No color") resolves to no colour, but a name still
            // needs one, so it falls back to the bright primary shade rather than vanishing.
            var colour = ClusterLabelStyling.resolveLabelColour(
                FACTION_F,
                nameStyles(null),
                FACTION_STYLED,
                UNUSED_PALETTE);

            assertThat(colour).isEqualTo(PRIMARY);
        }

        @Test
        void resolveLabelColourFollowsTheIndependentOuterBorderWhenTheBlocIsIndependentStyled() {
            // The classification, not a hardcoded independent-faction test, decides which
            // outer-border choice the label follows. Classified independent-styled, the label
            // inherits the independent choice (SECONDARY -> BLUE) even though the faction
            // choice differs (PRIMARY) - the seam the alliances view drives, where lone
            // factions and neutrals take the independent style.
            var colour = ClusterLabelStyling.resolveLabelColour(
                FACTION_F,
                nameStyles(
                    FactionPaletteSlot.PRIMARY,
                    FactionPaletteSlot.SECONDARY,
                    FULL_OPACITY,
                    FULL_OPACITY),
                INDEPENDENT_STYLED,
                UNUSED_PALETTE);

            assertThat(colour).isEqualTo(SECONDARY);
        }

        @Test
        void resolveLabelColourFadesAFactionNameByTheFactionNameOpacity() {
            // A faction-styled bloc takes the faction group's name opacity: half fades the
            // resolved PRIMARY shade's alpha to half, leaving its RGB (and the dot's) intact.
            var colour = ClusterLabelStyling.resolveLabelColour(
                FACTION_F,
                nameStyles(
                    FactionPaletteSlot.PRIMARY,
                    FactionPaletteSlot.PRIMARY,
                    HALF_OPACITY,
                    FULL_OPACITY),
                FACTION_STYLED,
                UNUSED_PALETTE);

            assertThat(colour.getAlpha())
                .isEqualTo(Math.round(PRIMARY.getAlpha() * (float) HALF_OPACITY));
            assertThat(colour.getRed())
                .isEqualTo(PRIMARY.getRed());
        }

        @Test
        void resolveLabelColourLeavesAFactionNameUntouchedByTheIndependentNameOpacity() {
            // The independent group's opacity fades only independent names: a faction-styled
            // bloc is unaffected even when independent opacity is dimmed, so the two groups
            // fade independently.
            var colour = ClusterLabelStyling.resolveLabelColour(
                FACTION_F,
                nameStyles(
                    FactionPaletteSlot.PRIMARY,
                    FactionPaletteSlot.PRIMARY,
                    FULL_OPACITY,
                    HALF_OPACITY),
                FACTION_STYLED,
                UNUSED_PALETTE);

            assertThat(colour).isEqualTo(PRIMARY);
        }

        @Test
        void resolveLabelColourFadesAnIndependentStyledNameByTheIndependentNameOpacity() {
            // An independent-styled bloc takes the independent group's opacity: half fades
            // its resolved shade's alpha to half, while the faction opacity (full here) has
            // no say over it.
            var colour = ClusterLabelStyling.resolveLabelColour(
                FACTION_F,
                nameStyles(
                    FactionPaletteSlot.PRIMARY,
                    FactionPaletteSlot.PRIMARY,
                    FULL_OPACITY,
                    HALF_OPACITY),
                INDEPENDENT_STYLED,
                UNUSED_PALETTE);

            assertThat(colour.getAlpha())
                .isEqualTo(Math.round(PRIMARY.getAlpha() * (float) HALF_OPACITY));
            assertThat(colour.getRed())
                .isEqualTo(PRIMARY.getRed());
        }

        @Test
        void resolveLabelColourMutesTheAlphaForABlocWithAMutedAdjustment() {
            // A bloc's adjustment dims its label on top of the (full) name opacity: half
            // fades the resolved shade's alpha to half and leaves its RGB intact - the same
            // fold applied wherever the style classification is read.
            var colour = ClusterLabelStyling.resolveLabelColour(
                FACTION_F,
                nameStyles(FactionPaletteSlot.PRIMARY),
                buildFactionStyled(new ElementStyleAdjustment(HALF_OPACITY, false)),
                UNUSED_PALETTE);

            assertThat(colour.getAlpha())
                .isEqualTo(Math.round(PRIMARY.getAlpha() * (float) HALF_OPACITY));
            assertThat(colour.getRed())
                .isEqualTo(PRIMARY.getRed());
        }

        @Test
        void resolveLabelColourDesaturatesToThePassPaletteForADesaturatedBloc() {
            // A desaturated bloc's label follows the pass's shared desaturation palette
            // instead of the holder's own shades, at full alpha since mute is off here.
            var colour = ClusterLabelStyling.resolveLabelColour(
                FACTION_F,
                nameStyles(FactionPaletteSlot.PRIMARY),
                buildFactionStyled(new ElementStyleAdjustment(FULL_OPACITY, true)),
                new FactionPalette(Color.GREEN, Color.YELLOW));

            assertThat(colour).isEqualTo(Color.GREEN);
        }

        @Test
        void resolveLabelColourLeavesAnUnadjustedBlocUntouchedRegardlessOfThePalette() {
            // A bloc the resolver maps to NONE - an alliance, in the real view - draws
            // unmuted and undesaturated no matter what the pass's palette holds, since the
            // adjustment (not the palette alone) gates whether either applies.
            var colour = ClusterLabelStyling.resolveLabelColour(
                FACTION_F,
                nameStyles(FactionPaletteSlot.PRIMARY),
                FACTION_STYLED,
                new FactionPalette(Color.GREEN, Color.YELLOW));

            assertThat(colour).isEqualTo(PRIMARY);
        }

        @Test
        void resolveLabelColourGivesAFilterRecededNameTheSameRecededPaletteItsFillTakes() {
            // Under a filter a non-spotlit bloc recedes: its label must follow the pass's shared
            // desaturation palette - the exact palette TerritoryBuilder recolours its fill to for
            // the same recede - so the receded name never drifts from the receded fill. The recede
            // both mutes and desaturates, as a real filter recede can; the colour comparison reads
            // RGB, since the label additionally fades its alpha by the name opacity the fill omits.
            var recede = new ElementStyleAdjustment(HALF_OPACITY, true);
            var desaturationPalette = new FactionPalette(Color.GREEN, Color.YELLOW);

            var labelColour = ClusterLabelStyling.resolveLabelColour(
                FACTION_F,
                nameStyles(FactionPaletteSlot.PRIMARY),
                buildFactionStyled(recede),
                desaturationPalette);

            // The shade MapPalettes resolves the same bloc's fill to under the same recede.
            var fillShade = MapPalettes
                .resolveEffectivePalette(recede, FACTION_F, desaturationPalette).primaryColour();

            assertThat(labelColour.getRed())
                .isEqualTo(fillShade.getRed());
            assertThat(labelColour.getGreen())
                .isEqualTo(fillShade.getGreen());
            assertThat(labelColour.getBlue())
                .isEqualTo(fillShade.getBlue());

            // And genuinely receded, not the bloc's own bright shade.
            assertThat(labelColour.getGreen())
                .isNotEqualTo(PRIMARY.getGreen());
        }
    }

    @Nested
    class NewLabelColourResolver {

        // A second bloc, so a lookup that answered the wrong bloc's shade is visible.
        private static final Color OTHER_PRIMARY = Color.CYAN;

        private static final DominantHolder FACTION_G =
            new DominantHolder("G", OTHER_PRIMARY, Color.DARK_GRAY);

        @Test
        void newLabelColourResolverAnswersTheShadeOfTheBlocItIsAskedFor() {
            // Every system of a bloc carries that bloc's own two shades, so the resolver must
            // key off the bloc id the search hands it rather than any one system.
            var resolver = ClusterLabelStyling.newLabelColourResolver(
                Map.of(buildCellKey("alpha"), FACTION_F, buildCellKey("beta"), FACTION_G),
                nameStyles(FactionPaletteSlot.PRIMARY),
                blocId -> new BlocStyleDecision(false, ElementStyleAdjustment.NONE),
                UNUSED_PALETTE);

            assertThat(resolver.apply("F")).isEqualTo(PRIMARY);
            assertThat(resolver.apply("G")).isEqualTo(OTHER_PRIMARY);
        }

        @Test
        void newLabelColourResolverAppliesEachBlocsOwnStyleDecision() {
            // The decision carries both halves the colour needs - which group's outer border
            // to follow and how the bloc recedes - so a bloc classified independent takes the
            // independent choice while its neighbour keeps the faction one.
            var resolver = ClusterLabelStyling.newLabelColourResolver(
                Map.of(buildCellKey("alpha"), FACTION_F, buildCellKey("beta"), FACTION_G),
                nameStyles(
                    FactionPaletteSlot.PRIMARY,
                    FactionPaletteSlot.SECONDARY,
                    FULL_OPACITY,
                    FULL_OPACITY),
                blocId -> new BlocStyleDecision("F".equals(blocId), ElementStyleAdjustment.NONE),
                UNUSED_PALETTE);

            assertThat(resolver.apply("F")).isEqualTo(SECONDARY);
            assertThat(resolver.apply("G")).isEqualTo(OTHER_PRIMARY);
        }

        @Test
        void newLabelColourResolverDecidesOncePerBlocAcrossItsClusters() {
            // Every cluster of a bloc draws its name the same, and a bloc can hold many, so
            // the decision is made once and reused rather than re-resolved per cluster.
            var askedBlocIds = new ArrayList<String>();
            var resolver = ClusterLabelStyling.newLabelColourResolver(
                Map.of(buildCellKey("alpha"), FACTION_F),
                nameStyles(FactionPaletteSlot.PRIMARY),
                blocId -> {
                    askedBlocIds.add(blocId);
                    return new BlocStyleDecision(false, ElementStyleAdjustment.NONE);
                },
                UNUSED_PALETTE);

            resolver.apply("F");
            resolver.apply("F");

            assertThat(askedBlocIds).containsExactly("F");
        }
    }
}
