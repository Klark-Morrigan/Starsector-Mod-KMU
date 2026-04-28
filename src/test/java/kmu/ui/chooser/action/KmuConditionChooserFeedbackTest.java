package kmu.ui.chooser.action;

import kmu.conditions.KmuConditionAddResult;
import kmu.conditions.KmuConditionAddStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionChooserFeedbackTest {
    @Test
    void mapsAddResultsToUserFacingFeedback() {
        assertThat(KmuConditionChooserFeedback.from(KmuConditionAddResult.added("hot")).getMessage())
                .isEqualTo("Added condition: hot");
        assertThat(KmuConditionChooserFeedback.from(KmuConditionAddResult.alreadyPresent("hot")).getMessage())
                .isEqualTo("Already present: hot");
        assertThat(KmuConditionChooserFeedback.from(KmuConditionAddResult.conditionNotFound("hot")).getMessage())
                .isEqualTo("Condition not found: hot");
        assertThat(KmuConditionChooserFeedback.from(KmuConditionAddResult.notPlanetary("hot")).getMessage())
                .isEqualTo("Not a planetary condition: hot");
        assertThat(KmuConditionChooserFeedback.from(
                        KmuConditionAddResult.failed("hot", "failed", new IllegalStateException("failed")))
                .getMessage())
                .isEqualTo("Failed to add condition: hot");
    }

    @Test
    void marksOnlyFailedResultsAsFailure() {
        assertThat(KmuConditionChooserFeedback.from(KmuConditionAddResult.added("hot")).isFailure())
                .isFalse();
        KmuConditionChooserFeedback failed = KmuConditionChooserFeedback.from(
                KmuConditionAddResult.failed("hot", "failed", new IllegalStateException("failed")));

        assertThat(failed.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
        assertThat(failed.isFailure()).isTrue();
    }
}
