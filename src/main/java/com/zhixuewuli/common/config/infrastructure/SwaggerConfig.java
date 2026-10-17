package com.zhixuewuli.common.config.infrastructure;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger API文档配置类
 * 
 * 配置OpenAPI 3.0规范的API文档生成，
 * 为开发者提供完整的接口文档和在线测试功能。
 * 
 * <p>
 * 配置内容：
 * </p>
 * <ul>
 * <li>API文档基本信息</li>
 * <li>联系人信息</li>
 * <li>版本管理</li>
 * <li>描述信息</li>
 * </ul>
 * 
 * <p>
 * 使用说明：
 * </p>
 * <ul>
 * <li>访问/swagger-ui.html查看交互式文档</li>
 * <li>访问/v3/api-docs获取JSON格式的API定义</li>
 * <li>支持在线接口测试和调试</li>
 * <li>自动生成请求示例和响应格式</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see OpenAPI OpenAPI 3.0规范
 * @see Info API基本信息配置
 */
@Configuration
public class SwaggerConfig {

    /**
     * 配置自定义OpenAPI信息
     * 
     * 定义API文档的基本信息，包括标题、描述、版本和联系人。
     * 
     * @return OpenAPI 配置好的OpenAPI对象
     * 
     * @implSpec 设置中文标题和描述
     * @implSpec 包含版本信息和联系方式
     * @implSpec 支持团队协作开发
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智学物理 API 文档")
                        .description("智学物理后端接口文档")
                        .version("1.0")
                        .contact(new Contact().name("架构组").email("arch@zhixuewuli.com")));
    }
}
