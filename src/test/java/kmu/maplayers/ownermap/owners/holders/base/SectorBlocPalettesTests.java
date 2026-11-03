package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.colour.ColourPair;

import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.starsector.SectorFactionFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static kmu.maplayers.ownermap.owners.holders.base.HolderGroupingFixture.GROUP_BLOC_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how a bloc's two shades are found: through the grouping's colour faction rather than
 * through the bloc ID, and as nothing at all when that faction is gone - and the owner built from
 * them, which carries the bloc's own ID beside the shades and is resolved once per pass.
 *
 * <p>Both matter because a bloc is not always a faction. A group carries a synthetic bloc ID no
 * {@code FactionAPI} answers to, so asking the sector for it directly would leave every group
 * colourless on a view where the fills are perfectly well coloured; and a bloc the sector
 * cannot name at all has to come back null, since that null is what has a caller drop the bloc
 * rather than paint it in a stand-in shade.
 */
final class SectorBlocPalettesTests {

    private static final String LEAD_MEMBER = "hegemony";
    private static final String SECOND_MEMBER = "persean";

    private static final Color BRIGHT = new Color(140, 160, 220);
    private static final Color DARK = new Color(40, 60, 120);

    @Nested
    class ReadBlocPalette {

        // The group case: the bloc's own ID names no faction, and its lead member's authored
        // pair is what the map paints it in.
        @Test
        void readsTheShadesOfTheFactionTheGroupingColoursTheBlocBy() {

            // ARRANGE
            var sectorMock = SectorFactionFixtures.buildSectorShadingFaction(
                LEAD_MEMBER,
                BRIGHT,
                DARK);

            var expectedPalette = new ColourPair(BRIGHT, DARK);

            // ACT
            var actualPalette = readPaletteFrom(sectorMock, GROUP_BLOC_ID);

            // ASSERT
            assertThat(actualPalette)
                .isEqualTo(expectedPalette);
        }

        @Test
        void readsNoPaletteForABlocWhoseColourFactionIsGone() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getFaction(LEAD_MEMBER))
                .thenReturn(null);

            // ACT
            var actualPalette = readPaletteFrom(sectorMock, GROUP_BLOC_ID);

            // ASSERT
            assertThat(actualPalette)
                .isNull();
        }

        // The sector is absent outside a running game, and a band asked for one then answers
        // with no colour rather than throwing on the way to a map nobody is looking at.
        @Test
        void readsNoPaletteWithoutASector() {

            // ACT
            var actualPalette = readPaletteFrom(null, GROUP_BLOC_ID);

            // ASSERT
            assertThat(actualPalette)
                .isNull();
        }

        @Test
        void answersASecondAskWithTheShadesTheFirstRead() {

            // ARRANGE
            var palettes = new SectorBlocPalettes(
                SectorFactionFixtures.buildSectorShadingFaction(LEAD_MEMBER, BRIGHT, DARK),
                buildGroupLedByTheLeadMember());

            var expectedPalette = new ColourPair(BRIGHT, DARK);

            palettes.readBlocPalette(GROUP_BLOC_ID);

            // ACT
            var actualPalette = palettes.readBlocPalette(GROUP_BLOC_ID);

            // ASSERT
            assertThat(actualPalette)
                .isEqualTo(expectedPalette);
        }

        // Every system's holder and every cell's band asks per bloc, so a pass that went back to
        // the sector each time would look the same faction up once per cell.
        @Test
        void asksTheSectorForABlocsColourFactionOnce() {

            // ARRANGE
            var sectorMock = SectorFactionFixtures.buildSectorShadingFaction(LEAD_MEMBER, BRIGHT, DARK);
            var palettes = new SectorBlocPalettes(sectorMock, buildGroupLedByTheLeadMember());

            palettes.readBlocPalette(GROUP_BLOC_ID);

            // ACT
            palettes.readBlocPalette(GROUP_BLOC_ID);

            // ASSERT
            verify(sectorMock, times(1))
                .getFaction(LEAD_MEMBER);
        }

        // The absence is remembered as well as a palette is: a bloc the sector cannot name is
        // asked about as often as one it can.
        @Test
        void remembersABlocWhoseColourFactionIsGone() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var palettes = new SectorBlocPalettes(sectorMock, buildGroupLedByTheLeadMember());

            palettes.readBlocPalette(GROUP_BLOC_ID);

            // ACT
            var actualPalette = palettes.readBlocPalette(GROUP_BLOC_ID);

            // ASSERT
            assertThat(actualPalette)
                .isNull();

            verify(sectorMock, times(1))
                .getFaction(LEAD_MEMBER);
        }
    }

    @Nested
    class ReadSegmentPalette {

        // A bloc's shades are the bloc's, so every member's colony draws in the pair its fill
        // is painted in - which is what keeps a run coloured by bloc one pair end to end.
        @Test
        void answersTheBlocsShadesWhicheverMemberOwnsTheColony() {

            // ARRANGE
            var palettes = new SectorBlocPalettes(
                SectorFactionFixtures.buildSectorShadingFaction(LEAD_MEMBER, BRIGHT, DARK),
                HolderGroupingFixture.buildGroupOf(LEAD_MEMBER, SECOND_MEMBER));

            var expectedPalette = new ColourPair(BRIGHT, DARK);

            // ACT
            var actualPalette = palettes.readSegmentPalette(GROUP_BLOC_ID, SECOND_MEMBER);

            // ASSERT
            assertThat(actualPalette)
                .isEqualTo(expectedPalette);
        }
    }

    @Nested
    class ResolveOwnerOf {

        @Test
        void pairsAFactionBlocWithItsOwnAuthoredShades() {

            // ARRANGE
            var sectorMock = SectorFactionFixtures.buildSectorShadingFaction(
                LEAD_MEMBER,
                BRIGHT,
                DARK);

            var palettes = new SectorBlocPalettes(sectorMock, HolderGrouping.identity());
            var expectedOwner = new SystemOwner(LEAD_MEMBER, new ColourPair(BRIGHT, DARK));

            // ACT
            var actualOwner = palettes.resolveOwnerOf(LEAD_MEMBER);

            // ASSERT
            assertThat(actualOwner)
                .isEqualTo(expectedOwner);
        }

        // The group's ID is no faction, so the palette is looked up through the leading member;
        // the owner still carries the group's ID, which is what the cells cluster by.
        @Test
        void coloursAGroupByItsLeadingMemberWhileKeepingTheGroupsId() {

            // ARRANGE
            var sectorMock = SectorFactionFixtures.buildSectorShadingFaction(
                LEAD_MEMBER,
                BRIGHT,
                DARK);

            var palettes = new SectorBlocPalettes(sectorMock, buildGroupLedByTheLeadMember());
            var expectedOwner = new SystemOwner(GROUP_BLOC_ID, new ColourPair(BRIGHT, DARK));

            // ACT
            var actualOwner = palettes.resolveOwnerOf(GROUP_BLOC_ID);

            // ASSERT
            assertThat(actualOwner)
                .isEqualTo(expectedOwner);
        }

        // A faction a mod removed mid-save leaves a bloc with no palette to paint in, so the
        // system drops as unowned rather than drawing in colours nobody authored.
        @Test
        void returnsNullWhenNoColourFactionIsPresent() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var palettes = new SectorBlocPalettes(sectorMock, HolderGrouping.identity());

            // ACT
            var actualOwner = palettes.resolveOwnerOf("vanished");

            // ASSERT
            assertThat(actualOwner)
                .isNull();
        }

        // Every system a bloc holds asks for its owner, so the pass hands each of them the one
        // value rather than a new owner per system.
        @Test
        void answersEveryAskWithTheOneOwner() {

            // ARRANGE
            var palettes = new SectorBlocPalettes(
                SectorFactionFixtures.buildSectorShadingFaction(LEAD_MEMBER, BRIGHT, DARK),
                HolderGrouping.identity());

            var firstOwner = palettes.resolveOwnerOf(LEAD_MEMBER);

            // ACT
            var actualOwner = palettes.resolveOwnerOf(LEAD_MEMBER);

            // ASSERT
            assertThat(actualOwner)
                .isSameAs(firstOwner);
        }

        // One memo answers both reads, so a holder resolve and a band over the same bloc look
        // its colour faction up once between them.
        @Test
        void sharesItsLookupWithThePaletteRead() {

            // ARRANGE
            var sectorMock = SectorFactionFixtures.buildSectorShadingFaction(LEAD_MEMBER, BRIGHT, DARK);
            var palettes = new SectorBlocPalettes(sectorMock, HolderGrouping.identity());

            palettes.resolveOwnerOf(LEAD_MEMBER);

            // ACT
            palettes.readBlocPalette(LEAD_MEMBER);

            // ASSERT
            verify(sectorMock, times(1))
                .getFaction(LEAD_MEMBER);
        }
    }

    // The palettes as one group's bloc reads them: the bloc folds to its lead member, which is
    // the faction the sector is asked for.
    private static ColourPair readPaletteFrom(SectorAPI sector, String blocId) {

        return new SectorBlocPalettes(sector, buildGroupLedByTheLeadMember())
            .readBlocPalette(blocId);
    }

    // One group whose lead member is the faction every sector here shades, so the group's ID is no
    // faction ID and its colour can only come through that member.
    private static HolderGrouping buildGroupLedByTheLeadMember() {
        return HolderGroupingFixture.buildGroupOf(LEAD_MEMBER);
    }
}
