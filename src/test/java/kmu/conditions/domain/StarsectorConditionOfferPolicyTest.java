package kmu.conditions.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StarsectorConditionOfferPolicyTest {

    @Nested
    class IsConditionOfferable {

        @Test
        void offersEveryConditionWhenTheToggleIsOn() {
            var policy = new StarsectorConditionOfferPolicy(() -> true);

            assertThat(policy.isConditionOfferable(buildSpec("hot", true))).isTrue();
            assertThat(policy.isConditionOfferable(buildSpec("decivilized", false))).isTrue();
        }

        @Test
        void offersOnlyPlanetaryConditionsWhenTheToggleIsOff() {
            var policy = new StarsectorConditionOfferPolicy(() -> false);

            assertThat(policy.isConditionOfferable(buildSpec("hot", true))).isTrue();
            assertThat(policy.isConditionOfferable(buildSpec("decivilized", false))).isFalse();
        }

        @Test
        void offersPlanetaryConditionsWithoutConsultingTheToggle() {
            // A planetary condition is always offerable, so the toggle must not be
            // read for it; a supplier that throws proves the short-circuit holds.
            var policy = new StarsectorConditionOfferPolicy(() -> {
                throw new IllegalStateException("toggle must not be read for planetary conditions");
            });

            assertThat(policy.isConditionOfferable(buildSpec("hot", true))).isTrue();
        }

        @Test
        void rejectsNullSpec() {
            var policy = new StarsectorConditionOfferPolicy(() -> true);

            assertThat(policy.isConditionOfferable(null)).isFalse();
        }
    }

    private static KmuConditionSpec buildSpec(String id, boolean planetary) {
        return new KmuConditionSpec(id, id, "graphics/icons/" + id + ".png", planetary);
    }
}
