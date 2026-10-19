package kmu.conditions.ui.picker.action;

import kmu.conditions.domain.KmuConditionAddResult;
import kmu.conditions.domain.KmuConditionAddStatus;
import kmu.util.KmuStringsSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionPickerFeedbackTests {

    @BeforeEach
    void installStrings() {
        KmuStringsSettingsFake.installSettings();
    }

    @AfterEach
    void clearStrings() {
        KmuStringsSettingsFake.clearSettings();
    }

    @Nested
    class From {

        @Test
        void mapsAddResultsToUserFacingFeedback() {

            assertThat(KmuConditionPickerFeedback.from(KmuConditionAddResult.added("hot")).getMessage())
                .isEqualTo("Added condition: hot");
            assertThat(KmuConditionPickerFeedback.from(KmuConditionAddResult.alreadyPresent("hot")).getMessage())
                .isEqualTo("Already present: hot");
            assertThat(KmuConditionPickerFeedback.from(KmuConditionAddResult.conditionNotFound("hot")).getMessage())
                .isEqualTo("Condition not found: hot");
            assertThat(KmuConditionPickerFeedback.from(KmuConditionAddResult.notOfferable("hot")).getMessage())
                .isEqualTo("Not a placeable condition: hot");

            assertThat(KmuConditionPickerFeedback.from(
                    KmuConditionAddResult.failed("hot", "failed", new IllegalStateException("failed")))
                .getMessage())
                .isEqualTo("Failed to add condition: hot");
        }

        @Test
        void namesAnUnknownConditionWhereTheResultCarriesNoId() {

            assertThat(KmuConditionPickerFeedback.from(KmuConditionAddResult.conditionNotFound(null)).getMessage())
                .isEqualTo("Condition not found: unknown condition");
        }

        @Test
        void marksOnlyFailedResultsAsFailure() {

            assertThat(KmuConditionPickerFeedback.from(KmuConditionAddResult.added("hot")).isFailure())
                .isFalse();

            var failed = KmuConditionPickerFeedback.from(
                KmuConditionAddResult.failed("hot", "failed", new IllegalStateException("failed")));

            assertThat(failed.getStatus())
                .isEqualTo(KmuConditionAddStatus.FAILED);
            assertThat(failed.isFailure())
                .isTrue();
        }
    }
}
