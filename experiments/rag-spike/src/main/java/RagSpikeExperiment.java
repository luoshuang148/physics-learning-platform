import com.fasterxml.jackson.databind.*;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Bounded, explicit live developer experiment; no student data or product integration. */
public final class RagSpikeExperiment {
    private static final ObjectMapper JSON=new ObjectMapper();
    public static void main(String[] args) throws Exception {
        var flags=Arrays.asList(args);
        boolean live=flags.contains("--allow-model-calls"), retrievalOnly=flags.contains("--retrieval-only");
        if(!live && !retrievalOnly){System.out.println("Model calls disabled. Use --retrieval-only or explicitly --allow-model-calls.");return;}
        Path output=Path.of(option(flags,"--output", ".local/ai-evaluation/g0-results.jsonl"));
        if(Files.exists(output))throw new IllegalArgumentException("Output already exists; choose a new file");
        Path fixtures=Path.of(option(flags,"--fixture-dir","experiments/rag-spike/fixtures"));
        String model=System.getenv().getOrDefault("SPIKE_CHAT_MODEL","glm-4-flash-250414");
        URI base=URI.create(System.getenv().getOrDefault("SPIKE_BASE_URL","https://open.bigmodel.cn/api/paas/v4"));
        String key=System.getenv().getOrDefault("ZHIPU_AI_API_KEY","");
        if(live && key.isBlank())throw new IllegalArgumentException("Missing ZHIPU_AI_API_KEY environment variable");
        if(live && !base.getScheme().equals("https"))throw new IllegalArgumentException("Live endpoint must use HTTPS");
        int maxRequests=Integer.parseInt(option(flags,"--max-requests","20"));
        if(maxRequests<1 || maxRequests>20)throw new IllegalArgumentException("Request limit must be 1..20");
        double threshold=Double.parseDouble(option(flags,"--minimum-score","0.60"));
        var requestCount=new AtomicInteger();
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.createFile(output,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try(var writer=Files.newBufferedWriter(output)) {
            if(flags.contains("--probe-tools")) {
                if(!live)throw new IllegalArgumentException("Tool probe requires --allow-model-calls");
                if(maxRequests<4)throw new IllegalArgumentException("Two adapters require a four-request tool budget");
                for(boolean sdk:List.of(false,true)) {
                    var result=SpikeToolProbe.run(sdk,base,key,model);
                    writer.write(JSON.writeValueAsString(Map.of("adapter",sdk?"sdk":"http","capturedAt",Instant.now().toString(),"toolResult",result)));writer.newLine();writer.flush();
                }
                System.out.println("Saved two synthetic tool round trips (4 model requests).");return;
            }
            var embeddingModel=new BgeSmallZhV15QuantizedEmbeddingModel();
            var cache=new HashMap<String,float[]>();
            RagSpike.Embeddings embedding=(text,query)->cache.computeIfAbsent((query?"q:":"d:")+text,k->
                    embeddingModel.embed((query?"为这个句子生成表示以用于检索相关文章：":"")+text).content().vector());
            String document=Files.readString(fixtures.resolve("physics-notes.md"));
            String corpusVersion="sha256:"+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest((document+Files.readString(fixtures.resolve("questions.jsonl"))).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var git=new ProcessBuilder("git","rev-parse","HEAD").start();
            if(!git.waitFor(3,java.util.concurrent.TimeUnit.SECONDS) || git.exitValue()!=0)throw new IllegalStateException("Cannot determine experiment code revision");
            String codeSha=new String(git.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).strip();
            List<JsonNode> cases=new ArrayList<>();
            for(String line:Files.readAllLines(fixtures.resolve("questions.jsonl")))if(!line.isBlank())cases.add(JSON.readTree(line));
            int failures=0;
            for(String adapterName:retrievalOnly?List.of("retrieval-only"):List.of("http","sdk")) {
                RagSpike.ModelAdapter adapter=retrievalOnly?(p,c)->new RagSpike.ModelReply("[S1]",null,null)
                        :adapterName.equals("http")?SpikeAdapters.http(base,key,model):SpikeAdapters.sdk(base,key,model);
                RagSpike.ModelAdapter bounded=(prompt,cancelled)->{
                    if(!retrievalOnly && requestCount.incrementAndGet()>maxRequests)throw new IllegalStateException("Model request budget exhausted");
                    return adapter.complete(prompt,cancelled);
                };
                for(var testCase:cases) {
                    var record=new LinkedHashMap<String,Object>();
                    record.put("caseId",testCase.path("id").asText());record.put("adapter",adapterName);
                    record.put("codeSha",codeSha);record.put("corpusVersion",corpusVersion);record.put("promptVersion","g0-evidence-v2");
                    record.put("generationMode",retrievalOnly?"NOT_RUN":"MODEL");
                    record.put("capturedAt",Instant.now().toString());record.put("model",retrievalOnly?null:model);
                    record.put("embeddingModel","bge-small-zh-v1.5-quantized");record.put("embeddingDimensions",512);
                    record.put("minimumScore",threshold);record.put("reviewStatus","pending");
                    record.put("expectedSourceIds",testCase.path("expectedSourceIds"));
                    try {
                        var result=new RagSpike(embedding,bounded,threshold).run(testCase.path("question").asText(),document,new AtomicBoolean());
                        record.put("status",retrievalOnly && result.status().equals("answered")?"retrieved":result.status());record.put("passages",result.passages());record.put("elapsedMs",result.elapsedMs());
                        if(!retrievalOnly){record.put("answer",result.answer());record.put("sourceIds",result.sourceIds());record.put("usage",result.usage());}
                    } catch(Exception error){record.put("status","failed");record.put("errorType",error.getClass().getSimpleName());failures++;}
                    writer.write(JSON.writeValueAsString(record));writer.newLine();writer.flush();
                    System.out.println(testCase.path("id").asText()+" "+adapterName+" "+record.get("status"));
                }
            }
            System.out.println("Saved "+output+"; model requests="+requestCount.get()+"; failures="+failures+"; human review pending.");
            if(failures>0)throw new IllegalStateException("Some experiment cases failed; inspect private results");
        }
    }
    private static String option(List<String> args,String name,String fallback){
        int index=args.indexOf(name);
        if(index<0)return fallback;
        if(index+1>=args.size() || args.get(index+1).startsWith("--"))throw new IllegalArgumentException("Missing value for "+name);
        return args.get(index+1);
    }
}
