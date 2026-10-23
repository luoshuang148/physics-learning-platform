import org.junit.jupiter.api.Test;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class RagSpikeContractTest {
    static final String DOCUMENT = "## FORCE\n合外力等于质量乘加速度。\n\n## ENERGY\n只有重力做功时机械能守恒。";
    // Hand-derived unit vectors isolate retrieval from the external embedding implementation.
    private float[] embed(String text, boolean query) {
        if (text.contains("机械能") || text.equals("查能量条件")) return new float[]{0, 1, 0};
        if (text.contains("合外力") || text.equals("查受力关系")) return new float[]{1, 0, 0};
        return new float[]{0, 0, 1};
    }
    private RagSpike spike() {
        return new RagSpike(this::embed, (p, c) -> new RagSpike.ModelReply("解释 [S1]", 20, 5), .8);
    }
    @Test void questionSelectsRelevantPassageAndSendsItToTheModel() throws Exception {
        var prompt = new AtomicReference<RagSpike.Prompt>();
        var spike = new RagSpike(this::embed, (p, c) -> {
            prompt.set(p); return new RagSpike.ModelReply("机械能守恒有条件 [S1]", 20, 5);
        }, .8);
        var result = spike.run("查能量条件", DOCUMENT, new AtomicBoolean());
        assertEquals("answered", result.status());
        assertEquals("ENERGY", result.passages().getFirst().id());
        assertEquals(java.util.List.of("S1"), result.sourceIds());
        assertTrue(prompt.get().user().contains("只有重力做功时机械能守恒"));
        assertFalse(prompt.get().user().contains("合外力等于"));
        assertEquals(20, result.usage().inputTokens());
    }
    @Test void changingTheQuestionChangesTheRetrievedSource() throws Exception {
        var result = spike().run("查受力关系", DOCUMENT, new AtomicBoolean());
        assertEquals(1, result.passages().size());
        assertEquals("FORCE", result.passages().getFirst().id());
    }
    @Test void insufficientEvidenceSkipsGenerationRatherThanInventingSources() throws Exception {
        var calls = new AtomicInteger();
        var spike = new RagSpike(this::embed, (p, c) -> {
            calls.incrementAndGet(); return new RagSpike.ModelReply("invented", null, null);
        }, .8);
        var result = spike.run("太阳系人口", DOCUMENT, new AtomicBoolean());
        assertEquals("insufficient", result.status());
        assertTrue(result.sourceIds().isEmpty());
        assertEquals(0, calls.get());
    }
    @Test void unknownCitationIsRejected() {
        var spike = new RagSpike(this::embed, (p, c) -> new RagSpike.ModelReply("结论 [S99]", 10, 5), .8);
        assertThrows(IllegalArgumentException.class, () -> spike.run("查能量条件", DOCUMENT, new AtomicBoolean()));
    }
    @Test void cancelledRequestDoesNotStartRetrievalOrGeneration() {
        assertThrows(CancellationException.class, () -> spike().run("查能量条件", DOCUMENT, new AtomicBoolean(true)));
    }
    @Test void emptyQuestionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> spike().run(" ", DOCUMENT, new AtomicBoolean()));
    }
}
