package kmu.conditions.ui.editor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionEditorOpenResultTest {

    @Nested
    class Opened {

        @Test
        void openedResultIsOpenedWithNoMessage() {
            var result = KmuConditionEditorOpenResult.opened();

            assertThat(result.getStatus()).isEqualTo(KmuConditionEditorOpenStatus.OPENED);
            assertThat(result.isOpened()).isTrue();
            assertThat(result.getCause()).isEmpty();
        }
    }

    @Nested
    class NoMarketContext {

        @Test
        void noMarketContextResultIsNotOpened() {
            var result = KmuConditionEditorOpenResult.noMarketContext();

            assertThat(result.getStatus()).isEqualTo(KmuConditionEditorOpenStatus.NO_MARKET_CONTEXT);
            assertThat(result.isOpened()).isFalse();
            assertThat(result.getMessage()).isNotBlank();
        }
    }

    @Nested
    class UnsupportedTarget {

        @Test
        void unsupportedTargetUsesProvidedReason() {
            var result =
                    KmuConditionEditorOpenResult.unsupportedTarget("Station markets are not supported.");

            assertThat(result.getStatus()).isEqualTo(KmuConditionEditorOpenStatus.UNSUPPORTED_TARGET);
            assertThat(result.isOpened()).isFalse();
            assertThat(result.getMessage()).isEqualTo("Station markets are not supported.");
        }
    }

    @Nested
    class Failed {

        @Test
        void failedResultExposesCauseAndIsNotOpened() {
            var cause = new RuntimeException("something went wrong");

            var result =
                    KmuConditionEditorOpenResult.failed("Editor failed to open.", cause);

            assertThat(result.getStatus()).isEqualTo(KmuConditionEditorOpenStatus.FAILED);
            assertThat(result.isOpened()).isFalse();
            assertThat(result.getMessage()).isEqualTo("Editor failed to open.");
            assertThat(result.getCause()).contains(cause);
        }
    }
}
