package kmu.maplayers.politicalmap.views;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.factions.alliances.FactionAlliances;

import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;
import kmu.maplayers.politicalmap.dominance.tooltip.SystemDominationTooltip;
import kmu.mods.nexerelin.NexerelinAlliances;
import kmu.util.KmuPoliticalMapStringKeys;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.starsector.SectorFactionFixtures.stubNamedFaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins what the factions view decides: its frozen ID and label, the identity grouping, every bloc as
 * a spotlight target since no faction here is folded into another, and the domination box on hover,
 * since domination is what painted the cells.
 *
 * <p>What every view the domination contest paints answers alike is {@link DominancePaintedView}'s,
 * and what every view of the layer answers alike is {@link PoliticalHolderView}'s, each pinned once
 * by its own suite.
 */
final class FactionsViewTests {

    // The faction view ignores its grouping argument (its own grouping is always identity),
    // so any grouping stands in where the interface demands one.
    private static final HolderGrouping ANY_GROUPING = HolderGrouping.identity();

    @Nested
    class GetId {

        @Test
        void isTheFrozenFactionsId() {
            // Frozen: renaming it silently resets any save that selected this view to the default.
            assertThat(FactionsView.INSTANCE.getId())
                .isEqualTo("factions");
        }
    }

    @Nested
    class GetSegmentLabelKey {

        @Test
        void isTheFactionsRadioLabel() {

            assertThat(FactionsView.INSTANCE.getSegmentLabelKey())
                .isEqualTo(KmuPoliticalMapStringKeys.POLITICAL_MAP_CTL_FACTIONS);
        }
    }

    @Nested
    class ResolveGrouping {

        @Test
        void returnsTheIdentityGrouping() {
            // Every faction is its own bloc, so the pipeline resolves plain faction holding.
            assertThat(FactionsView.INSTANCE.resolveGrouping())
                .isSameAs(HolderGrouping.identity());
        }
    }

    @Nested
    class ResolveViewReading {

        @Test
        void namesBlocsByTheirFactions() {
            // The reading the core asks for, over the identity grouping this view paints by. That
            // the source folds under that same grouping is the shared assembly's, pinned once.
            var sectorMock = mock(SectorAPI.class);

            stubNamedFaction(sectorMock, "hegemony", "Hegemony");

            try (var alliancesMock = mockStatic(NexerelinAlliances.class)) {

                alliancesMock
                    .when(NexerelinAlliances::resolveGrouping)
                    .thenReturn(HolderGrouping.identity());

                // The contest affiliation reads the alliance set as well, and nobody allied is the
                // answer that leaves the name this case is about to the grouping alone.
                alliancesMock
                    .when(NexerelinAlliances::resolveFactionAlliances)
                    .thenReturn(FactionAlliances.NONE);

                assertThat(FactionsView
                        .INSTANCE
                        .resolveViewReading(sectorMock)
                        .reading()
                        .resolveName("hegemony", OwnerNameFormatChoice.SHORT))
                    .isEqualTo("Hegemony");
            }
        }
    }

    @Nested
    class ResolveHoverTooltip {

        @Test
        void offersTheDominationBox() {
            // The box explaining the fills this view paints, which are painted by domination.
            assertThat(FactionsView.INSTANCE.resolveHoverTooltip())
                .containsInstanceOf(SystemDominationTooltip.class);
        }

        @Test
        void offersTheSameBoxOnEveryAsk() {
            // Held once by the view rather than built per hover, so every ask reads one box.
            assertThat(FactionsView.INSTANCE.resolveHoverTooltip().orElseThrow())
                .isSameAs(FactionsView.INSTANCE.resolveHoverTooltip().orElseThrow());
        }
    }

    @Nested
    class ResolveSelectableBlocGate {

        @Test
        void admitsEveryBloc() {
            // Under identity every faction is its own bloc, so a core faction, independent space and
            // a pirate bloc alike are spotlight targets; presence is the shared stats read's gate.
            var gate = FactionsView.INSTANCE.resolveSelectableBlocGate(ANY_GROUPING);

            assertThat(List.of("hegemony", Factions.INDEPENDENT, "pirates"))
                .allMatch(gate);
        }
    }
}
