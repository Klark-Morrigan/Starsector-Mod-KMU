package kmu.maplayers.ownermap.render.labels;

import kmu.maplayers.base.theme.ElementPaintSelection;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.style.FactionPaletteSlot;
import kmu.maplayers.ownermap.render.style.MapPalettes;
import kmu.maplayers.ownermap.render.style.OwnerMapCategory;
import kmu.maplayers.ownermap.render.style.OwnerStyleDecision;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what shade a cluster name draws in - the half of a label the owner map decides
 * before the placement search ever sees it. The rule is that a name takes the name style of
 * the category its owner was placed in, resolved against the same palette the fill and border
 * read, so a name can never drift from the space it labels; these tests hold that against each
 * category, each colour choice, and each recede. The name half needs the label font, so it only
 * resolves in-engine and is not covered here.
 */
final class ClusterLabelStylingTests {

    // Two distinct shades so a name that followed the wrong palette slot is visible.
    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    // The bloc whose shades a resolved name should carry.
    private static final SystemOwner FACTION_F =
        new SystemOwner("F", new OwnerPalette(PRIMARY, SECONDARY));

    // A garish pair no test with an identity adjustment ever reads, so a name that
    // desaturated when it should not stands out.
    private static final OwnerPalette UNUSED_PALETTE =
        new OwnerPalette(Color.MAGENTA, Color.MAGENTA);

    // Full opacity leaves scaleAlpha an identity, so the colour tests read the resolved shade
    // unfaded; the fade tests dim one group at a time.
    private static final double FULL_OPACITY = 1.0;
    private static final double HALF_OPACITY = 0.5;

    // The two classifications under an identity adjustment, so a colour test names only the
    // branch it exercises.
    private static final OwnerStyleDecision FACTION_STYLED = buildFactionStyled(ElementStyleAdjustment.NONE);
    private static final OwnerStyleDecision INDEPENDENT_STYLED =
        new OwnerStyleDecision(OwnerMapCategory.INDEPENDENT, ElementStyleAdjustment.NONE);

    // A faction-styled bloc under a given recede, for the tests that vary the adjustment
    // rather than the classification.
    private static OwnerStyleDecision buildFactionStyled(ElementStyleAdjustment adjustment) {
        return new OwnerStyleDecision(OwnerMapCategory.FACTION, adjustment);
    }

    // Both categories pointed at the same slot and opacity, so a test that varies neither reads
    // one shade whichever category the owner was placed in.
    private static BlocNameStyles nameStyles(ElementPaintSelection factionOuterSelection) {
        return nameStyles(
            factionOuterSelection,
            FactionPaletteSlot.PRIMARY,
            FULL_OPACITY,
            FULL_OPACITY);
    }

    // The two categories' name styles spelled out in full, so a test can point the faction and
    // independent categories at different slots or opacities and prove which one was read.
    // Takes the selections a style actually holds rather than the settings-side choices they
    // are stored as, so these tests pin the label rule and not the crossing between the two -
    // which FactionPaletteSlotTests owns. A null selection is a hidden element.
    private static BlocNameStyles nameStyles(
            ElementPaintSelection factionOuterSelection,
            ElementPaintSelection independentOuterSelection,
            double factionNameOpacity,
            double independentNameOpacity) {
        return new BlocNameStyles(Map.of(
            OwnerMapCategory.FACTION,
            new ElementStyle(factionOuterSelection, factionNameOpacity),
            OwnerMapCategory.INDEPENDENT,
            new ElementStyle(independentOuterSelection, independentNameOpacity)));
    }

    @Nested
    class ResolveLabelColour {

        @Test
        void takesThePrimaryShadeWhenTheOuterBorderIsPrimary() {
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
        void inheritsTheSecondaryShadeWhenTheOuterBorderIsSecondary() {
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
        void fallsBackToThePrimaryShadeWhenTheOuterBorderIsHidden() {
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
        void followsTheNameStyleOfTheCategoryTheOwnerDrawsIn() {
            // The category, not a hardcoded independent-faction test, decides which name style the
            // label follows. Placed in the independent category, the label inherits its choice
            // (SECONDARY -> BLUE) even though the faction choice differs (PRIMARY) - the seam a
            // layer drives when it places some owners in a quieter category.
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
        void fadesAFactionNameByTheFactionNameOpacity() {
            // A faction-category owner takes the faction category's name opacity: half fades the
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
        void leavesAFactionNameUntouchedByTheIndependentNameOpacity() {
            // The independent category's opacity fades only independent names: a faction-category
            // owner is unaffected even when independent opacity is dimmed, so the two categories
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
        void fadesAnIndependentNameByTheIndependentNameOpacity() {
            // An independent-category owner takes the independent category's opacity: half fades
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
        void mutesTheAlphaForABlocWithAMutedAdjustment() {
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
        void desaturatesToThePassPaletteForADesaturatedBloc() {
            // A desaturated bloc's label follows the pass's shared desaturation palette
            // instead of the holder's own shades, at full alpha since mute is off here.
            var colour = ClusterLabelStyling.resolveLabelColour(
                FACTION_F,
                nameStyles(FactionPaletteSlot.PRIMARY),
                buildFactionStyled(new ElementStyleAdjustment(FULL_OPACITY, true)),
                new OwnerPalette(Color.GREEN, Color.YELLOW));

            assertThat(colour).isEqualTo(Color.GREEN);
        }

        @Test
        void leavesAnUnadjustedBlocUntouchedRegardlessOfThePalette() {
            // A bloc the resolver maps to NONE draws unmuted and undesaturated no matter what the
            // pass's palette holds, since the adjustment (not the palette alone) gates whether
            // either applies.
            var colour = ClusterLabelStyling.resolveLabelColour(
                FACTION_F,
                nameStyles(FactionPaletteSlot.PRIMARY),
                FACTION_STYLED,
                new OwnerPalette(Color.GREEN, Color.YELLOW));

            assertThat(colour).isEqualTo(PRIMARY);
        }

        @Test
        void givesAFilterRecededNameTheSameRecededPaletteItsFillTakes() {
            // Under a filter a non-spotlit bloc recedes: its label must follow the pass's shared
            // desaturation palette - the exact palette OwnerMapBuilder recolours its fill to for
            // the same recede - so the receded name never drifts from the receded fill. The recede
            // both mutes and desaturates, as a real filter recede can; the colour comparison reads
            // RGB, since the label additionally fades its alpha by the name opacity the fill omits.
            var recede = new ElementStyleAdjustment(HALF_OPACITY, true);
            var desaturationPalette = new OwnerPalette(Color.GREEN, Color.YELLOW);

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

        private static final SystemOwner FACTION_G =
            new SystemOwner("G", new OwnerPalette(OTHER_PRIMARY, Color.DARK_GRAY));

        @Test
        void answersTheShadeOfTheBlocItIsAskedFor() {
            // Every system of a bloc carries that bloc's own two shades, so the resolver must
            // key off the bloc ID the search hands it rather than any one system.
            var resolver = ClusterLabelStyling.newLabelColourResolver(
                Map.of(buildCellKey("alpha"), FACTION_F, buildCellKey("beta"), FACTION_G),
                nameStyles(FactionPaletteSlot.PRIMARY),
                blocId -> new OwnerStyleDecision(OwnerMapCategory.FACTION, ElementStyleAdjustment.NONE),
                UNUSED_PALETTE);

            assertThat(resolver.apply("F")).isEqualTo(PRIMARY);
            assertThat(resolver.apply("G")).isEqualTo(OTHER_PRIMARY);
        }

        @Test
        void appliesEachBlocsOwnStyleDecision() {
            // The decision carries both halves the colour needs - which category's name style to
            // follow and how the owner recedes - so an owner placed in the independent category
            // takes its choice while its neighbour keeps the faction one.
            var resolver = ClusterLabelStyling.newLabelColourResolver(
                Map.of(buildCellKey("alpha"), FACTION_F, buildCellKey("beta"), FACTION_G),
                nameStyles(
                    FactionPaletteSlot.PRIMARY,
                    FactionPaletteSlot.SECONDARY,
                    FULL_OPACITY,
                    FULL_OPACITY),
                blocId -> new OwnerStyleDecision(
                    "F".equals(blocId) ? OwnerMapCategory.INDEPENDENT : OwnerMapCategory.FACTION,
                    ElementStyleAdjustment.NONE),
                UNUSED_PALETTE);

            assertThat(resolver.apply("F")).isEqualTo(SECONDARY);
            assertThat(resolver.apply("G")).isEqualTo(OTHER_PRIMARY);
        }

        @Test
        void decidesOncePerBlocAcrossItsClusters() {
            // Every cluster of a bloc draws its name the same, and a bloc can hold many, so
            // the decision is made once and reused rather than re-resolved per cluster.
            var askedBlocIds = new ArrayList<String>();
            var resolver = ClusterLabelStyling.newLabelColourResolver(
                Map.of(buildCellKey("alpha"), FACTION_F),
                nameStyles(FactionPaletteSlot.PRIMARY),
                blocId -> {
                    askedBlocIds.add(blocId);
                    return new OwnerStyleDecision(OwnerMapCategory.FACTION, ElementStyleAdjustment.NONE);
                },
                UNUSED_PALETTE);

            resolver.apply("F");
            resolver.apply("F");

            assertThat(askedBlocIds).containsExactly("F");
        }
    }
}
