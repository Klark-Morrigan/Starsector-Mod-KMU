package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.claims.ClaimReader;

import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.politicalmap.claims.SectorClaims;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;

import static kmu.maplayers.ownermap.holding.ColonyReadRulesFixtures.UNDER_THE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the Claims view's per-system holder read: the reader it asks is opened over the batch's own
 * pass, and the answer is the single-system claim resolve's, which owns the fold and the colouring
 * and has its own suite.
 */
final class ClaimSystemHolderResolveTests {

    @Nested
    class CreateSourceReadingThrough {

        @Test
        void opensItsClaimReaderOverThePassesOwnWalk() {
            // The reader shares the batch's walk of each system rather than adding one.
            var pass = HolderPass.over(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity());
            var openedOver = new ArrayList<SectorPassIndex>();

            ClaimSystemHolderResolve
                .createSourceReadingThrough((visibility, colonies) -> {
                    openedOver.add(colonies);
                    return mock(ClaimReader.class);
                })
                .openResolveOver(pass);

            assertThat(openedOver)
                .containsExactly(pass.sectorIndex());
        }
    }

    @Nested
    class ResolveHolderIn {

        @Test
        void answersWithTheClaimantTheSingleSystemResolveFolds() {

            var pass = HolderPass.over(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity());
            var claimReaderMock = mock(ClaimReader.class);
            var systemMock = mock(StarSystemAPI.class);
            var claimant = new SystemOwner("hegemony", new OwnerPalette(Color.RED, Color.BLUE));

            var resolve = ClaimSystemHolderResolve
                .createSourceReadingThrough((visibility, colonies) -> claimReaderMock)
                .openResolveOver(pass);

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderIn(
                        same(systemMock),
                        same(pass),
                        same(claimReaderMock),
                        any()))
                    .thenReturn(claimant);

                assertThat(resolve.resolveHolderIn(systemMock))
                    .isSameAs(claimant);
            }
        }
    }
}
