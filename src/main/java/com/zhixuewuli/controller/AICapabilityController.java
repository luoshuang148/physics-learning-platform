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
 @GetMapping("/capabilities")
 public Result<Map<String,Object>> capabilities(){
  boolean reports=properties.isEnabled()&&present(properties.getAliyunApiKey());
  boolean tutoring=properties.isEnabled()&&present(properties.getZhipuApiKey());
  return Result.success(Map.of("reports",reports,"tutoring",tutoring,"notice","AI 建议仅供学习参考，需核实。问题和作答会发送至模型服务，请勿填写个人敏感信息。"));
 }
 private boolean present(String key){return key!=null&&!key.isBlank();}
}
