package kmu.conditions.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionOfferPolicyTest {

    @Nested
    class PlanetaryOnly {

        @Test
        void offersPlanetaryConditions() {
            var policy = KmuConditionOfferPolicy.planetaryOnly();

            assertThat(policy.isConditionOfferable(buildSpec("hot", true))).isTrue();
        }

        @Test
        void rejectsNonPlanetaryConditions() {
            var policy = KmuConditionOfferPolicy.planetaryOnly();

            assertThat(policy.isConditionOfferable(buildSpec("decivilized", false))).isFalse();
        }

        @Test
        void rejectsNullSpec() {
            var policy = KmuConditionOfferPolicy.planetaryOnly();

            assertThat(policy.isConditionOfferable(null)).isFalse();
        }
    }

    private static KmuConditionSpec buildSpec(String id, boolean planetary) {
        return new KmuConditionSpec(id, id, "graphics/icons/" + id + ".png", planetary);
    }
}
