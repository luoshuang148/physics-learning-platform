# AI 服务快速测试脚本
$BaseUrl = "http://localhost:8080/api/v1"

Write-Host "`n=== 智学物理 AI 服务测试 ===" -ForegroundColor Cyan

# 登录获取 Token
Write-Host "`n[1/4] 登录获取 Token..." -ForegroundColor Yellow
$body = @{
    username = "student001"
    password = "password123"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -ContentType "application/json" -Body $body
    if ($response.code -eq 200 -or $response.code -eq "SUCCESS") {
        $token = $response.data.token
        Write-Host "✓ 登录成功" -ForegroundColor Green
    } else {
        Write-Host "✗ 登录失败：$($response.message)" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "✗ 登录异常：$_" -ForegroundColor Red
    exit 1
}

# 设置会话 ID
$sessionId = "20260315000000_sessionTestABC01"
Write-Host "`n[信息] 使用会话 ID: $sessionId" -ForegroundColor Gray

# 设置请求头
$headers = @{
    "Content-Type" = "application/json"
    "Authorization" = "Bearer $token"
    "X-Session-ID" = $sessionId
}

# 测试 1: 智谱 AI - 实时问答
Write-Host "`n[2/4] 测试：智谱 AI 实时问答" -ForegroundColor Yellow
$qaBody = @{
    question = "什么是抛体运动？请用通俗易懂的语言解释。"
    imageBase64 = $null
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$BaseUrl/ai/student/realtimeQA" -Method Post -Headers $headers -Body $qaBody -TimeoutSec 60
    Write-Host "✓ 实时问答完成" -ForegroundColor Green
} catch {
    Write-Host "✗ 实时问答失败：$_" -ForegroundColor Red
}

# 测试 2: 阿里 AI - 学生报告
Write-Host "`n[3/4] 测试：阿里 AI 学生报告" -ForegroundColor Yellow
try {
    $reportResponse = Invoke-RestMethod -Uri "$BaseUrl/ai/student/report" -Method Get -Headers $headers -TimeoutSec 120
    Write-Host "✓ 学生报告生成完成" -ForegroundColor Green
} catch {
    Write-Host "! 提示：$($_.ErrorDetails.Message)" -ForegroundColor Yellow
}

# 测试 3: AI 对话历史
Write-Host "`n[4/4] 测试：AI 对话历史" -ForegroundColor Yellow
try {
    $historyResponse = Invoke-RestMethod -Uri "$BaseUrl/ai/conversation/history?sessionId=$sessionId" -Method Get -Headers $headers -TimeoutSec 30
    Write-Host "✓ 对话历史查询完成" -ForegroundColor Green
} catch {
    Write-Host "! 对话历史：$($_.ErrorDetails.Message)" -ForegroundColor Yellow
}

Write-Host "`n=== 测试全部完成 ===" -ForegroundColor Cyan
pause
