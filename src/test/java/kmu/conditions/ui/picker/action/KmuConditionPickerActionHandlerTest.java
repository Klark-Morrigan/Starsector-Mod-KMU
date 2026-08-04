package kmu.conditions.ui.picker.action;

import kmu.conditions.domain.KmuConditionAddStatus;
import kmu.conditions.domain.KmuConditionRepository;
import kmu.conditions.domain.KmuConditionService;
import kmu.conditions.domain.KmuConditionSpec;
import kmu.conditions.domain.KmuEditableMarket;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntryState;
import kmu.conditions.ui.picker.model.KmuConditionPickerModelFactory;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionPickerActionHandlerTest {

    @Nested
    class Handle {

        @Test
        void addsAbsentConditionRefreshesModelAndShowsFeedback() {
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    buildSpec("hot", "Hot", true)));
            var marketFake = new EditableMarketFake();
            var handler = new KmuConditionPickerActionHandler(
                    service,
                    new KmuConditionPickerModelFactory(service),
                    marketFake);
            var entry = handler.getModel().getEntries().get(0);

            var result = handler.handle(KmuConditionPickerAction.fromEntry(entry));

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.ADDED);
            assertThat(handler.getModel().getEntries())
                    .extracting(KmuConditionPickerEntry::getState)
                    .containsExactly(KmuConditionPickerEntryState.PRESENT);
            assertThat(handler.getFeedback())
                    .hasValueSatisfying(feedback -> {
                        assertThat(feedback.getStatus()).isEqualTo(KmuConditionAddStatus.ADDED);
                        assertThat(feedback.getMessage()).isEqualTo("Added condition: hot");
                        assertThat(feedback.isFailure()).isFalse();
                    });
            assertThat(marketFake.conditionIds).containsExactly("hot");
            assertThat(marketFake.calls).containsExactly("add:hot", "surveyed:hot", "reapply");
        }

        @Test
        void presentConditionActionDoesNotMutateMarket() {
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    buildSpec("hot", "Hot", true)));
            var marketFake = new EditableMarketFake("hot");
            var handler = new KmuConditionPickerActionHandler(
                    service,
                    new KmuConditionPickerModelFactory(service),
                    marketFake);
            var originalModel = handler.getModel();
            var entry = handler.getModel().getEntries().get(0);

            var result = handler.handle(KmuConditionPickerAction.fromEntry(entry));

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.ALREADY_PRESENT);
            assertThat(handler.getModel()).isSameAs(originalModel);
            assertThat(handler.getFeedback()).isEmpty();
            assertThat(marketFake.conditionIds).containsExactly("hot");
            assertThat(marketFake.calls).isEmpty();
        }

        @Test
        void failedAddRefreshesModelAndShowsFailureFeedback() {
            var exception = new IllegalStateException("add failed");
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    buildSpec("hot", "Hot", true)));
            var marketFake = new EditableMarketFake()
                    .failAddCondition(exception);
            var handler = new KmuConditionPickerActionHandler(
                    service,
                    new KmuConditionPickerModelFactory(service),
                    marketFake);
            var entry = handler.getModel().getEntries().get(0);

            var result = handler.handle(KmuConditionPickerAction.fromEntry(entry));

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
            assertThat(handler.getModel().getEntries())
                    .extracting(KmuConditionPickerEntry::getState)
                    .containsExactly(KmuConditionPickerEntryState.ABSENT);
            assertThat(handler.getFeedback())
                    .hasValueSatisfying(feedback -> {
                        assertThat(feedback.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
                        assertThat(feedback.getMessage()).isEqualTo("Failed to add condition: hot");
                        assertThat(feedback.isFailure()).isTrue();
                    });
            assertThat(marketFake.conditionIds).isEmpty();
            assertThat(marketFake.calls).isEmpty();
        }
    }

    private static KmuConditionSpec buildSpec(String id, String name, boolean planetary) {
        return new KmuConditionSpec(id, name, "graphics/icons/" + id + ".png", planetary);
    }

    private static final class ConditionRepositoryFake implements KmuConditionRepository {
        private final LinkedHashMap<String, KmuConditionSpec> specsById = new LinkedHashMap<>();

        private ConditionRepositoryFake(KmuConditionSpec... specs) {
            for (var spec : specs) {
                specsById.put(spec.getId(), spec);
            }
        }

        @Override
        public List<KmuConditionSpec> getAllConditionSpecs() {
            return new ArrayList<>(specsById.values());
        }

        @Override
        public Optional<KmuConditionSpec> findConditionSpec(String conditionId) {
            return Optional.ofNullable(specsById.get(conditionId));
        }
    }

    private static final class EditableMarketFake implements KmuEditableMarket {
        private final LinkedHashSet<String> conditionIds = new LinkedHashSet<>();
        private final List<String> calls = new ArrayList<>();
        private RuntimeException addConditionException;

        private EditableMarketFake(String... conditionIds) {
            for (var conditionId : conditionIds) {
                this.conditionIds.add(conditionId);
            }
        }

        private EditableMarketFake failAddCondition(RuntimeException exception) {
            this.addConditionException = exception;
            return this;
        }

        @Override
        public Set<String> getConditionIds() {
            return new LinkedHashSet<>(conditionIds);
        }

        @Override
        public boolean hasCondition(String conditionId) {
            return conditionIds.contains(conditionId);
        }

        @Override
        public void addCondition(String conditionId) {
            if (addConditionException != null) {
                throw addConditionException;
            }
            calls.add("add:" + conditionId);
            conditionIds.add(conditionId);
        }

        @Override
        public void markConditionSurveyed(String conditionId) {
            calls.add("surveyed:" + conditionId);
        }

        @Override
        public void reapplyConditions() {
            calls.add("reapply");
        }
    }
}
