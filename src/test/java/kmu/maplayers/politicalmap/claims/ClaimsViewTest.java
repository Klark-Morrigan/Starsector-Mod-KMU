package kmu.maplayers.politicalmap.claims;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.dominance.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.politics.ownership.ClaimsOwnershipProvider;
import kmu.util.KmuStrings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the claims view's render rules: it is the faction view's look keyed off the vanilla claim
 * mechanic, so its grouping is identity, its ownership comes from the claims-only provider, and its
 * bloc styling and label delegate to the faction view. It offers no spotlight, so its selectable set
 * is always empty. Reproducing these here is what lets the shared pipeline read the claims view
 * through {@link kmu.maplayers.politicalmap.base.PoliticalMapView} without naming it.
 */
final class ClaimsViewTest {

    // The claims view ignores its grouping argument (its own grouping is always identity), so any
    // grouping stands in where the interface demands one.
    private static final OwnershipGrouping ANY_GROUPING = OwnershipGrouping.identity();

    @Nested
    class GetId {

        @Test
        void getIdIsTheSaveStableClaimsId() {
            assertThat(ClaimsView.INSTANCE.getId()).isEqualTo("claims");
        }
    }

    @Nested
    class GetSegmentLabelKey {

        @Test
        void getSegmentLabelKeyIsTheClaimsRadioLabel() {
            assertThat(ClaimsView.INSTANCE.getSegmentLabelKey())
                    .isEqualTo(KmuStrings.POLITICAL_MAP_CTL_CLAIMS);
        }
    }

    @Nested
    class ResolveGrouping {

        @Test
        void resolveGroupingReturnsTheIdentityGrouping() {
            // Claims group strictly by claiming faction with no alliance rollup, so the pipeline
            // resolves plain faction ownership.
            assertThat(ClaimsView.INSTANCE.resolveGrouping())
                    .isSameAs(OwnershipGrouping.identity());
        }
    }

    @Nested
    class ResolveOwnershipProvider {

        @Test
        void resolveOwnershipProviderReturnsTheClaimsOnlyProvider() {
            // Ownership is the claim mechanic itself, so the view supplies the claims-only provider
            // rather than inheriting the held-plus-claims default.
            assertThat(ClaimsView.INSTANCE.resolveOwnershipProvider())
                    .isSameAs(ClaimsOwnershipProvider.INSTANCE);
        }
    }

    @Nested
    class GetContentRevision {

        @Test
        void getContentRevisionIsInvariantAcrossAllianceChanges() {
            // The claims view samples no live input of its own - its market-inferred claims repaint on
            // the shared economy revision, not here - so an alliance forming or dissolving (which the
            // alliances view renders) must leave its contribution fixed and never churn this view.
            var before = ClaimsView.INSTANCE.getContentRevision();
            MapLayerRefresh.requestAllianceRefresh();

            assertThat(ClaimsView.INSTANCE.getContentRevision()).isEqualTo(before);
        }
    }

    @Nested
    class ShouldUseIndependentStyle {

        @Test
        void shouldUseIndependentStyleIsTrueForIndependentSpace() {
            // Delegated to the faction view: an independent claimant recedes to the muted style like
            // independent ground.
            assertThat(ClaimsView.INSTANCE.shouldUseIndependentStyle(
                    Factions.INDEPENDENT, ANY_GROUPING, BlocStyleAdjustment.NONE)).isTrue();
        }

        @Test
        void shouldUseIndependentStyleIsFalseForACoreFaction() {
            // A held claimant faction paints in full faction style, exactly as the faction view draws
            // it.
            assertThat(ClaimsView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony", ANY_GROUPING, BlocStyleAdjustment.NONE)).isFalse();
        }

        @Test
        void shouldUseIndependentStyleForwardsTheAdjustmentToTheFactionView() {
            // The adjustment argument must reach the delegate: a core faction the recede has
            // desaturated takes the independent style, so passing a desaturating adjustment (rather
            // than NONE) flips the result - pinning that the arg is forwarded, not dropped.
            assertThat(ClaimsView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony", ANY_GROUPING, new BlocStyleAdjustment(0.3, true))).isTrue();
        }
    }

    @Nested
    class ResolveBlocStyleAdjustment {

        @Test
        void resolveBlocStyleAdjustmentIsNoneForAnyBloc() {
            // The claims view adjusts no bloc, delegating the faction view's decision that a claimant
            // and independent space alike draw exactly as classified.
            assertThat(ClaimsView.INSTANCE.resolveBlocStyleAdjustment("hegemony", ANY_GROUPING))
                    .isEqualTo(BlocStyleAdjustment.NONE);
            assertThat(ClaimsView.INSTANCE.resolveBlocStyleAdjustment(
                    Factions.INDEPENDENT, ANY_GROUPING)).isEqualTo(BlocStyleAdjustment.NONE);
        }
    }

    @Nested
    class ResolveName {

        @Test
        void resolveNameReadsTheClaimingFactionsOwnName() {
            // A claim bloc id is a plain faction id, so the label is that faction's display name in
            // the player's chosen form - resolved through the faction view.
            var sectorMock = mock(SectorAPI.class);
            var factionMock = mock(FactionAPI.class);
            when(sectorMock.getFaction("hegemony")).thenReturn(factionMock);
            when(factionMock.getDisplayNameLong()).thenReturn("The Hegemony");

            assertThat(ClaimsView.INSTANCE.resolveName("hegemony", ANY_GROUPING, sectorMock,
                    FactionNameFormatChoice.FULL)).isEqualTo("The Hegemony");
        }

        @Test
        void resolveNameForwardsTheShortFormatToTheFactionView() {
            // The name-format argument must reach the delegate too: Short reads the faction's short
            // name rather than its long title, so asserting the short name pins that the format is
            // forwarded rather than defaulted.
            var sectorMock = mock(SectorAPI.class);
            var factionMock = mock(FactionAPI.class);
            when(sectorMock.getFaction("hegemony")).thenReturn(factionMock);
            when(factionMock.getDisplayName()).thenReturn("Hegemony");

            assertThat(ClaimsView.INSTANCE.resolveName("hegemony", ANY_GROUPING, sectorMock,
                    FactionNameFormatChoice.SHORT)).isEqualTo("Hegemony");
        }

        @Test
        void resolveNameIsNullWhenTheFactionDoesNotResolve() {
            var sectorMock = mock(SectorAPI.class);
            when(sectorMock.getFaction("ghost")).thenReturn(null);

            assertThat(ClaimsView.INSTANCE.resolveName("ghost", ANY_GROUPING, sectorMock,
                    FactionNameFormatChoice.FULL)).isNull();
        }
    }

    @Nested
    class ResolveSelectableBlocs {

        @Test
        void resolveSelectableBlocsIsEmptySoTheViewOffersNoSpotlight() {
            // Claim presence is not the market presence the shared picker derives its selectable set
            // from, so the claims view inherits the interface's no-spotlight default: the picker draws
            // nothing and a stale saved selection heals to none. It reads none of its arguments, so
            // this guards against a future accidental override reintroducing a spotlight.
            assertThat(ClaimsView.INSTANCE.resolveSelectableBlocs(null, null, false)).isEmpty();
        }
    }
}
