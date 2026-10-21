package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.HolderResolution;
import kmu.maplayers.politicalmap.dominance.FilteredPolitics;
import kmu.maplayers.politicalmap.dominance.SectorPolitics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the held-dominance holding provider's one job: delegating to one of two resolvers,
 * branching on whether a bloc is spotlighted. Off filter it
 * reproduces {@link SectorPolitics#resolveDominantHolderBySystemKey} with nothing contested;
 * under a filter it hands on {@link FilteredPolitics#resolveFilteredHolder}'s resolution whole.
 * The resolvers themselves read the live economy, so they are stubbed here and covered end to end
 * in their own integration suites.
 */
final class DominanceHolderProviderTests {

    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    private static final SystemKey OWNED_SYSTEM = buildCellKey("owned-system");

    @Nested
    class ResolveHolder {

        @Test
        void returnsTheDominantHoldersAndNothingContestedOffFilter() {

            var sectorMock = mock(SectorAPI.class);
            var pass = HolderPass.createOver(sectorMock, UNDER_THE_FOG, HolderGrouping.identity());
            var holders = Map.of(
                OWNED_SYSTEM,
                new SystemOwner("hegemony", new ColourPair(PRIMARY, SECONDARY)));

            try (var sectorPoliticsMock = mockStatic(SectorPolitics.class)) {

                sectorPoliticsMock
                    .when(() -> SectorPolitics.resolveDominantHolderBySystemKey(pass))
                    .thenReturn(holders);

                var resolution = DominanceHolderProvider.INSTANCE.resolveHolder(pass, null);

                assertThat(resolution.ownerBySystemKey())
                    .isEqualTo(holders);
                assertThat(resolution.fillStates().getNonSolidFillStateBySystemKey())
                    .doesNotContainValue(FillState.HATCHED);
                assertThat(resolution.fillStates().getNonSolidFillStateBySystemKey())
                    .doesNotContainValue(FillState.UNFILLED);
            }
        }

        @Test
        void passesThroughThePresenceAwareResolverWhenABlocIsSpotlighted() {

            var sectorMock = mock(SectorAPI.class);
            var pass = HolderPass.createOver(sectorMock, UNDER_THE_FOG, HolderGrouping.identity());
            var holders = Map.of(
                OWNED_SYSTEM,
                new SystemOwner("$spotlit", new ColourPair(PRIMARY, SECONDARY)));

            var filtered = new HolderResolution(
                holders,
                SystemFillStates.createOf(Map.of(OWNED_SYSTEM, FillState.HATCHED)));

            try (var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {

                filteredPoliticsMock
                    .when(() -> FilteredPolitics.resolveFilteredHolder(pass, "hegemony"))
                    .thenReturn(filtered);

                // Handed on whole: the presence-aware resolver already answers the full resolution,
                // so nothing about its holders or its fill states is restated here.
                assertThat(DominanceHolderProvider.INSTANCE.resolveHolder(pass, "hegemony"))
                    .isSameAs(filtered);
            }
        }
    }
}
