package com.zhixuewuli.controller;

import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.dto.request.ExperimentCreateRequest;
import com.zhixuewuli.dto.request.ExperimentUpdateRequest;
import com.zhixuewuli.dto.response.ExperimentResponse;
import com.zhixuewuli.entity.Experiment;
import com.zhixuewuli.service.experiment.ExperimentService;
import com.zhixuewuli.common.util.JsonArrayUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 实验配置管理控制器
 * 
 * <p>
 * 提供实验配置增删改查的 RESTful API 接口。
 * </p>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@RestController
@RequestMapping("/experiments")
@Tag(name = "实验管理", description = "实验配置增删改查相关接口")
@Slf4j
@RequiredArgsConstructor
public class ExperimentController {
    
    private final ExperimentService experimentService;
    private final JsonArrayUtil jsonArrayUtil;
    
    /**
     * 创建实验
     * 
     * @param request 实验创建请求
     * @param httpRequest HTTP 请求（用于获取用户信息）
     * @return 创建的实验信息
     */
    @PostMapping("/teacher/create")
    @Operation(
        summary = "创建实验",
        description = "教师身份创建新的实验配置",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "创建成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）")
        }
    )
    @ResponseStatus(HttpStatus.CREATED)
    public Result<ExperimentResponse> createExperiment(
            @io.swagger.v3.oas.annotations.Parameter(description = "实验创建请求参数", required = true) @Valid @RequestBody ExperimentCreateRequest request,
            HttpServletRequest httpRequest) {
        
        Long userId = (Long) httpRequest.getAttribute("userId");
        String username = (String) httpRequest.getAttribute("username");
        log.info("用户 {} 开始创建实验，名称：{}", username, request.getName());
        
        Experiment experiment = experimentService.createExperiment(request, userId);
        ExperimentResponse response = convertToResponse(experiment);
        
        log.info("用户 {} 实验创建成功，ID: {}", username, experiment.getId());
        return Result.created("实验创建成功", response);
    }
    
    /**
     * 删除实验
     * 
     * @param id 实验ID
     * @param httpRequest HTTP 请求（用于获取用户信息）
     * @return 删除结果
     */
    @DeleteMapping("/teacher/delete/{id}")
    @Operation(
        summary = "删除实验",
        description = "根据ID删除指定实验",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "实验不存在")
        }
    )
    public Result<Void> deleteExperiment(
            @io.swagger.v3.oas.annotations.Parameter(description = "实验 ID", required = true) @PathVariable Long id,
            HttpServletRequest httpRequest) {
        
        Long userId = (Long) httpRequest.getAttribute("userId");
        String username = (String) httpRequest.getAttribute("username");
        log.info("用户 {} 开始删除实验，ID: {}", username, id);
        
        experimentService.deleteExperimentById(id, userId);
        
        log.info("用户 {} 实验删除成功，ID: {}", username, id);
        return Result.success();
    }
    
    /**
     * 更新实验
     * 
     * @param request 实验更新请求
     * @param httpRequest HTTP 请求（用于获取用户信息）
     * @return 更新后的实验信息
     */
    @PutMapping("/teacher/update")
    @Operation(
        summary = "更新实验",
        description = "增量更新实验配置信息",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "实验不存在")
        }
    )
    public Result<ExperimentResponse> updateExperiment(
            @io.swagger.v3.oas.annotations.Parameter(description = "实验更新请求参数", required = true) @Valid @RequestBody ExperimentUpdateRequest request,
            HttpServletRequest httpRequest) {
        
        Long userId = (Long) httpRequest.getAttribute("userId");
        String username = (String) httpRequest.getAttribute("username");
        log.info("用户 {} 开始更新实验，ID: {}", username, request.getId());
        
        Experiment experiment = experimentService.updateExperiment(request, userId);
        ExperimentResponse response = convertToResponse(experiment);
        
        log.info("用户 {} 实验更新成功，ID: {}", username, experiment.getId());
        return Result.success("实验更新成功", response);
    }
    
    /**
     * 根据ID 获取实验
     * 
     * @param id 实验ID
     * @return 实验信息
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "获取实验详情",
        description = "根据ID 获取实验配置信息",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "实验不存在")
        }
    )
    public Result<ExperimentResponse> getExperiment(
            @io.swagger.v3.oas.annotations.Parameter(description = "实验 ID", required = true) @PathVariable Long id) {
        
        log.info("查询实验，ID: {}", id);
        
        Experiment experiment = experimentService.getExperimentById(id);
        ExperimentResponse response = convertToResponse(experiment);
        
        return Result.success(response);
    }
    
    /**
     * 获取所有实验
     * 
     * @return 实验列表
     */
    @GetMapping("/all")
    @Operation(
        summary = "获取所有实验",
        description = "获取所有实验配置，按序号正序排列",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功")
        }
    )
    public Result<List<ExperimentResponse>> getAllExperiments() {
        
        log.info("查询所有实验");
        
        List<Experiment> experiments = experimentService.getAllExperiments();
        List<ExperimentResponse> responses = experiments.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
        
        return Result.success(responses);
    }
    
    /**
     * 将实体转换为响应 DTO
     * 
     * @param experiment 实验实体
     * @return 实验响应 DTO
     */
    private ExperimentResponse convertToResponse(Experiment experiment) {
        List<Long> questionIds = jsonArrayUtil.jsonToList(experiment.getQuestionIds());
        
        return ExperimentResponse.builder()
            .id(experiment.getId())
            .name(experiment.getName())
            .questionIds(questionIds)
            .questionIdsJson(experiment.getQuestionIds())
            .description(experiment.getDescription())
            .orderNumber(experiment.getOrderNumber())
            .createdAt(experiment.getCreatedAt())
            .updatedAt(experiment.getUpdatedAt())
            .build();
    }
}
