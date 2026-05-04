package kmu.conditions.ui.editor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionEditorOpenResultTest {
    @Test
    void openedResultIsOpenedWithNoMessage() {
        KmuConditionEditorOpenResult result = KmuConditionEditorOpenResult.opened();

        assertThat(result.getStatus()).isEqualTo(KmuConditionEditorOpenStatus.OPENED);
        assertThat(result.isOpened()).isTrue();
        assertThat(result.getCause()).isEmpty();
    }

    @Test
    void noMarketContextResultIsNotOpened() {
        KmuConditionEditorOpenResult result = KmuConditionEditorOpenResult.noMarketContext();

        assertThat(result.getStatus()).isEqualTo(KmuConditionEditorOpenStatus.NO_MARKET_CONTEXT);
        assertThat(result.isOpened()).isFalse();
        assertThat(result.getMessage()).isNotBlank();
    }

    @Test
    void unsupportedTargetUsesProvidedReason() {
        KmuConditionEditorOpenResult result =
                KmuConditionEditorOpenResult.unsupportedTarget("Station markets are not supported.");

        assertThat(result.getStatus()).isEqualTo(KmuConditionEditorOpenStatus.UNSUPPORTED_TARGET);
        assertThat(result.isOpened()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Station markets are not supported.");
    }

    @Test
    void failedResultExposesCauseAndIsNotOpened() {
        RuntimeException cause = new RuntimeException("something went wrong");

        KmuConditionEditorOpenResult result =
                KmuConditionEditorOpenResult.failed("Editor failed to open.", cause);

        assertThat(result.getStatus()).isEqualTo(KmuConditionEditorOpenStatus.FAILED);
        assertThat(result.isOpened()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Editor failed to open.");
        assertThat(result.getCause()).contains(cause);
    }
}
