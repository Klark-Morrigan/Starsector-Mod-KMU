package kmu.maplayers.ownermap.render.style;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.starsector.StarsectorFactionFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins how a bloc's two shades are found: through the grouping's colour faction rather than
 * through the bloc ID, and as nothing at all when that faction is gone - and the owner built from
 * them, which carries the bloc's own ID beside the shades. A faction's pair keeps its slots exactly
 * on the way into the tier's type, bright into primary and dark into secondary.
 *
 * <p>Both matter because a bloc is not always a faction. A group carries a synthetic bloc ID no
 * {@code FactionAPI} answers to, so asking the sector for it directly would leave every group
 * colourless on a view where the fills are perfectly well coloured; and a bloc the sector
 * cannot name at all has to come back null, since that null is what has a caller drop the bloc
 * rather than paint it in a stand-in shade.
 */
final class SectorBlocPalettesTests {

    private static final String GROUP_BLOC = "hegemony_compact";
    private static final String LEAD_MEMBER = "hegemony";

    private static final Color BRIGHT = new Color(140, 160, 220);
    private static final Color DARK = new Color(40, 60, 120);

    @Nested
    class ReadBlocPalette {

        @Test
        void readsTheShadesOfTheFactionTheGroupingColoursTheBlocBy() {
            // The group case: the bloc's own ID names no faction, and its lead member's authored
            // pair is what the map paints it in.
            var sectorMock = StarsectorFactionFixtures.buildSectorShadingFaction(
                LEAD_MEMBER,
                BRIGHT,
                DARK);

            assertThat(readPaletteFrom(sectorMock, GROUP_BLOC))
                .isEqualTo(new OwnerPalette(BRIGHT, DARK));
        }

        @Test
        void readsNoPaletteForABlocWhoseColourFactionIsGone() {

            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getFaction(LEAD_MEMBER))
                .thenReturn(null);

            assertThat(readPaletteFrom(sectorMock, GROUP_BLOC))
                .isNull();
        }

        @Test
        void readsNoPaletteWithoutASector() {
            // The sector is absent outside a running game, and a band asked for one then answers
            // with no colour rather than throwing on the way to a map nobody is looking at.
            assertThat(readPaletteFrom(null, GROUP_BLOC))
                .isNull();
        }
    }

    @Nested
    class ResolveOwnerOf {

        @Test
        void pairsAFactionBlocWithItsOwnAuthoredShades() {

            var sectorMock = StarsectorFactionFixtures.buildSectorShadingFaction(
                LEAD_MEMBER,
                BRIGHT,
                DARK);

            assertThat(new SectorBlocPalettes(sectorMock, HolderGrouping.identity())
                    .resolveOwnerOf(LEAD_MEMBER))
                .isEqualTo(new SystemOwner(LEAD_MEMBER, new OwnerPalette(BRIGHT, DARK)));
        }

        @Test
        void coloursAGroupByItsLeadingMemberWhileKeepingTheGroupsId() {
            // The group's ID is no faction, so the palette is looked up through the leading member;
            // the owner still carries the group's ID, which is what the cells cluster by.
            var sectorMock = StarsectorFactionFixtures.buildSectorShadingFaction(
                LEAD_MEMBER,
                BRIGHT,
                DARK);

            assertThat(new SectorBlocPalettes(sectorMock, buildGroupLedByTheLeadMember())
                    .resolveOwnerOf(GROUP_BLOC))
                .isEqualTo(new SystemOwner(GROUP_BLOC, new OwnerPalette(BRIGHT, DARK)));
        }

        @Test
        void returnsNullWhenNoColourFactionIsPresent() {
            // A faction a mod removed mid-save leaves a bloc with no palette to paint in, so the
            // system drops as unowned rather than drawing in colours nobody authored.
            var sectorMock = mock(SectorAPI.class);

            assertThat(new SectorBlocPalettes(sectorMock, HolderGrouping.identity())
                    .resolveOwnerOf("vanished"))
                .isNull();
        }
    }

    @Nested
    class AdoptFactionPalette {

        @Test
        void keepsTheBrightShadePrimaryAndTheDarkSecondary() {

            assertThat(SectorBlocPalettes.adoptFactionPalette(new FactionPalette(BRIGHT, DARK)))
                .isEqualTo(new OwnerPalette(BRIGHT, DARK));
        }
    }

    // The palettes as one group's bloc reads them: the bloc folds to its lead member, which is
    // the faction the sector is asked for.
    private static OwnerPalette readPaletteFrom(SectorAPI sector, String blocId) {
        return new SectorBlocPalettes(sector, buildGroupLedByTheLeadMember())
            .readBlocPalette(blocId);
    }

    // One group whose lead member is the faction every sector here shades, so the group's ID is no
    // faction ID and its colour can only come through that member.
    private static HolderGrouping buildGroupLedByTheLeadMember() {
        return new HolderGrouping(
            Map.of(LEAD_MEMBER, GROUP_BLOC),
            Map.of(GROUP_BLOC, LEAD_MEMBER),
            Map.of(GROUP_BLOC, "The Hegemony Compact"));
    }
}
