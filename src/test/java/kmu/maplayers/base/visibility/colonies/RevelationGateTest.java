package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.colonies.Colony;
import kmlib.testfixtures.starsector.markets.colonies.ColonyMarketFixture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.ACADEMY_ENTITY_ID;
import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.clearRegistrations;
import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.registerTheAcademy;
import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.standOnEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what each {@link RevelationGate} covers, and which colonies are gated by any of them at
 * all - the second being what decides whose observations are worth writing down. The cases live in
 * a {@link Nested} group per method so the suite reports as a per-method tree, and the colonies
 * they are posed against are {@link ColonyMarketFixture}'s.
 *
 * <p>Pinned apart from the rule that applies them because the two gates read different facts -
 * one the kind of place, the other whether the market conceals itself - and a gate quietly
 * covering the other's shape would hold back a colony no setting was asked to hide, while every
 * case in {@link ColonyKnowledgeTest} that poses both gates together went on passing.
 *
 * <p>The kind is stated beside the colony rather than resolved off the market, these cases being
 * about what a gate does with a kind rather than about how one is read - which is
 * {@link ColonyKindTest}'s.
 *
 * <p>One case here is about a fact the gates deliberately do not read: whether a concealment is
 * public knowledge ({@link OpenlyKnownColonyRegistry}). That excuses a word on a hover box and must
 * never widen what the player is shown, so it is pinned where folding the two into one flag would
 * first show.
 */
final class RevelationGateTest {

    @AfterEach
    void clearOpenlyKnownColonies() {
        clearRegistrations();
    }

    @Nested
    class CoversColony {

        @Test
        void space_derelicts_covers_a_derelict() {

            assertThat(RevelationGate.SPACE_DERELICTS.coversColony(
                    buildColony(ColonyMarketFixture.buildDerelictStation()),
                    ColonyKind.SPACE_DERELICT))
                .isTrue();
        }

        @Test
        void space_derelicts_passes_over_a_colony_somebody_lives_on() {

            assertThat(RevelationGate.SPACE_DERELICTS.coversColony(
                    buildColony(ColonyMarketFixture.buildVisibleColony("hegemony")),
                    ColonyKind.COLONY))
                .isFalse();
        }

        @Test
        void space_derelicts_passes_over_a_concealed_colony() {
            // The other gate's shape. A derelict gate that covered concealment would hide a
            // raided pirate base for the player who raided it.
            assertThat(RevelationGate.SPACE_DERELICTS.coversColony(
                    buildColony(ColonyMarketFixture.buildFoundConcealedColony("pirates")),
                    ColonyKind.COLONY))
                .isFalse();
        }

        @Test
        void space_derelicts_passes_over_a_station_a_faction_keeps() {
            // A kept station wears the derelict condition and is somebody's, so the kind read
            // parts it from the hulk and this gate is not about it - it answers to concealment
            // alone, exactly as an ordinary colony does.
            assertThat(RevelationGate.SPACE_DERELICTS.coversColony(
                    buildColony(ColonyMarketFixture.buildOutpost("hegemony")),
                    ColonyKind.OUTPOST))
                .isFalse();
        }

        @Test
        void hidden_colonies_covers_a_concealed_colony() {

            assertThat(RevelationGate.HIDDEN_COLONIES.coversColony(
                    buildColony(ColonyMarketFixture.buildFoundConcealedColony("rat_exotech")),
                    ColonyKind.COLONY))
                .isTrue();
        }

        @Test
        void hidden_colonies_passes_over_a_colony_held_in_the_open() {

            assertThat(RevelationGate.HIDDEN_COLONIES.coversColony(
                    buildColony(ColonyMarketFixture.buildVisibleColony("hegemony")),
                    ColonyKind.COLONY))
                .isFalse();
        }

        @Test
        void hidden_colonies_passes_over_a_derelict_nothing_conceals() {
            // The ordinary derelict: open, and covered by the other gate alone. Concealment and
            // kind are separate facts, and this is the case that says so.
            assertThat(RevelationGate.HIDDEN_COLONIES.coversColony(
                    buildColony(ColonyMarketFixture.buildDerelictStation()),
                    ColonyKind.SPACE_DERELICT))
                .isFalse();
        }
    }

    @Nested
    class IsGatedColony {

        @Test
        void reports_a_derelict_as_gated() {

            assertThat(RevelationGate.isGatedColony(
                    buildColony(ColonyMarketFixture.buildDerelictStation()),
                    ColonyKind.SPACE_DERELICT))
                .isTrue();
        }

        @Test
        void reports_a_concealed_colony_as_gated() {

            assertThat(RevelationGate.isGatedColony(
                    buildColony(ColonyMarketFixture.buildFoundConcealedColony("pirates")),
                    ColonyKind.COLONY))
                .isTrue();
        }

        @Test
        void reports_a_concealed_colony_the_sector_openly_points_at_as_gated() {
            // The landmark exemption excuses one word on a hover box and touches nothing here. A
            // single flag serving both is the obvious-looking simplification, and it would open the
            // Academy to a player who has never been to Galatia - the very market the settled route
            // was designed around.
            var academy = standOnEntity(
                ColonyMarketFixture.buildFoundConcealedColony("independent"),
                ACADEMY_ENTITY_ID);

            registerTheAcademy();

            assertThat(RevelationGate.isGatedColony(buildColony(academy), ColonyKind.COLONY))
                .isTrue();
        }

        @Test
        void reports_a_colony_held_in_the_open_as_ungated() {
            // The shape the register must not fill up with. An open colony the economy lists is
            // permanently in the sector's own sight, so an observation of one answers nothing.
            assertThat(RevelationGate.isGatedColony(
                    buildColony(ColonyMarketFixture.buildVisibleColony("hegemony")),
                    ColonyKind.COLONY))
                .isFalse();
        }

        @Test
        void reports_an_open_station_a_faction_keeps_as_ungated() {
            // Neither gate is about it: its owner parts it from the hulk, and nothing conceals it.
            assertThat(RevelationGate.isGatedColony(
                    buildColony(ColonyMarketFixture.buildOutpost("hegemony")),
                    ColonyKind.OUTPOST))
                .isFalse();
        }

        @Test
        void reports_no_gate_where_there_is_no_colony_to_test() {

            assertThat(RevelationGate.isGatedColony(null, ColonyKind.SPACE_DERELICT))
                .isFalse();
        }
    }

    // The colony a gate is asked about. Listed by the economy throughout: which listing a market
    // was found in reaches no gate, so varying it would say a case turned on it.
    private static Colony buildColony(MarketAPI market) {
        return new Colony(market, true);
    }
}
