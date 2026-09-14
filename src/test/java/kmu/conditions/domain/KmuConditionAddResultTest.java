package kmu.conditions.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionAddResultTest {

    @Nested
    class Added {

        @Test
        void exposesAddedResultState() {
            var result = KmuConditionAddResult.added("hot");

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.ADDED);
            assertThat(result.getConditionId()).contains("hot");
            assertThat(result.getMessage()).isEmpty();
            assertThat(result.getCause()).isEmpty();
            assertThat(result.getStatus().isMutationApplied()).isTrue();
            assertThat(result.getStatus()).isNotEqualTo(KmuConditionAddStatus.FAILED);
        }
    }

    @Nested
    class Failed {

        @Test
        void exposesFailedResultState() {
            var exception = new IllegalStateException("failed");

            var result = KmuConditionAddResult.failed("hot", "Failed to add.", exception);

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
            assertThat(result.getConditionId()).contains("hot");
            assertThat(result.getMessage()).contains("Failed to add.");
            assertThat(result.getCause()).contains(exception);
            assertThat(result.getStatus().isMutationApplied()).isFalse();
            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
        }
    }
}
