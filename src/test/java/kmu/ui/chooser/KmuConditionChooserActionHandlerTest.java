package kmu.ui.chooser;

import kmu.conditions.KmuConditionAddResult;
import kmu.conditions.KmuConditionAddStatus;
import kmu.conditions.KmuConditionRepository;
import kmu.conditions.KmuConditionService;
import kmu.conditions.KmuConditionSpec;
import kmu.conditions.KmuEditableMarket;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionChooserActionHandlerTest {
    @Test
    void addsAbsentConditionRefreshesModelAndShowsFeedback() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                spec("hot", "Hot", true)));
        FakeEditableMarket market = new FakeEditableMarket();
        KmuConditionChooserActionHandler handler = new KmuConditionChooserActionHandler(
                service,
                new KmuConditionChooserModelFactory(service),
                market);
        KmuConditionChooserEntry entry = handler.getModel().getEntries().get(0);

        KmuConditionAddResult result = handler.handle(KmuConditionChooserAction.fromEntry(entry));

        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.ADDED);
        assertThat(handler.getModel().getEntries())
                .extracting(KmuConditionChooserEntry::getState)
                .containsExactly(KmuConditionChooserEntryState.PRESENT);
        assertThat(handler.getFeedback())
                .hasValueSatisfying(feedback -> {
                    assertThat(feedback.getStatus()).isEqualTo(KmuConditionAddStatus.ADDED);
                    assertThat(feedback.getMessage()).isEqualTo("Added condition: hot");
                    assertThat(feedback.isFailure()).isFalse();
                });
        assertThat(market.conditionIds).containsExactly("hot");
        assertThat(market.calls).containsExactly("add:hot", "surveyed:hot", "reapply");
    }

    @Test
    void presentConditionActionDoesNotMutateMarket() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                spec("hot", "Hot", true)));
        FakeEditableMarket market = new FakeEditableMarket("hot");
        KmuConditionChooserActionHandler handler = new KmuConditionChooserActionHandler(
                service,
                new KmuConditionChooserModelFactory(service),
                market);
        KmuConditionChooserEntry entry = handler.getModel().getEntries().get(0);

        KmuConditionAddResult result = handler.handle(KmuConditionChooserAction.fromEntry(entry));

        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.ALREADY_PRESENT);
        assertThat(handler.getFeedback())
                .hasValueSatisfying(feedback -> {
                    assertThat(feedback.getStatus()).isEqualTo(KmuConditionAddStatus.ALREADY_PRESENT);
                    assertThat(feedback.getMessage()).isEqualTo("Already present: hot");
                });
        assertThat(market.conditionIds).containsExactly("hot");
        assertThat(market.calls).isEmpty();
    }

    @Test
    void failedAddRefreshesModelAndShowsFailureFeedback() {
        RuntimeException exception = new IllegalStateException("add failed");
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                spec("hot", "Hot", true)));
        FakeEditableMarket market = new FakeEditableMarket()
                .failAddCondition(exception);
        KmuConditionChooserActionHandler handler = new KmuConditionChooserActionHandler(
                service,
                new KmuConditionChooserModelFactory(service),
                market);
        KmuConditionChooserEntry entry = handler.getModel().getEntries().get(0);

        KmuConditionAddResult result = handler.handle(KmuConditionChooserAction.fromEntry(entry));

        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
        assertThat(handler.getModel().getEntries())
                .extracting(KmuConditionChooserEntry::getState)
                .containsExactly(KmuConditionChooserEntryState.ABSENT);
        assertThat(handler.getFeedback())
                .hasValueSatisfying(feedback -> {
                    assertThat(feedback.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
                    assertThat(feedback.getMessage()).isEqualTo("Failed to add condition: hot");
                    assertThat(feedback.isFailure()).isTrue();
                });
        assertThat(market.conditionIds).isEmpty();
        assertThat(market.calls).isEmpty();
    }

    private static KmuConditionSpec spec(String id, String name, boolean planetary) {
        return new KmuConditionSpec(id, name, "graphics/icons/" + id + ".png", planetary);
    }

    private static final class FakeConditionRepository implements KmuConditionRepository {
        private final LinkedHashMap<String, KmuConditionSpec> specsById = new LinkedHashMap<>();

        private FakeConditionRepository(KmuConditionSpec... specs) {
            for (KmuConditionSpec spec : specs) {
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

    private static final class FakeEditableMarket implements KmuEditableMarket {
        private final LinkedHashSet<String> conditionIds = new LinkedHashSet<>();
        private final List<String> calls = new ArrayList<>();
        private RuntimeException addConditionException;

        private FakeEditableMarket(String... conditionIds) {
            for (String conditionId : conditionIds) {
                this.conditionIds.add(conditionId);
            }
        }

        private FakeEditableMarket failAddCondition(RuntimeException exception) {
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
