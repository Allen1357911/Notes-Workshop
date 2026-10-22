param(
    [switch]$NoPause
)
# ============================================================
# 停止笔记工坊（关闭占用 8080 端口的 java 进程）
#   双击 tools\stop.bat 即可；命令行：
#     powershell -ExecutionPolicy Bypass -File tools\stop.ps1
#   注意：本文件必须保存为 UTF-8 with BOM，否则 PowerShell 会按 ANSI 读、中文乱码
# ============================================================
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$conn = $null
try { $conn = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue } catch { }

if (-not $conn) {
    Write-Host '8080 没有监听，服务本来就没在运行。'
    exit 0
}

$pids = @($conn | Select-Object -ExpandProperty OwningProcess | Sort-Object -Unique)
foreach ($processId in $pids) {
    Write-Host ('正在停止进程 PID ' + $processId + ' ...')
    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 2

$still = $null
try { $still = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue } catch { }
if ($still) {
    Write-Host '停止失败：8080 仍被占用，请用任务管理器结束该 java 进程。' -ForegroundColor Red
    if (-not $NoPause) { Start-Sleep -Seconds 3 }
    exit 1
}
Write-Host '已停止。'
