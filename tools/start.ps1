param(
    [switch]$NoBrowser,   # 自动化测试用：不打开浏览器
    [switch]$NoPause      # 自动化测试用：失败时不等按键
)
# ============================================================
# 一键启动笔记工坊
#   双击 tools\start.bat 即可；命令行：
#     powershell -ExecutionPolicy Bypass -File tools\start.ps1
#   做的事：检查 MySQL -> 检查端口 -> 检查 jar（没有就打包）-> 读取 Key -> 后台启动 -> 等就绪 -> 打开浏览器
#   注意：本文件必须保存为 UTF-8 with BOM，否则 PowerShell 会按 ANSI 读、中文乱码
# ============================================================
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Wait-Key {
    if ($NoPause) { return }
    Write-Host ''
    Write-Host '按任意键关闭窗口...'
    try { [void]$Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown') } catch { Start-Sleep -Seconds 6 }
}

$proj = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $proj 'target\note-workshop-1.0.0.jar'
$base = 'http://localhost:8080'
$outLog = Join-Path $proj 'target\app.out.log'
$errLog = Join-Path $proj 'target\app.err.log'

$jdkHome = $env:JAVA_HOME
if (-not $jdkHome -or -not (Test-Path (Join-Path $jdkHome 'bin\java.exe'))) { $jdkHome = 'D:\Java\JDK25' }
$javaExe = Join-Path $jdkHome 'bin\java.exe'

Write-Host '=============================================='
Write-Host '  笔记工坊 Note-Workshop 一键启动'
Write-Host '=============================================='

# ---------- 1. MySQL ----------
Write-Host ''
Write-Host '[1/5] 检查 MySQL（3306 端口）...'
$mysqlUp = $false
try { $mysqlUp = [bool](Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue) } catch { }
if (-not $mysqlUp) {
    Write-Host '  [X] 3306 没有监听：MySQL 没启动。' -ForegroundColor Red
    Write-Host '      解决：Win+R 输入 services.msc -> 找到 MySQL 服务 -> 右键启动' -ForegroundColor Yellow
    Wait-Key
    exit 1
}
Write-Host '  [OK] MySQL 正在运行'

# ---------- 2. 端口 ----------
Write-Host ''
Write-Host '[2/5] 检查 8080 端口...'
$busy = $null
try { $busy = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue } catch { }
if ($busy) {
    $busyPid = ($busy | Select-Object -First 1).OwningProcess
    Write-Host ('  [!] 8080 已被占用（PID ' + $busyPid + '），项目可能已经在运行')
    Write-Host '      直接打开浏览器即可；要重启请先运行 tools\stop.bat'
    if (-not $NoBrowser) { Start-Process $base }
    exit 0
}
Write-Host '  [OK] 8080 空闲'

# ---------- 3. jar ----------
Write-Host ''
Write-Host '[3/5] 检查打包结果...'
if (-not (Test-Path $jar)) {
    Write-Host '  没找到 jar，开始用 Maven 打包（第一次会下载依赖，比较慢）...'
    $mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
    if (-not $mvn) {
        $candidate = 'D:\Java\IDEA\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd'
        if (Test-Path $candidate) { $mvn = $candidate }
    }
    if (-not $mvn) {
        Write-Host '  [X] 找不到 Maven：请手动执行 mvn -DskipTests package' -ForegroundColor Red
        Wait-Key
        exit 1
    }
    & $mvn -B -ntp "-Dmaven.repo.local=$proj\.mvnrepo" -DskipTests package
    if (-not (Test-Path $jar)) {
        Write-Host '  [X] 打包失败，请检查上面的报错' -ForegroundColor Red
        Wait-Key
        exit 1
    }
}
Write-Host '  [OK] 找到 target\note-workshop-1.0.0.jar'

# ---------- 4. API Key ----------
Write-Host ''
Write-Host '[4/5] 准备 DeepSeek API Key...'
if ($env:DEEPSEEK_API_KEY) {
    Write-Host '  [OK] 当前窗口已有 DEEPSEEK_API_KEY'
} else {
    $key = (Get-ItemProperty 'HKCU:\Environment' -Name 'DEEPSEEK_API_KEY' -ErrorAction SilentlyContinue).DEEPSEEK_API_KEY
    if ($key) {
        $env:DEEPSEEK_API_KEY = $key.Trim()
        Write-Host '  [OK] 已从系统环境变量读取到 DEEPSEEK_API_KEY'
    } else {
        Write-Host '  [!] 没读到 Key：AI 接口会返回 60001，其他功能都正常' -ForegroundColor Yellow
        Write-Host '      要启用 AI：执行  setx DEEPSEEK_API_KEY "你的key"  然后重新打开窗口' -ForegroundColor Yellow
    }
}

# ---------- 5. 启动 ----------
Write-Host ''
Write-Host '[5/5] 启动应用...'
$targetDir = Split-Path $outLog -Parent
if (-not (Test-Path $targetDir)) { New-Item -ItemType Directory -Path $targetDir -Force | Out-Null }
Start-Process -FilePath $javaExe -ArgumentList '-jar', $jar -WorkingDirectory $proj `
    -RedirectStandardOutput $outLog -RedirectStandardError $errLog | Out-Null

# 等就绪：最多 120 秒，每 10 秒报一次进度（避免"看起来没动静"）
$started = Get-Date
$deadline = $started.AddSeconds(120)
$ready = $false
$lastNote = 0
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    try {
        $resp = Invoke-WebRequest "$base/api/notes/public" -UseBasicParsing -TimeoutSec 3
        if ($resp.StatusCode -eq 200) { $ready = $true; break }
    } catch { }
    $elapsed = [int]((Get-Date) - $started).TotalSeconds
    if (($elapsed - $lastNote) -ge 10) {
        $lastNote = $elapsed
        Write-Host ('  等待启动... 已 ' + $elapsed + ' 秒（最多等 120 秒）')
    }
}

if (-not $ready) {
    Write-Host '  [X] 120 秒内没有就绪，最后 20 行日志：' -ForegroundColor Red
    if (Test-Path $outLog) { Get-Content $outLog -Tail 20 -Encoding UTF8 | ForEach-Object { Write-Host ('      ' + $_) } }
    if (Test-Path $errLog) { Get-Content $errLog -Tail 10 -Encoding UTF8 | ForEach-Object { Write-Host ('      ' + $_) } }
    Wait-Key
    exit 1
}

$cost = [int]((Get-Date) - $started).TotalSeconds
Write-Host ('  [OK] 启动成功（用了 ' + $cost + ' 秒）')
Write-Host ''
Write-Host '=============================================='
Write-Host ('  打开这个地址：' + $base)
Write-Host ('    管理后台     ' + $base + '/admin.html')
Write-Host ('    AI 客服      ' + $base + '/chat.html')
Write-Host ('    接口文档      ' + $base + '/swagger-ui.html')
Write-Host ''
Write-Host '  演示账号（密码都是 123456）：'
Write-Host '    alice       普通用户（推荐先登这个）'
Write-Host '    bob_2026    普通用户'
Write-Host '    demo_admin  管理员（登录后导航栏出现「管理后台」）'
Write-Host ''
Write-Host '  停止服务：双击 tools\stop.bat'
Write-Host '=============================================='
if (-not $NoBrowser) { Start-Process $base }
