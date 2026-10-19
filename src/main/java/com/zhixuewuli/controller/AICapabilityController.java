package com.zhixuewuli.controller;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AICapabilityController {
 private final AIBusinessProperties properties;
 private final com.zhixuewuli.common.metrics.AIMetrics metrics;
 @GetMapping("/capabilities")
 public Result<Map<String,Object>> capabilities(){
  boolean reports=properties.isEnabled()&&present(properties.getAliyunApiKey());
  boolean tutoring=properties.isEnabled()&&present(properties.getZhipuApiKey());
  return Result.success(Map.of("reports",reports,"tutoring",tutoring,
    "reportStatus",metrics.lastStatus("aliyun"),"tutoringStatus",metrics.lastStatus("zhipu"),
    "notice","开关与密钥仅表示已配置；运行状态取自最近一次实际调用，不会为检查状态额外调用模型。AI 建议需核实，请勿填写敏感信息。"));
 }
 private boolean present(String key){return key!=null&&!key.isBlank();}
}
