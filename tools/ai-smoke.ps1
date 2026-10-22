# AI 真实调用冒烟测试（会消耗真实 token，默认不跑）
#
# 用法：
#   1. 先设置环境变量（只需一次，然后重开终端）：
#        setx DEEPSEEK_API_KEY "你的key"
#   2. 启动应用（普通模式，不要加 --app.ai.mock=true）
#   3. 在本目录执行：
#        powershell -ExecutionPolicy Bypass -File tools\ai-smoke.ps1
#
# 脚本做的事：登录演示账号 -> 建一篇笔记 -> 审核通过 -> 依次调用三种 AI 能力
#            -> 打印结果与 token 用量 -> 保存其中一种 -> 查统计 -> 清理测试笔记

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = "http://127.0.0.1:8080"

function Api {
    param([string]$Method, [string]$Path, [string]$Json, [string]$Token)
    try {
        $req = [System.Net.HttpWebRequest]::Create($base + $Path)
        $req.Method = $Method
        $req.Timeout = 60000
        if ($Token) { [void]$req.Headers.Add("Authorization", "Bearer $Token") }
        if ($Json) {
            $bytes = [System.Text.Encoding]::UTF8.GetBytes($Json)
            $req.ContentType = "application/json;charset=utf-8"
            $req.ContentLength = $bytes.Length
            $stream = $req.GetRequestStream()
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Close()
        }
        try {
            $resp = $req.GetResponse()
        } catch [System.Net.WebException] {
            $resp = $_.Exception.Response
        }
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
        $text = $reader.ReadToEnd()
        $reader.Close()
        $resp.Close()
        $obj = $null
        try { $obj = $text | ConvertFrom-Json } catch { }
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Json = $obj }
    } catch {
        return [pscustomobject]@{ Status = -1; Json = $null }
    }
}

if (-not $env:DEEPSEEK_API_KEY) {
    Write-Output "[提示] 当前窗口没有读到 DEEPSEEK_API_KEY。"
    Write-Output "       如果你刚用 setx 设置过，请【新开一个终端】再执行本脚本（setx 只对新进程生效）。"
}

Write-Output "=== 1. 登录 ==="
$login = Api -Method POST -Path "/api/auth/login" -Json '{"username":"alice","password":"123456"}'
if ($login.Status -ne 200) {
    Write-Output ("  登录失败（HTTP " + $login.Status + "）。请确认服务已启动、演示账号已存在。")
    exit 1
}
$token = $login.Json.data.token
Write-Output "  alice 登录成功"

Write-Output "`n=== 2. 建一篇笔记并审核通过 ==="
$note = Api -Method POST -Path "/api/notes" -Token $token -Json '{"bookName":"《AI 冒烟测试》","content":"这本书让我意识到，成长不是线性的。作者用三个真实案例说明：第一，刻意练习需要及时反馈；第二，休息和输入同样重要；第三，长期主义比短期爆发更可靠。我打算把这三条应用到自己的学习计划里，每周复盘一次。","visibility":"PUBLIC"}'
$noteId = $note.Json.data.id
Write-Output ("  笔记 id=" + $noteId + "，状态=" + $note.Json.data.auditStatus)

$admin = Api -Method POST -Path "/api/auth/login" -Json '{"username":"demo_admin","password":"123456"}'
if ($admin.Status -eq 200) {
    Api -Method PUT -Path ("/api/admin/notes/" + $noteId + "/audit") -Token $admin.Json.data.token -Json '{"approved":true}' | Out-Null
    Write-Output "  已由管理员审核通过"
}

Write-Output "`n=== 3. 依次调用三种 AI 能力 ==="
$lastLogId = $null
foreach ($capability in @("SUMMARY", "AUDIENCE", "SCORE")) {
    $r = Api -Method POST -Path ("/api/notes/" + $noteId + "/ai/" + $capability) -Token $token
    if ($r.Status -eq 200) {
        $lastLogId = $r.Json.data.aiLogId
        Write-Output ("`n  [" + $capability + "] 成功，模型=" + $r.Json.data.model + "，tokens=" + $r.Json.data.totalTokens + "，耗时=" + $r.Json.data.costMs + "ms，今日剩余 " + $r.Json.data.remainingQuota + " 次")
        Write-Output ("  " + $r.Json.data.text.Replace("`n", "`n  "))
    } else {
        Write-Output ("`n  [" + $capability + "] 失败：HTTP " + $r.Status + " / code " + $r.Json.code + " / " + $r.Json.message)
    }
}

Write-Output "`n=== 4. 确认保存最后一种结果（不公开） ==="
if ($lastLogId) {
    $save = Api -Method PUT -Path ("/api/notes/" + $noteId + "/ai-result") -Token $token -Json ('{"aiLogId":' + $lastLogId + ',"aiPublic":false}')
    Write-Output ("  保存结果：HTTP " + $save.Status + " / code " + $save.Json.code)
    $detail = Api -Method GET -Path ("/api/notes/" + $noteId) -Token $token
    Write-Output ("  详情里的 AI 结果类型=" + $detail.Json.data.aiType + "，公开=" + $detail.Json.data.aiPublic)
}

Write-Output "`n=== 5. 管理员统计 ==="
if ($admin.Status -eq 200) {
    $stats = Api -Method GET -Path "/api/admin/ai-stats" -Token $admin.Json.data.token
    if ($stats.Status -eq 200) {
        Write-Output ("  总调用 " + $stats.Json.data.totalCalls + " 次，成功率 " + $stats.Json.data.successRate + "%，总 token " + $stats.Json.data.totalTokens + "，平均耗时 " + $stats.Json.data.avgCostMs + "ms，模型 " + $stats.Json.data.model)
        foreach ($item in @($stats.Json.data.byCapability)) {
            Write-Output ("    " + $item.label + "：调用 " + $item.calls + " 次，token " + $item.tokens + "，平均 " + $item.avgCostMs + "ms")
        }
    }
}

Write-Output "`n=== 6. 清理测试笔记 ==="
Api -Method DELETE -Path ("/api/notes/" + $noteId) -Token $token | Out-Null
Write-Output "  已删除"

Write-Output "`n完成。如果上面三种能力都有结果，说明 AI 接入成功。"
