import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class SpikeExperimentSafetyTest {
    @Test void defaultInvocationCannotMakeModelCalls() throws Exception {
        var javaExecutable=Path.of(System.getProperty("java.home"),"bin","java").toString();
        var builder=new ProcessBuilder(javaExecutable,"-cp",System.getProperty("java.class.path"),"RagSpikeExperiment").redirectErrorStream(true);
        builder.environment().remove("ZHIPU_AI_API_KEY");builder.environment().remove("OPENAI_API_KEY");
        var process=builder.start();assertTrue(process.waitFor(3,TimeUnit.SECONDS));
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0,process.exitValue(),output);
        assertTrue(output.contains("Model calls disabled"),output);
    }
    @Test void retrievalOnlyReportsSourcesAndProvenanceWithoutClaimingGeneration() throws Exception {
        var directory=java.nio.file.Files.createTempDirectory("physics-rag-probe-");
        var output=directory.resolve("results.jsonl");
        try {
            var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                    "-cp",System.getProperty("java.class.path"),"RagSpikeExperiment","--retrieval-only",
                    "--fixture-dir","fixtures","--output",output.toString()).redirectErrorStream(true);
            builder.environment().remove("ZHIPU_AI_API_KEY");
            var process=builder.start();assertTrue(process.waitFor(30,TimeUnit.SECONDS));
            assertEquals(0,process.exitValue(),new String(process.getInputStream().readAllBytes()));
            var records=java.nio.file.Files.readAllLines(output);assertEquals(10,records.size());
            var json=new com.fasterxml.jackson.databind.ObjectMapper();
            for(String line:records) {
                var record=json.readTree(line);
                assertEquals("NOT_RUN",record.path("generationMode").asText());
                assertTrue(record.path("codeSha").asText().matches("[0-9a-f]{40}"));
                assertTrue(record.path("corpusVersion").asText().matches("sha256:[0-9a-f]{64}"));
                assertFalse(record.has("answer"));
                assertTrue(java.util.List.of("retrieved","insufficient").contains(record.path("status").asText()));
            }
        } finally {java.nio.file.Files.deleteIfExists(output);java.nio.file.Files.deleteIfExists(directory);}
    }
}
