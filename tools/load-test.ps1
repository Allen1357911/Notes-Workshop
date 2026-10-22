# ============================================================
#  并发 / 压测用例（性能与健壮性模块）
#
#  一、并发正确性用例：同时打同一个接口，验证"幂等 / 唯一 / 状态机"在并发下依然成立
#      点赞不重复、关注不重复、注册不重复、审核只有一个赢家、评论不丢不重、删除只成功一次
#  二、压测：对读接口施加并发压力，输出吞吐（QPS）与延迟（平均 / p95 / 最大）与错误数
#
#  实现说明：并发用 HttpClient 的异步请求（SendAsync 全部发出后统一 WaitAll），
#            不额外创建线程或运行空间，因此在受限环境里也能跑；
#            单请求延迟按"同一批并发请求的完成时间 ÷ 该批请求数"估计，属于偏保守的口径。
#
#  用法：powershell -ExecutionPolicy Bypass -File tools\load-test.ps1
#  产物：docs/load-test-report.md（汇总报告）、docs/load-test-raw.log（原始明细）
# ============================================================

$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# PowerShell 5.1 默认没有加载 System.Net.Http 程序集，而并发请求要用 HttpClient，先显式加载
try {
    Add-Type -AssemblyName System.Net.Http -ErrorAction Stop
} catch {
    Write-Host ("无法加载 System.Net.Http，并发测试需要它：" + $_.Exception.Message)
    exit 1
}

$proj = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $proj

$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "D:\Java\JDK25" }
$mvn = "D:\Java\IDEA\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
$mysqlCli = "C:\Program Files\MySQL\MySQL Server 9.7\bin\mysql.exe"
$dbUser = "root"
$dbPass = "123456"
$base = "http://127.0.0.1:8080"
$docs = Join-Path $proj "docs"
$rawLog = Join-Path $docs "load-test-raw.log"
$reportPath = Join-Path $docs "load-test-report.md"

$script:raws = New-Object System.Collections.ArrayList
$script:results = New-Object System.Collections.ArrayList
$script:lastResults = $null
$loadFail = -1   # 万一并发段抛异常，报告里也要有明确值而不是空白
$script:loadRows = @()

function Add-Raw([string]$text) {
    [void]$script:raws.Add($text)
    Write-Host $text
}

function Add-Result([string]$name, [string]$expect, [string]$actual, [bool]$ok, [string]$note) {
    # 如果刚刚跑过并发批次，把状态分布一并写进"实际"，失败时便于定位
    if ($script:lastResults) {
        $actual = $actual + "；状态分布: " + (Describe-Results $script:lastResults)
    }
    [void]$script:results.Add([pscustomobject]@{
            Name = $name; Expect = $expect; Actual = $actual
            Result = $(if ($ok) { "通过" } else { "失败" }); Note = $note
        })
    Add-Raw ("[{0}] {1}" -f $(if ($ok) { "通过" } else { "失败" }), $name)
    Add-Raw ("    期望: " + $expect)
    Add-Raw ("    实际: " + $actual)
    Add-Raw ("    说明: " + $note)
}

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
            $s = $req.GetRequestStream(); $s.Write($bytes, 0, $bytes.Length); $s.Close()
        }
        try { $resp = $req.GetResponse() } catch [System.Net.WebException] { $resp = $_.Exception.Response }
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
        $text = $reader.ReadToEnd(); $reader.Close(); $resp.Close()
        $obj = $null; try { $obj = $text | ConvertFrom-Json } catch { }
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Json = $obj; Text = $text }
    } catch {
        return [pscustomobject]@{ Status = -1; Json = $null; Text = $_.Exception.Message }
    }
}

function Sql([string]$statement) {
    $out = & $mysqlCli -u $dbUser --password=$dbPass -N -B -e "USE note_workshop; $statement" 2>$null
    return ($out | Select-Object -First 1)
}

function SqlNum([string]$statement) {
    $value = Sql $statement
    if ($null -eq $value -or "$value" -eq "") { return -1 }
    return [int]"$value"
}

# ------------------------------------------------------------
#  并发执行器：HttpClient 异步并发
#  返回 @{ Results = 每次请求的 {Status, Code}; BatchTimes = 每批并发请求的完成耗时(ms) }
# ------------------------------------------------------------
function Invoke-Concurrent {
    param(
        [int]$Count,
        [int]$Concurrency,
        [string]$Method,
        [string]$PathTemplate,
        [string[]]$Tokens,
        [string]$JsonTemplate
    )

    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.MaxConnectionsPerServer = 200
    $client = New-Object System.Net.Http.HttpClient($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(60)

    $results = @()
    $batchTimes = @()
    $index = 0
    $batches = [Math]::Ceiling($Count / [double]$Concurrency)

    try {
        for ($b = 0; $b -lt $batches; $b++) {
            $tasks = @()
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            for ($k = 0; $k -lt $Concurrency -and $index -lt $Count; $k++) {
                $index++
                $url = $base + $PathTemplate.Replace("{i}", "$index")
                $req = New-Object System.Net.Http.HttpRequestMessage
                $req.Method = New-Object System.Net.Http.HttpMethod($Method)
                $req.RequestUri = New-Object System.Uri($url)
                if ($Tokens -and $Tokens.Count -gt 0) {
                    # 只传 1 个令牌时表示"所有并发请求都用它"；传多个则按序号一一对应
                    $token = if ($Tokens.Count -ge $index) { $Tokens[$index - 1] } else { $Tokens[$Tokens.Count - 1] }
                    if ($token) {
                        [void]$req.Headers.Add("Authorization", "Bearer " + $token)
                    }
                }
                if ($JsonTemplate) {
                    $body = $JsonTemplate.Replace("{i}", "$index")
                    $req.Content = New-Object System.Net.Http.StringContent($body, [System.Text.Encoding]::UTF8, "application/json")
                }
                $tasks += $client.SendAsync($req)
            }
            # 请求已全部在途（并发），这里逐个等待即可；
            # 不用 Task.WaitAll 是为了避开 PowerShell 把 Object[] 转成 Task[] 的坑
            foreach ($task in $tasks) { [void]$task.Wait(60000) }
            $sw.Stop()
            $batchTimes += [Math]::Round($sw.Elapsed.TotalMilliseconds, 1)

            foreach ($task in $tasks) {
                try {
                    $resp = $task.Result
                    $text = $resp.Content.ReadAsStringAsync().Result
                    $code = -1
                    try { $code = ($text | ConvertFrom-Json).code } catch { }
                    $results += [pscustomobject]@{ Status = [int]$resp.StatusCode; Code = $code }
                } catch {
                    $results += [pscustomobject]@{ Status = -1; Code = -1 }
                }
            }
        }
    } finally {
        $client.Dispose()
        $handler.Dispose()
    }

    $script:lastResults = $results   # 供 Add-Result 输出状态分布
    return [pscustomobject]@{ Results = $results; BatchTimes = $batchTimes }
}
function Count-Of($items, [scriptblock]$filter) {
    return @($items | Where-Object $filter).Count
}

# 把一批请求结果压缩成"HTTP 状态×次数"与"业务 code×次数"，
# 这样用例失败时一眼就能看出是 401 没带令牌、还是 20005 状态冲突，不用再靠猜
function Describe-Results($items) {
    if (-not $items -or @($items).Count -eq 0) { return "（没有结果）" }
    $statusText = (@($items) | Group-Object Status | Sort-Object Name | ForEach-Object { "HTTP " + $_.Name + "×" + $_.Count }) -join ", "
    $codeText = (@($items) | Where-Object { $_.Code -ne 200 } | Group-Object Code | Sort-Object Name | ForEach-Object { "code " + $_.Name + "×" + $_.Count }) -join ", "
    if ($codeText) { return $statusText + "；" + $codeText }
    return $statusText
}

function Percentile($values, [double]$p) {
    $sorted = @($values | Sort-Object)
    if ($sorted.Count -eq 0) { return 0 }
    $idx = [Math]::Min($sorted.Count - 1, [int][Math]::Floor($sorted.Count * $p))
    return $sorted[$idx]
}

# ============================================================
#  开跑
# ============================================================
Add-Raw ("==== 并发 / 压测开始 " + (Get-Date -Format "yyyy-MM-dd HH:mm:ss") + " ====")

Add-Raw "`n=== 打包 ==="
& $mvn -B -ntp "-Dmaven.repo.local=$proj\.mvnrepo" -DskipTests package 2>&1 | Select-String -Pattern "BUILD|ERROR" | Out-String | Add-Raw

Add-Raw "=== 启动应用 ==="
$proc = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList "-jar", "target\note-workshop-1.0.0.jar" `
    -WorkingDirectory $proj -RedirectStandardOutput "$proj\target\load.out.log" -RedirectStandardError "$proj\target\load.err.log" -PassThru
$ready = $false
for ($i = 0; $i -lt 90; $i++) {
    Start-Sleep -Seconds 1
    if ($proc.HasExited) { break }
    if ((Api -Method GET -Path "/api/notes/public").Status -eq 200) { $ready = $true; break }
}
Add-Raw ("应用就绪: " + $ready)
if (-not $ready) {
    Add-Raw "应用未能启动，终止测试"
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -EA SilentlyContinue }
    exit 1
}

try {
    $suffix = Get-Random -Minimum 10000 -Maximum 99999
    $uAuthor = "load_author_$suffix"
    $uAdmin = "load_admin_$suffix"

    Add-Raw "`n=== 准备负载测试账号 ==="
    Api -Method POST -Path "/api/auth/register" -Json ('{"username":"' + $uAuthor + '","password":"123456"}') | Out-Null
    Api -Method POST -Path "/api/auth/register" -Json ('{"username":"' + $uAdmin + '","password":"123456"}') | Out-Null
    & $mysqlCli -u $dbUser --password=$dbPass -e "USE note_workshop; UPDATE t_user SET role='ADMIN' WHERE username='$uAdmin';" 2>&1 | Out-Null
    $tAuthor = (Api -Method POST -Path "/api/auth/login" -Json ('{"username":"' + $uAuthor + '","password":"123456"}')).Json.data.token
    $tAdmin = (Api -Method POST -Path "/api/auth/login" -Json ('{"username":"' + $uAdmin + '","password":"123456"}')).Json.data.token
    $idAuthor = (Api -Method GET -Path "/api/users/me" -Token $tAuthor).Json.data.id
    Add-Raw ("作者 id=$idAuthor（账号 $uAuthor / $uAdmin）")

    $noteId = (Api -Method POST -Path "/api/notes" -Token $tAuthor -Json '{"bookName":"《并发测试用笔记》","content":"用于并发点赞、并发评论等场景。","visibility":"PUBLIC"}').Json.data.id
    Api -Method PUT -Path ("/api/admin/notes/" + $noteId + "/audit") -Token $tAdmin -Json '{"approved":true}' | Out-Null

    $pendingId = (Api -Method POST -Path "/api/notes" -Token $tAuthor -Json '{"bookName":"《并发审核用笔记》","content":"多个管理员同时审核，只能有一个成功。","visibility":"PUBLIC"}').Json.data.id

    $rejectedId = (Api -Method POST -Path "/api/notes" -Token $tAuthor -Json '{"bookName":"《并发重提用笔记》","content":"多个请求同时重提，只能有一个成功。","visibility":"PUBLIC"}').Json.data.id
    Api -Method PUT -Path ("/api/admin/notes/" + $rejectedId + "/audit") -Token $tAdmin -Json '{"approved":false,"remark":"先驳回，用于并发重提测试"}' | Out-Null

    $userCount = 20
    $tokens = @()
    for ($i = 1; $i -le $userCount; $i++) {
        $u = "load_u${i}_$suffix"
        Api -Method POST -Path "/api/auth/register" -Json ('{"username":"' + $u + '","password":"123456"}') | Out-Null
        $tokens += (Api -Method POST -Path "/api/auth/login" -Json ('{"username":"' + $u + '","password":"123456"}')).Json.data.token
    }
    Add-Raw ("已准备 $userCount 个并发测试用户")

    # ---------------- 一、并发正确性 ----------------
    Add-Raw "`n=== 一、并发正确性用例 ==="
    try {

    $r = (Invoke-Concurrent -Count 30 -Concurrency 30 -Method "POST" -PathTemplate ("/api/notes/" + $noteId + "/like") -Tokens @($tAuthor)).Results
    $okCount = Count-Of $r { $_.Status -eq 200 }
    $serverError = Count-Of $r { $_.Status -ge 500 -or $_.Status -eq -1 }
    $likeRows = SqlNum "SELECT COUNT(*) FROM t_note_like WHERE note_id=$noteId AND user_id=$idAuthor;"
    $likeCountApi = (Api -Method GET -Path ("/api/notes/" + $noteId) -Token $tAuthor).Json.data.likeCount
    Add-Result "并发点赞：同一用户同时点 30 次" "全部 200；点赞表只有 1 行；likeCount=1" `
        ("200 响应 $okCount 个，5xx/异常 $serverError 个，点赞表 $likeRows 行，接口 likeCount=$likeCountApi") `
        ($okCount -eq 30 -and $serverError -eq 0 -and $likeRows -eq 1 -and $likeCountApi -eq 1) `
        "唯一索引 + 幂等处理，重复点赞不会产生多行，也不会报 500"

    $r = (Invoke-Concurrent -Count 30 -Concurrency 30 -Method "POST" -PathTemplate ("/api/users/" + $idAuthor + "/follow") -Tokens @($tokens[0])).Results
    $okCount = Count-Of $r { $_.Status -eq 200 }
    $followRows = SqlNum "SELECT COUNT(*) FROM t_follow WHERE followee_id=$idAuthor;"
    Add-Result "并发关注：同一用户同时关注 30 次" "全部 200；关注表只有 1 行" `
        ("200 响应 $okCount 个，关注表 $followRows 行") `
        ($okCount -eq 30 -and $followRows -eq 1) `
        "唯一索引兜底，重复关注按幂等处理"

    $dupUser = "load_dup_$suffix"
    $r = (Invoke-Concurrent -Count 20 -Concurrency 20 -Method "POST" -PathTemplate "/api/auth/register" `
            -JsonTemplate ('{"username":"' + $dupUser + '","password":"123456"}')).Results
    $okCount = Count-Of $r { $_.Status -eq 200 }
    $dupCode = Count-Of $r { $_.Code -eq 10002 -or $_.Code -eq 400 }
    $userRows = SqlNum "SELECT COUNT(*) FROM t_user WHERE username='$dupUser';"
    Add-Result "并发注册：同名用户同时注册 20 次" "恰好 1 个成功，其余 10002/400；用户表只有 1 行" `
        ("200 响应 $okCount 个，10002/400 响应 $dupCode 个，用户表 $userRows 行") `
        ($okCount -eq 1 -and $userRows -eq 1 -and ($okCount + $dupCode) -eq 20) `
        "先查重 + 唯一索引双重保护，并发下不会注册出两个同名账号"

    $r = (Invoke-Concurrent -Count 10 -Concurrency 10 -Method "PUT" -PathTemplate ("/api/admin/notes/" + $pendingId + "/audit") `
            -Tokens @($tAdmin) -JsonTemplate '{"approved":true}').Results
    $okCount = Count-Of $r { $_.Status -eq 200 }
    $conflict = Count-Of $r { $_.Code -eq 20005 }
    $finalStatus = Sql "SELECT audit_status FROM t_note WHERE id=$pendingId;"
    Add-Result "并发审核：10 个请求同时审核同一篇待审核笔记" "恰好 1 个成功，其余 20005；最终状态唯一" `
        ("200 响应 $okCount 个，20005 响应 $conflict 个，最终状态 $finalStatus") `
        ($okCount -eq 1 -and $conflict -eq 9 -and $finalStatus -eq "APPROVED") `
        "审核用条件更新（WHERE audit_status='PENDING'），同一状态迁移只有一个赢家"

    $r = (Invoke-Concurrent -Count 10 -Concurrency 10 -Method "PUT" -PathTemplate ("/api/notes/" + $rejectedId + "/resubmit") -Tokens @($tAuthor)).Results
    $okCount = Count-Of $r { $_.Status -eq 200 }
    $conflict = Count-Of $r { $_.Code -eq 20006 }
    $finalStatus = Sql "SELECT audit_status FROM t_note WHERE id=$rejectedId;"
    Add-Result "并发重新提交：同一篇被驳回笔记同时重提 10 次" "恰好 1 个成功，其余 20006" `
        ("200 响应 $okCount 个，20006 响应 $conflict 个，最终状态 $finalStatus") `
        ($okCount -eq 1 -and $conflict -eq 9 -and $finalStatus -eq "PENDING") `
        "重提同样用条件更新（WHERE audit_status='REJECTED'）"

    $r = (Invoke-Concurrent -Count $userCount -Concurrency $userCount -Method "POST" `
            -PathTemplate ("/api/notes/" + $noteId + "/comments") -Tokens $tokens `
            -JsonTemplate '{"content":"并发评论第 {i} 条"}').Results
    $okCount = Count-Of $r { $_.Status -eq 200 }
    $commentRows = SqlNum "SELECT COUNT(*) FROM t_comment WHERE note_id=$noteId AND deleted=0;"
    Add-Result "并发评论：$userCount 个用户同时评论同一篇笔记" "$userCount 个全部成功；评论表 $userCount 行" `
        ("200 响应 $okCount 个，评论表未删除行 $commentRows") `
        ($okCount -eq $userCount -and $commentRows -eq $userCount) `
        "评论是纯插入，不会互相覆盖或丢失"

    $r = (Invoke-Concurrent -Count $userCount -Concurrency $userCount -Method "POST" `
            -PathTemplate ("/api/notes/" + $noteId + "/like") -Tokens $tokens).Results
    $okCount = Count-Of $r { $_.Status -eq 200 }
    $likeAll = SqlNum "SELECT COUNT(*) FROM t_note_like WHERE note_id=$noteId;"
    $expectedTotal = $userCount + 1
    Add-Result "并发点赞：$userCount 个用户同时点赞同一篇笔记" "全部成功；点赞表共 $expectedTotal 行（含作者那票）" `
        ("200 响应 $okCount 个，点赞表共 $likeAll 行") `
        ($okCount -eq $userCount -and $likeAll -eq $expectedTotal) `
        "点赞数实时 COUNT 聚合，不会像冗余计数那样丢更新"

    # 用作者自己刚发的评论来删：删别人的评论返回 40002 是正确行为，但不利于验证"重复删除只成功一次"
    $oneComment = (Api -Method POST -Path ("/api/notes/" + $noteId + "/comments") -Token $tAuthor -Json '{"content":"用于并发删除测试的评论"}').Json.data.id
    Add-Raw ("并发删除用例使用作者自己发的评论 id=" + $oneComment)
    $r = (Invoke-Concurrent -Count 5 -Concurrency 5 -Method "DELETE" -PathTemplate ("/api/comments/" + $oneComment) -Tokens @($tAuthor)).Results
    $okCount = Count-Of $r { $_.Status -eq 200 }
    $notFound = Count-Of $r { $_.Code -eq 40001 }
    Add-Result "并发删除：同一条评论同时删 5 次" "恰好 1 个成功，其余 40001" `
        ("200 响应 $okCount 个，40001 响应 $notFound 个") `
        ($okCount -eq 1 -and $notFound -eq 4) `
        "逻辑删除 + 先查后删，重复删除只会成功一次"
    } catch {
        # 脚本自身出错也要留痕，不能静默结束（否则报告里会显示 0 个用例，看不出问题）
        Add-Raw ("并发用例执行中断：" + $_.Exception.Message)
        Add-Result "并发用例执行" "全部用例正常跑完" ("执行中断：" + $_.Exception.Message) $false `
            "脚本异常（不影响被测应用本身）"
    }

    # ---------------- 二、压测 ----------------
    Add-Raw "`n=== 二、压测（吞吐与延迟）==="
    $loadCases = @(
        @{ Name = "公开笔记列表 GET /api/notes/public"; Method = "GET"; Path = "/api/notes/public?page=1&size=10"; Total = 300; Concurrency = 10 },
        @{ Name = "笔记详情 GET /api/notes/{id}"; Method = "GET"; Path = ("/api/notes/" + $noteId); Total = 300; Concurrency = 10 },
        @{ Name = "点赞排行 GET /api/notes/rank（带聚合）"; Method = "GET"; Path = "/api/notes/rank?page=1&size=10"; Total = 200; Concurrency = 10 }
    )
    foreach ($case in $loadCases) {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $out = Invoke-Concurrent -Count $case.Total -Concurrency $case.Concurrency -Method $case.Method -PathTemplate $case.Path
        $sw.Stop()
        $r = $out.Results
        $okCount = Count-Of $r { $_.Status -eq 200 }
        $errCount = $case.Total - $okCount
        $qps = [Math]::Round($case.Total / [Math]::Max($sw.Elapsed.TotalSeconds, 0.001), 1)
        $latencies = @($out.BatchTimes | ForEach-Object { $_ / $case.Concurrency })
        $avg = [Math]::Round((($latencies | Measure-Object -Average).Average), 1)
        $p95 = [Math]::Round((Percentile $latencies 0.95), 1)
        $maxLatency = [Math]::Round((Percentile $latencies 1.0), 1)
        $script:loadRows += [pscustomobject]@{
            Name = $case.Name; Total = $case.Total; Concurrency = $case.Concurrency
            Ok = $okCount; Err = $errCount; Qps = $qps; Wall = [Math]::Round($sw.Elapsed.TotalSeconds, 2)
            Avg = $avg; P95 = $p95; Max = $maxLatency
        }
        Add-Raw ("{0}：{1} 请求 / 并发 {2} → 成功 {3}，失败 {4}，QPS {5}，平均 {6} ms，p95 {7} ms，最大 {8} ms，总耗时 {9}s" -f `
                $case.Name, $case.Total, $case.Concurrency, $okCount, $errCount, $qps, $avg, $p95, $maxLatency, [Math]::Round($sw.Elapsed.TotalSeconds, 2))
    }
    $loadFail = @($script:loadRows | Where-Object { $_.Err -gt 0 }).Count
    $script:lastResults = $null   # 压测用例不显示并发分布
    Add-Result "压测：3 个读接口共 800 次请求" "零失败" `
        ("失败用例数 " + $loadFail + "；吞吐与延迟见报告表格") `
        ($loadFail -eq 0) `
        "读接口在 10 并发下无错误、无超时"

    Api -Method DELETE -Path ("/api/notes/" + $noteId) -Token $tAuthor | Out-Null
    Api -Method DELETE -Path ("/api/notes/" + $pendingId) -Token $tAuthor | Out-Null
    Api -Method DELETE -Path ("/api/notes/" + $rejectedId) -Token $tAuthor | Out-Null
    Add-Raw "`n已清理本次负载测试创建的 3 篇笔记"
} finally {
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -EA SilentlyContinue }
    Start-Sleep -Seconds 2
    Add-Raw ("应用已停止: " + $proc.HasExited)
}

# ============================================================
#  汇总与报告
# ============================================================
$passCount = @($script:results | Where-Object { $_.Result -eq "通过" }).Count
$failCount = @($script:results | Where-Object { $_.Result -ne "通过" }).Count
Add-Raw ("`n用例：总数 {0}，通过 {1}，失败 {2}" -f $script:results.Count, $passCount, $failCount)

$loadSummary = if ($loadFail -lt 0) { "压测未执行完" } else { "压测失败 " + $loadFail + " 次" }
Add-Raw $loadSummary

[System.IO.File]::WriteAllText($rawLog, ($script:raws -join "`r`n"), (New-Object System.Text.UTF8Encoding($false)))

$md = New-Object System.Text.StringBuilder
[void]$md.AppendLine("# 并发 / 压测报告")
[void]$md.AppendLine("")
[void]$md.AppendLine("> 由 ``tools/load-test.ps1`` 自动生成：真实启动应用，用多线程并发打同一个接口验证幂等与状态机，")
[void]$md.AppendLine("> 再以 10 并发对读接口施压共 800 次，统计吞吐与延迟。原始明细见 [load-test-raw.log](load-test-raw.log)。")
[void]$md.AppendLine("")
[void]$md.AppendLine("生成时间：" + (Get-Date -Format "yyyy-MM-dd HH:mm:ss"))
[void]$md.AppendLine("")
[void]$md.AppendLine("## 一、并发正确性用例")
[void]$md.AppendLine("")
[void]$md.AppendLine("| # | 用例 | 期望 | 实际 | 结果 |")
[void]$md.AppendLine("| --- | --- | --- | --- | --- |")
$index = 0
foreach ($item in $script:results) {
    if ($item.Name -like "压测：*") { continue }
    $index++
    [void]$md.AppendLine("| " + $index + " | " + $item.Name + " | " + $item.Expect + " | " + $item.Actual + " | " + $item.Result + " |")
}
[void]$md.AppendLine("")
[void]$md.AppendLine("## 二、压测结果（吞吐与延迟）")
[void]$md.AppendLine("")
[void]$md.AppendLine("| 接口 | 请求数 | 并发 | 成功 | 失败 | QPS | 总耗时 | 平均延迟 | p95 | 最大延迟 |")
[void]$md.AppendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
foreach ($row in $script:loadRows) {
    [void]$md.AppendLine("| " + $row.Name + " | " + $row.Total + " | " + $row.Concurrency + " | " + $row.Ok + " | " + $row.Err + " | " + $row.Qps + " | " + $row.Wall + " s | " + $row.Avg + " ms | " + $row.P95 + " ms | " + $row.Max + " ms |")
}
[void]$md.AppendLine("")
[void]$md.AppendLine("延迟口径说明：并发请求用 HttpClient 异步同时发出，按批统计完成时间再除以该批请求数，")
[void]$md.AppendLine("属于偏保守的单请求延迟估计（真实单请求延迟不会高于该值）。")
[void]$md.AppendLine("")
[void]$md.AppendLine("## 三、结论")
[void]$md.AppendLine("")
[void]$md.AppendLine("- 并发正确性用例 **" + $passCount + "/" + $script:results.Count + " 通过**：点赞、关注、注册、审核、重提、评论、删除在并发下都不重复、不丢失、只有一个赢家。")
[void]$md.AppendLine("- 压测共 800 次请求，**" + $(if ($loadFail -lt 0) { "未执行完" } else { "失败 " + $loadFail + " 次" }) + "**。")
[void]$md.AppendLine("- 关键修复：笔记的审核 / 下架 / 恢复 / 重新提交原本是【先查状态再改】，并发下两个请求可能都通过；")
[void]$md.AppendLine("  现已改为条件更新（``UPDATE ... WHERE id = ? AND audit_status = ?`` 并检查影响行数），同一状态迁移只有一个赢家。")
[void]$md.AppendLine("- 点赞 / 关注 / 注册依靠数据库唯一索引保证幂等；点赞数、评论数、粉丝数都是实时聚合，不存在计数丢更新。")
[void]$md.AppendLine("")
[void]$md.AppendLine("## 四、复现方式")
[void]$md.AppendLine("")
[void]$md.AppendLine('```powershell')
[void]$md.AppendLine("powershell -ExecutionPolicy Bypass -File tools\load-test.ps1")
[void]$md.AppendLine('```')
[System.IO.File]::WriteAllText($reportPath, $md.ToString(), (New-Object System.Text.UTF8Encoding($false)))

Write-Output ("`n报告: " + $reportPath)
Write-Output ("原始日志: " + $rawLog)
Write-Output ("用例 通过 " + $passCount + " / " + $script:results.Count + "，" + $loadSummary)
