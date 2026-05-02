package kmu.conditions.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionAddResultTest {
    @Test
    void exposesAddedResultState() {
        KmuConditionAddResult result = KmuConditionAddResult.added("hot");

        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.ADDED);
        assertThat(result.getConditionId()).contains("hot");
        assertThat(result.getMessage()).isEmpty();
        assertThat(result.getCause()).isEmpty();
        assertThat(result.getStatus().isMutationApplied()).isTrue();
        assertThat(result.getStatus()).isNotEqualTo(KmuConditionAddStatus.FAILED);
    }

    @Test
    void exposesFailedResultState() {
        RuntimeException exception = new IllegalStateException("failed");

        KmuConditionAddResult result = KmuConditionAddResult.failed("hot", "Failed to add.", exception);

        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
        assertThat(result.getConditionId()).contains("hot");
        assertThat(result.getMessage()).contains("Failed to add.");
        assertThat(result.getCause()).contains(exception);
        assertThat(result.getStatus().isMutationApplied()).isFalse();
        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
    }
}
