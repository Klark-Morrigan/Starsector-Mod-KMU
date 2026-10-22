package kmu.maplayers.ownermap.render.labels;

import kmlib.colour.ColourPair;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.installed.LazyFontCache;

import kmu.maplayers.base.theme.ElementPaintSelection;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;
import kmu.maplayers.ownermap.render.style.MapPaints;
import kmu.maplayers.ownermap.render.style.MapPalettes;
import kmu.maplayers.ownermap.render.style.MapStyling;
import kmu.maplayers.ownermap.render.style.OwnerCategories;
import kmu.maplayers.ownermap.render.style.OwnerPaletteSlot;
import kmu.maplayers.ownermap.render.style.OwnerStyleDecision;
import kmu.maplayers.ownermap.render.style.StandInCategoryFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins what shade a cluster name draws in - the half of a label the owner map decides
 * before the placement search ever sees it. The rule is that a name takes the name style of
 * the category its owner was placed in, resolved against the same palette the fill and border
 * read, so a name can never drift from the space it labels; these tests hold that against each
 * category, each colour choice, and each recede. The name half needs the label font, so only the
 * face it is measured in is pinned here, with the font itself stubbed away.
 *
 * <p>The resolver pair is pinned last, as the wiring the fit drive below receives: each owner in its
 * own shade, a receded owner in the pass's desaturation palette, and the snapshot's own face.
 */
final class ClusterLabelStylingTests {

    // Two distinct shades so a name that followed the wrong palette slot is visible.
    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    // The owner whose shades a resolved name should carry.
    private static final SystemOwner OWNER_F =
        new SystemOwner("F", new ColourPair(PRIMARY, SECONDARY));

    // A garish pair no test with an identity adjustment ever reads, so a name that
    // desaturated when it should not stands out.
    private static final ColourPair UNUSED_PALETTE =
        new ColourPair(Color.MAGENTA, Color.MAGENTA);

    // Full opacity leaves scaleAlpha an identity, so the colour tests read the resolved shade
    // unfaded; the fade tests dim one group at a time.
    private static final double FULL_OPACITY = 1.0;
    private static final double HALF_OPACITY = 0.5;

    // The two classifications under an identity adjustment, so a colour test names only the
    // branch it exercises.
    private static final OwnerStyleDecision OWNED_STYLED =
        buildOwnedStyled(ElementStyleAdjustment.NONE);

    private static final OwnerStyleDecision INDEPENDENT_STYLED =
        new OwnerStyleDecision(StandInCategoryFake.RECEDED, ElementStyleAdjustment.NONE);

    // An owned-styled owner under a given recede, for the tests that vary the adjustment
    // rather than the classification.
    private static OwnerStyleDecision buildOwnedStyled(ElementStyleAdjustment adjustment) {
        return new OwnerStyleDecision(StandInCategoryFake.OWNED, adjustment);
    }

    // Both categories pointed at the same slot and opacity, so a test that varies neither reads
    // one shade whichever category the owner was placed in.
    private static OwnerNameStyles nameStyles(ElementPaintSelection ownedOuterSelection) {

        return nameStyles(
            ownedOuterSelection,
            OwnerPaletteSlot.PRIMARY,
            FULL_OPACITY,
            FULL_OPACITY);
    }

    // The two categories' name styles spelled out in full, so a test can point the owned and
    // independent categories at different slots or opacities and prove which one was read.
    // Takes the selections a style actually holds rather than the settings-side choices they
    // are stored as, so these tests pin the label rule and not the crossing between the two -
    // which OwnerPaletteSlotTests owns. A null selection is a hidden element.
    private static OwnerNameStyles nameStyles(
            ElementPaintSelection ownedOuterSelection,
            ElementPaintSelection independentOuterSelection,
            double ownedNameOpacity,
            double independentNameOpacity) {

        return new OwnerNameStyles(Map.of(
            StandInCategoryFake.OWNED,
            new ElementStyle(ownedOuterSelection, ownedNameOpacity),
            StandInCategoryFake.RECEDED,
            new ElementStyle(independentOuterSelection, independentNameOpacity)));
    }

    @Nested
    class ResolveLabelColour {

        @Test
        void takesThePrimaryShadeWhenTheOuterBorderIsPrimary() {
            // The default outer-border choice is the bright primary shade, so the name
            // (and its debug dot) inherits it - RED here.
            var colour = ClusterLabelStyling.resolveLabelColour(
                OWNER_F,
                nameStyles(OwnerPaletteSlot.PRIMARY),
                OWNED_STYLED,
                UNUSED_PALETTE);

            assertThat(colour)
                .isEqualTo(PRIMARY);
        }

        @Test
        void inheritsTheSecondaryShadeWhenTheOuterBorderIsSecondary() {
            // Point the outer border at the secondary (dark) shade and the name follows
            // it - BLUE - so the label reads as the border's own colour, not a fixed pick.
            var colour = ClusterLabelStyling.resolveLabelColour(
                OWNER_F,
                nameStyles(OwnerPaletteSlot.SECONDARY),
                OWNED_STYLED,
                UNUSED_PALETTE);

            assertThat(colour)
                .isEqualTo(SECONDARY);
        }

        @Test
        void fallsBackToThePrimaryShadeWhenTheOuterBorderIsHidden() {
            // A hidden outer border ("No color") resolves to no colour, but a name still
            // needs one, so it falls back to the bright primary shade rather than vanishing.
            var colour = ClusterLabelStyling.resolveLabelColour(
                OWNER_F,
                nameStyles(null),
                OWNED_STYLED,
                UNUSED_PALETTE);

            assertThat(colour)
                .isEqualTo(PRIMARY);
        }

        @Test
        void followsTheNameStyleOfTheCategoryTheOwnerDrawsIn() {
            // The category, not a hardcoded independent-owner test, decides which name style the
            // label follows. Placed in the independent category, the label inherits its choice
            // (SECONDARY -> BLUE) even though the owned choice differs (PRIMARY) - the seam a
            // layer drives when it places some owners in a quieter category.
            var colour = ClusterLabelStyling.resolveLabelColour(
                OWNER_F,
                nameStyles(
                    OwnerPaletteSlot.PRIMARY,
                    OwnerPaletteSlot.SECONDARY,
                    FULL_OPACITY,
                    FULL_OPACITY),
                INDEPENDENT_STYLED,
                UNUSED_PALETTE);

            assertThat(colour)
                .isEqualTo(SECONDARY);
        }

        @Test
        void fadesAnOwnedNameByTheOwnedNameOpacity() {
            // An owned-category owner takes the owned category's name opacity: half fades the
            // resolved PRIMARY shade's alpha to half, leaving its RGB (and the dot's) intact.
            var colour = ClusterLabelStyling.resolveLabelColour(
                OWNER_F,
                nameStyles(
                    OwnerPaletteSlot.PRIMARY,
                    OwnerPaletteSlot.PRIMARY,
                    HALF_OPACITY,
                    FULL_OPACITY),
                OWNED_STYLED,
                UNUSED_PALETTE);

            assertThat(colour.getAlpha())
                .isEqualTo(Math.round(PRIMARY.getAlpha() * (float) HALF_OPACITY));
            assertThat(colour.getRed())
                .isEqualTo(PRIMARY.getRed());
        }

        @Test
        void leavesAnOwnedNameUntouchedByTheIndependentNameOpacity() {
            // The independent category's opacity fades only independent names: an owned-category
            // owner is unaffected even when independent opacity is dimmed, so the two categories
            // fade independently.
            var colour = ClusterLabelStyling.resolveLabelColour(
                OWNER_F,
                nameStyles(
                    OwnerPaletteSlot.PRIMARY,
                    OwnerPaletteSlot.PRIMARY,
                    FULL_OPACITY,
                    HALF_OPACITY),
                OWNED_STYLED,
                UNUSED_PALETTE);

            assertThat(colour)
                .isEqualTo(PRIMARY);
        }

        @Test
        void fadesAnIndependentNameByTheIndependentNameOpacity() {
            // An independent-category owner takes the independent category's opacity: half fades
            // its resolved shade's alpha to half, while the owned opacity (full here) has
            // no say over it.
            var colour = ClusterLabelStyling.resolveLabelColour(
                OWNER_F,
                nameStyles(
                    OwnerPaletteSlot.PRIMARY,
                    OwnerPaletteSlot.PRIMARY,
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
        void mutesTheAlphaForAnOwnerWithAMutedAdjustment() {
            // An owner's adjustment dims its label on top of the (full) name opacity: half
            // fades the resolved shade's alpha to half and leaves its RGB intact - the same
            // fold applied wherever the style classification is read.
            var colour = ClusterLabelStyling.resolveLabelColour(
                OWNER_F,
                nameStyles(OwnerPaletteSlot.PRIMARY),
                buildOwnedStyled(new ElementStyleAdjustment(HALF_OPACITY, false)),
                UNUSED_PALETTE);

            assertThat(colour.getAlpha())
                .isEqualTo(Math.round(PRIMARY.getAlpha() * (float) HALF_OPACITY));
            assertThat(colour.getRed())
                .isEqualTo(PRIMARY.getRed());
        }

        @Test
        void desaturatesToThePassPaletteForADesaturatedOwner() {
            // A desaturated owner's label follows the pass's shared desaturation palette
            // instead of the owner's own shades, at full alpha since mute is off here.
            var colour = ClusterLabelStyling.resolveLabelColour(
                OWNER_F,
                nameStyles(OwnerPaletteSlot.PRIMARY),
                buildOwnedStyled(new ElementStyleAdjustment(FULL_OPACITY, true)),
                new ColourPair(Color.GREEN, Color.YELLOW));

            assertThat(colour)
                .isEqualTo(Color.GREEN);
        }

        @Test
        void leavesAnUnadjustedOwnerUntouchedRegardlessOfThePalette() {
            // An owner the resolver maps to NONE draws unmuted and undesaturated no matter what the
            // pass's palette holds, since the adjustment (not the palette alone) gates whether
            // either applies.
            var colour = ClusterLabelStyling.resolveLabelColour(
                OWNER_F,
                nameStyles(OwnerPaletteSlot.PRIMARY),
                OWNED_STYLED,
                new ColourPair(Color.GREEN, Color.YELLOW));

            assertThat(colour)
                .isEqualTo(PRIMARY);
        }

        @Test
        void givesAFilterRecededNameTheSameRecededPaletteItsFillTakes() {
            // Under a filter a non-spotlit owner recedes: its label must follow the pass's shared
            // desaturation palette - the exact palette OwnerMapBuilder recolours its fill to for
            // the same recede - so the receded name never drifts from the receded fill. The recede
            // both mutes and desaturates, as a real filter recede can; the colour comparison reads
            // RGB, since the label additionally fades its alpha by the name opacity the fill omits.
            var recede = new ElementStyleAdjustment(HALF_OPACITY, true);
            var desaturationPalette = new ColourPair(Color.GREEN, Color.YELLOW);

            var labelColour = ClusterLabelStyling.resolveLabelColour(
                OWNER_F,
                nameStyles(OwnerPaletteSlot.PRIMARY),
                buildOwnedStyled(recede),
                desaturationPalette);

            // The shade MapPalettes resolves the same owner's fill to under the same recede.
            var fillShade = MapPalettes
                .resolveEffectivePalette(recede, OWNER_F, desaturationPalette).primaryColour();

            assertThat(labelColour.getRed())
                .isEqualTo(fillShade.getRed());
            assertThat(labelColour.getGreen())
                .isEqualTo(fillShade.getGreen());
            assertThat(labelColour.getBlue())
                .isEqualTo(fillShade.getBlue());

            // And genuinely receded, not the owner's own bright shade.
            assertThat(labelColour.getGreen())
                .isNotEqualTo(PRIMARY.getGreen());
        }
    }

    @Nested
    class NewLabelColourResolver {

        // A second owner, so a lookup that answered the wrong owner's shade is visible.
        private static final Color OTHER_PRIMARY = Color.CYAN;

        private static final SystemOwner OWNER_G =
            new SystemOwner("G", new ColourPair(OTHER_PRIMARY, Color.DARK_GRAY));

        @Test
        void answersTheShadeOfTheOwnerItIsAskedFor() {
            // Every system of an owner carries that owner's own two shades, so the resolver must
            // key off the owner ID the search hands it rather than any one system.
            var resolver = ClusterLabelStyling.newLabelColourResolver(
                Map.of(buildCellKey("alpha"), OWNER_F, buildCellKey("beta"), OWNER_G),
                nameStyles(OwnerPaletteSlot.PRIMARY),
                ownerId -> new OwnerStyleDecision(StandInCategoryFake.OWNED, ElementStyleAdjustment.NONE),
                UNUSED_PALETTE);

            assertThat(resolver.apply("F"))
                .isEqualTo(PRIMARY);
            assertThat(resolver.apply("G"))
                .isEqualTo(OTHER_PRIMARY);
        }

        @Test
        void appliesEachOwnersOwnStyleDecision() {
            // The decision carries both halves the colour needs - which category's name style to
            // follow and how the owner recedes - so an owner placed in the independent category
            // takes its choice while its neighbour keeps the owned one.
            var resolver = ClusterLabelStyling.newLabelColourResolver(
                Map.of(buildCellKey("alpha"), OWNER_F, buildCellKey("beta"), OWNER_G),
                nameStyles(
                    OwnerPaletteSlot.PRIMARY,
                    OwnerPaletteSlot.SECONDARY,
                    FULL_OPACITY,
                    FULL_OPACITY),
                ownerId -> new OwnerStyleDecision(
                    "F".equals(ownerId) ? StandInCategoryFake.RECEDED : StandInCategoryFake.OWNED,
                    ElementStyleAdjustment.NONE),
                UNUSED_PALETTE);

            assertThat(resolver.apply("F"))
                .isEqualTo(SECONDARY);
            assertThat(resolver.apply("G"))
                .isEqualTo(OTHER_PRIMARY);
        }

        @Test
        void decidesOncePerOwnerAcrossItsClusters() {
            // Every cluster of an owner draws its name the same, and an owner can hold many, so
            // the decision is made once and reused rather than re-resolved per cluster.
            var askedOwnerIds = new ArrayList<String>();
            var resolver = ClusterLabelStyling.newLabelColourResolver(
                Map.of(buildCellKey("alpha"), OWNER_F),
                nameStyles(OwnerPaletteSlot.PRIMARY),
                ownerId -> {
                    askedOwnerIds.add(ownerId);
                    return new OwnerStyleDecision(StandInCategoryFake.OWNED, ElementStyleAdjustment.NONE);
                },
                UNUSED_PALETTE);

            resolver.apply("F");
            resolver.apply("F");

            assertThat(askedOwnerIds)
                .containsExactly("F");
        }
    }

    @Nested
    class CreateLabelResolvers {

        // A second owner, so a pair that answered the wrong owner's shade is visible.
        private static final SystemOwner OWNER_G =
            new SystemOwner("G", new ColourPair(Color.CYAN, Color.DARK_GRAY));

        // The shades a receded owner recolours to, garish so a name that kept its own colour
        // through a recede is unmistakable.
        private static final ColourPair DESATURATION_PALETTE =
            new ColourPair(Color.GREEN, Color.YELLOW);

        // A settled face rather than the face labels ask for, so a pair measuring in the asked-for
        // face cannot pass.
        private static final StarsectorFont SETTLED_LABEL_FACE = StarsectorFont.VANILLA_INSIGNIA_25;

        private final OwnerCategories categoriesMock = mock(OwnerCategories.class);

        private MockedStatic<LazyFontCache> fontsMock;

        @BeforeEach
        void openTheFontSeam() {

            fontsMock = mockStatic(LazyFontCache.class);

            // No label font resolves outside the game, which is the fallback the pair is designed
            // to take: the aspect stand-in sizes a box and no name is drawn from it.
            fontsMock
                .when(() -> LazyFontCache.loadByFace(any()))
                .thenReturn(null);

            // Both categories' names on the bright primary shade at full opacity, so a name's
            // colour reads back as the palette shade its owner resolved to.
            when(categoriesMock.readNameStyles())
                .thenReturn(Map.of(
                    StandInCategoryFake.OWNED,
                    new ElementStyle(OwnerPaletteSlot.PRIMARY, FULL_OPACITY),
                    StandInCategoryFake.RECEDED,
                    new ElementStyle(OwnerPaletteSlot.PRIMARY, FULL_OPACITY)));

            when(categoriesMock.resolveFullStrengthCategory())
                .thenReturn(StandInCategoryFake.OWNED);
        }

        @AfterEach
        void closeTheFontSeam() {

            fontsMock.close();
        }

        @Test
        void shadesEachOwnerByItsOwnPalette() {
            // The pair is the owner tier's whole answer to a name, so it must key off the owner the
            // fit asks about rather than one shared pick.
            var resolvers = ClusterLabelStyling.createLabelResolvers(buildSnapshot(
                ContentInputsFixtures.createInputsSpellingNames(OwnerNameFormatChoice.FULL)));

            assertThat(resolvers.labelColourByOwner().apply("F"))
                .isEqualTo(PRIMARY);
            assertThat(resolvers.labelColourByOwner().apply("G"))
                .isEqualTo(Color.CYAN);
        }

        @Test
        void recedesANonSpotlitOwnerToThePassDesaturationPalette() {
            // Under a filter every owner but the spotlit one recedes, and its name has to recede with
            // its fill: the shared palette this pass recoloured that fill to is the one the name
            // draws in, so the two cannot drift apart while a spotlight is up.
            var resolvers = ClusterLabelStyling.createLabelResolvers(buildSnapshot(new ContentInputs(
                "F",
                new ElementStyleAdjustment(FULL_OPACITY, true),
                ElementStyleAdjustment.NONE,
                OwnerNameFormatChoice.FULL,
                false)));

            assertThat(resolvers.labelColourByOwner().apply("G"))
                .isEqualTo(Color.GREEN);
        }

        @Test
        void measuresTheNamesInTheFaceTheSnapshotSettled() {
            // The face the boxes are sized to is the one the mint draws in; measuring in any other
            // would size the boxes for glyphs nothing draws.
            ClusterLabelStyling
                .createLabelResolvers(buildSnapshot(
                    ContentInputsFixtures.createInputsSpellingNames(OwnerNameFormatChoice.FULL)))
                .nameEstimatorByOwner()
                .apply("F");

            fontsMock.verify(() -> LazyFontCache.loadByFace(SETTLED_LABEL_FACE));
        }

        // A snapshot over the two owners, under the given picks, desaturating a receded owner to the
        // garish palette above.
        private ClusterLabelStylingSnapshot buildSnapshot(ContentInputs contentInputs) {

            return new ClusterLabelStylingSnapshot(
                Map.of(buildCellKey("alpha"), OWNER_F, buildCellKey("beta"), OWNER_G),
                new MapPaints(
                    new MapStyling(
                        null,
                        null,
                        categoriesMock,
                        UNUSED_PALETTE,
                        DESATURATION_PALETTE,
                        UNUSED_PALETTE),
                    OwnerReadingFake.createAnsweringNothing(),
                    contentInputs),
                SETTLED_LABEL_FACE);
        }
    }
}
