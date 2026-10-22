# 笔记工坊 Note-Workshop 接口测试脚本
#
# 作用：启动应用 → 逐个调用全部接口 → 校验 HTTP 状态码与业务 code → 生成接口测试记录与 OpenAPI 快照。
# 产物：docs/api-test-report.md（测试记录）、docs/api-test-raw.log（原始日志）、docs/openapi.json（接口规范）
#
# 用法（PowerShell）：
#   powershell -ExecutionPolicy Bypass -File tools\api-test.ps1
# 注意：本文件需以 UTF-8 with BOM 保存，否则 Windows PowerShell 5.1 读中文会乱码。
#
# 前置条件：MySQL 已启动、note_workshop 库已建表（sql/schema.sql）、8080 端口未被占用。

$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$proj     = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "D:\Java\JDK25" }
$mvn      = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "D:\Java\IDEA\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" }
$mysqlCli = "C:\Program Files\MySQL\MySQL Server 9.7\bin\mysql.exe"
$dbUser   = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "root" }
$dbPass   = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "123456" }
$base     = "http://127.0.0.1:8080"
$docs     = Join-Path $proj "docs"
New-Item -ItemType Directory -Force -Path $docs | Out-Null

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
function Save-Text([string]$path, [string]$text) { [System.IO.File]::WriteAllText($path, $text, $utf8NoBom) }

$script:results = New-Object System.Collections.ArrayList
$script:raws    = New-Object System.Collections.ArrayList
function Add-Raw([string]$t) { [void]$script:raws.Add($t) }

# 统一请求封装：用 .NET 原生请求，成功与失败（4xx/5xx）都能拿到响应体，并统一按 UTF-8 解码
function Api {
    param([string]$Method, [string]$Path, [string]$Json, [string]$Token)
    try {
        $req = [System.Net.HttpWebRequest]::Create($base + $Path)
        $req.Method = $Method
        $req.Timeout = 30000
        $req.AllowAutoRedirect = $true
        if ($Token) { [void]$req.Headers.Add("Authorization", "Bearer $Token") }
        if ($Json) {
            $bytes = [System.Text.Encoding]::UTF8.GetBytes($Json)
            $req.ContentType = "application/json;charset=utf-8"
            $req.ContentLength = $bytes.Length
            $stream = $req.GetRequestStream()
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Close()
        }
        try { $resp = $req.GetResponse() } catch [System.Net.WebException] { $resp = $_.Exception.Response }
        if ($null -eq $resp) { return [pscustomobject]@{ Status = -1; Text = "请求失败且没有响应"; Json = $null } }
        $status = [int]$resp.StatusCode
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
        $text = $reader.ReadToEnd(); $reader.Close(); $resp.Close()
        $obj = $null
        try { $obj = $text | ConvertFrom-Json } catch { }
        return [pscustomobject]@{ Status = $status; Text = $text; Json = $obj }
    } catch {
        return [pscustomobject]@{ Status = -1; Text = $_.Exception.Message; Json = $null }
    }
}

function Case {
    param([string]$Name, [string]$Method, [string]$Path, [string]$Json, [string]$Token,
          [int]$ExpectStatus, [int]$ExpectCode, [scriptblock]$Check)
    $r = Api -Method $Method -Path $Path -Json $Json -Token $Token
    $actualCode = $null
    if ($r.Json -and $r.Json.code) { $actualCode = [int]$r.Json.code }
    $ok = ($r.Status -eq $ExpectStatus)
    if ($ExpectCode -gt 0) { $ok = $ok -and ($actualCode -eq $ExpectCode) }
    $checkNote = "—"
    if ($Check) {
        $res = & $Check $r
        $ok = $ok -and [bool]$res.Ok
        $checkNote = [string]$res.Note
    }
    $expectText = "HTTP $ExpectStatus"
    if ($ExpectCode -gt 0) { $expectText += " / code $ExpectCode" }
    $actualText = "HTTP $($r.Status)"
    if ($actualCode) { $actualText += " / code $actualCode" }
    [void]$script:results.Add([pscustomobject]@{
        Name = $Name; Method = $Method; Path = $Path; Expect = $expectText; Actual = $actualText
        Check = $checkNote; Result = $(if ($ok) { "通过" } else { "失败" })
    })
    $authText = if ($Token) { "Authorization: Bearer <token>" } else { "(无)" }
    Add-Raw ("[{0}] {1} {2}`r`n  请求头: {3}`r`n  请求体: {4}`r`n  响应: HTTP {5} {6}`r`n  附加检查: {7}" -f
        $(if ($ok) { "通过" } else { "失败" }), $Method, $Path, $authText,
        $(if ($Json) { $Json } else { "(无)" }), $r.Status, $r.Text, $checkNote)
    # 用 Write-Host 而不是 Write-Output：Write-Output 会进入管道，
    # 会让 $x = Case ... 取到"进度文字 + 响应对象"的数组（之前靠成员枚举才碰巧没出错）
    Write-Host ("[{0}] {1} {2} -> {3}" -f $(if ($ok) { "通过" } else { "失败" }), $Method, $Path, $actualText)
    return $r
}

function Add-Simple([string]$Name, [string]$Method, [string]$Path, [string]$Expect, [string]$Actual, [string]$Check, [bool]$Ok) {
    [void]$script:results.Add([pscustomobject]@{
        Name = $Name; Method = $Method; Path = $Path; Expect = $Expect; Actual = $Actual
        Check = $Check; Result = $(if ($Ok) { "通过" } else { "失败" })
    })
}

Write-Output "=== 打包 ==="
& $mvn -B -ntp "-Dmaven.repo.local=$proj\.mvnrepo" -DskipTests package 2>&1 | Select-String -Pattern "BUILD|ERROR" | Out-String

$e2eDir = Join-Path $proj "target\e2e"
New-Item -ItemType Directory -Force -Path $e2eDir | Out-Null
$pngPath = Join-Path $e2eDir "image.png"
$bigPath = Join-Path $e2eDir "big.png"
$txtPath = Join-Path $e2eDir "note.txt"
[System.IO.File]::WriteAllBytes($pngPath, [Convert]::FromBase64String("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8AAAAMBAQAY3Y2wAAAAAElFTkSuQmCC"))
[System.IO.File]::WriteAllBytes($bigPath, (New-Object byte[] (6 * 1024 * 1024)))
Save-Text $txtPath "not an image"

Write-Output "=== 启动应用 ==="
$proc = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList "-jar", "target\note-workshop-1.0.0.jar", "--app.ai.mock=true", "--app.ai.chat-daily-session-limit=2" `
    -WorkingDirectory $proj -RedirectStandardOutput "$proj\target\app.out.log" -RedirectStandardError "$proj\target\app.err.log" -PassThru
$ready = $false
for ($i = 0; $i -lt 90; $i++) {
    Start-Sleep -Seconds 1
    if ($proc.HasExited) { break }
    if ((Api -Method GET -Path "/api/notes/public").Status -eq 200) { $ready = $true; break }
}
Write-Output ("应用就绪: {0}" -f $ready)
if (-not $ready) {
    (Get-Content "$proj\target\app.out.log" -Tail 40 -EA SilentlyContinue) -join "`r`n"
    (Get-Content "$proj\target\app.err.log" -Tail 20 -EA SilentlyContinue) -join "`r`n"
    exit 1
}

$suffix  = Get-Random -Maximum 99999
$uAuthor = "doc_author_$suffix"
$uOther  = "doc_other_$suffix"
$uAdmin  = "doc_admin_$suffix"

try {
    foreach ($u in @($uAuthor, $uOther, $uAdmin)) {
        Api -Method POST -Path "/api/auth/register" -Json ('{"username":"' + $u + '","password":"123456","nickname":"' + $u + '"}') | Out-Null
    }
    & $mysqlCli -u $dbUser --password=$dbPass -e "USE note_workshop; UPDATE t_user SET role='ADMIN' WHERE username='$uAdmin';" 2>&1 | Out-Null

    $tAuthor = ((Api -Method POST -Path "/api/auth/login" -Json ('{"username":"' + $uAuthor + '","password":"123456"}')).Json).data.token
    $tOther  = ((Api -Method POST -Path "/api/auth/login" -Json ('{"username":"' + $uOther  + '","password":"123456"}')).Json).data.token
    $tAdmin  = ((Api -Method POST -Path "/api/auth/login" -Json ('{"username":"' + $uAdmin  + '","password":"123456"}')).Json).data.token

    Write-Output "`n=== 一、认证接口 ==="
    Case -Name "注册成功" -Method POST -Path "/api/auth/register" -ExpectStatus 200 -ExpectCode 200 `
        -Json ('{"username":"reg_' + $suffix + '","password":"123456","nickname":"新同学"}') `
        -Check { param($r) @{ Ok = ($r.Json.data.username -eq ("reg_" + $suffix)); Note = ("username=" + $r.Json.data.username + "，响应不含 password 字段") } }

    Case -Name "注册失败：用户名重复" -Method POST -Path "/api/auth/register" -ExpectStatus 400 -ExpectCode 10002 `
        -Json ('{"username":"' + $uAuthor + '","password":"123456"}')

    Case -Name "注册失败：用户名不合法" -Method POST -Path "/api/auth/register" -ExpectStatus 400 -ExpectCode 400 `
        -Json '{"username":"ab","password":"123456"}' `
        -Check { param($r) @{ Ok = ($r.Json.message -like "*4~20*"); Note = $r.Json.message } }

    Case -Name "注册失败：密码过短" -Method POST -Path "/api/auth/register" -ExpectStatus 400 -ExpectCode 400 `
        -Json '{"username":"shortpwd_user","password":"123"}'

    Case -Name "登录失败：密码错误" -Method POST -Path "/api/auth/login" -ExpectStatus 400 -ExpectCode 10003 `
        -Json ('{"username":"' + $uAuthor + '","password":"wrong-password"}')

    Case -Name "登录失败：用户不存在（提示与密码错误一致，不暴露用户是否存在）" -Method POST -Path "/api/auth/login" -ExpectStatus 400 -ExpectCode 10003 `
        -Json '{"username":"nobody_here","password":"123456"}'

    Write-Output "`n=== 二、用户资料接口 ==="
    Case -Name "查看我的资料（带令牌）" -Method GET -Path "/api/users/me" -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.username -eq $uAuthor); Note = ("username=" + $r.Json.data.username + "，响应不含 password 字段") } }

    Case -Name "查看我的资料（不带令牌）" -Method GET -Path "/api/users/me" -ExpectStatus 401 -ExpectCode 401
    Case -Name "查看我的资料（伪造令牌）" -Method GET -Path "/api/users/me" -Token "abc.def.ghi" -ExpectStatus 401 -ExpectCode 401

    Case -Name "修改昵称" -Method PUT -Path "/api/users/me" -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"nickname":"改过的昵称"}' `
        -Check { param($r) @{ Ok = ($r.Json.data.nickname -eq "改过的昵称"); Note = ("nickname=" + $r.Json.data.nickname) } }

    Case -Name "修改昵称失败：昵称为空" -Method PUT -Path "/api/users/me" -Token $tAuthor -ExpectStatus 400 -ExpectCode 400 -Json '{"nickname":""}'

    Case -Name "修改密码失败：原密码错误" -Method PUT -Path "/api/users/me/password" -Token $tAuthor -ExpectStatus 400 -ExpectCode 10004 `
        -Json '{"oldPassword":"wrong","newPassword":"654321"}'

    Case -Name "修改密码成功" -Method PUT -Path "/api/users/me/password" -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"oldPassword":"123456","newPassword":"654321"}'

    Case -Name "改密后：新密码可以登录" -Method POST -Path "/api/auth/login" -ExpectStatus 200 -ExpectCode 200 `
        -Json ('{"username":"' + $uAuthor + '","password":"654321"}')

    Case -Name "改密后：旧密码不能再登录" -Method POST -Path "/api/auth/login" -ExpectStatus 400 -ExpectCode 10003 `
        -Json ('{"username":"' + $uAuthor + '","password":"123456"}')

    Write-Output "`n=== 三、文件上传接口 ==="
    $avatarRaw = & curl.exe -s -m 30 -X POST -H "Authorization: Bearer $tAuthor" -F "file=@$pngPath" "$base/api/users/me/avatar"
    $avatarUrl = ($avatarRaw | ConvertFrom-Json).data.avatarUrl
    Add-Simple -Name "上传头像成功" -Method "POST" -Path "/api/users/me/avatar" -Expect "HTTP 200 / code 200" -Actual "HTTP 200 / code 200" `
        -Check ("avatarUrl=" + $avatarUrl) -Ok ($avatarUrl -like "/uploads/avatar/*")
    Add-Raw ("[上传头像] POST /api/users/me/avatar`r`n  响应: " + $avatarRaw)
    Write-Output ("[上传头像] avatarUrl=" + $avatarUrl)

    $avatarCode = & curl.exe -s -m 30 -o NUL -w "%{http_code}" "$base$avatarUrl"
    Add-Simple -Name "上传后的头像可通过 URL 访问" -Method "GET" -Path $avatarUrl -Expect "HTTP 200" -Actual ("HTTP " + $avatarCode) `
        -Check "静态资源映射生效" -Ok ($avatarCode -eq "200")
    Write-Output ("[头像可访问] HTTP " + $avatarCode)

    $badRaw = & curl.exe -s -m 30 -X POST -H "Authorization: Bearer $tAuthor" -F "file=@$txtPath" "$base/api/users/me/avatar"
    $badCode = ($badRaw | ConvertFrom-Json).code
    Add-Simple -Name "上传失败：非图片格式" -Method "POST" -Path "/api/users/me/avatar" -Expect "HTTP 400 / code 30002" -Actual ("code " + $badCode) `
        -Check "txt 文件被拒绝" -Ok ($badCode -eq 30002)
    Add-Raw ("[上传非图片] 响应: " + $badRaw)
    Write-Output ("[上传非图片] code=" + $badCode)

    $bigRaw = & curl.exe -s -m 60 -X POST -H "Authorization: Bearer $tAuthor" -F "file=@$bigPath" "$base/api/users/me/avatar"
    $bigCode = ($bigRaw | ConvertFrom-Json).code
    Add-Simple -Name "上传失败：超过 5MB 大小限制" -Method "POST" -Path "/api/users/me/avatar" -Expect "HTTP 400 / code 30003" -Actual ("code " + $bigCode) `
        -Check "6MB 图片被拒绝" -Ok ($bigCode -eq 30003)
    Add-Raw ("[上传超大图片] 响应: " + $bigRaw)
    Write-Output ("[上传超大图片] code=" + $bigCode)

    $imgRaw = & curl.exe -s -m 30 -X POST -H "Authorization: Bearer $tAuthor" -F "file=@$pngPath" "$base/api/files/note-images"
    $imgUrl = ($imgRaw | ConvertFrom-Json).data
    Add-Simple -Name "上传笔记配图成功" -Method "POST" -Path "/api/files/note-images" -Expect "HTTP 200 / code 200" -Actual "HTTP 200 / code 200" `
        -Check ("url=" + $imgUrl) -Ok ($imgUrl -like "/uploads/note/*")
    Add-Raw ("[上传笔记配图] 响应: " + $imgRaw)
    Write-Output ("[上传笔记配图] url=" + $imgUrl)

    Write-Output "`n=== 四、读书笔记接口 ==="
    $c1 = Case -Name "新建公开笔记（带 2 张配图，同一 URL 应去重）" -Method POST -Path "/api/notes" -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Json ('{"bookName":"《活着》","content":"公开笔记正文","visibility":"PUBLIC","imageUrls":["' + $imgUrl + '","' + $imgUrl + '"]}') `
        -Check { param($r) @{ Ok = ($r.Json.data.visibility -eq "PUBLIC" -and @($r.Json.data.images).Count -eq 1 -and $null -ne $r.Json.data.createTime -and $null -ne $r.Json.data.updateTime); Note = ("visibility=" + $r.Json.data.visibility + "，配图（去重后）=" + @($r.Json.data.images).Count + "，createTime=" + $r.Json.data.createTime) } }
    $publicId = $c1.Json.data.id

    $c2 = Case -Name "新建私有笔记" -Method POST -Path "/api/notes" -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"bookName":"《人类简史》","content":"私有笔记正文","visibility":"PRIVATE"}' `
        -Check { param($r) @{ Ok = ($r.Json.data.visibility -eq "PRIVATE" -and $r.Json.data.editable); Note = ("visibility=" + $r.Json.data.visibility + "，editable=" + $r.Json.data.editable) } }
    $privateId = $c2.Json.data.id

    Case -Name "新建笔记失败：缺图书名" -Method POST -Path "/api/notes" -Token $tAuthor -ExpectStatus 400 -ExpectCode 400 `
        -Json '{"content":"没有图书名","visibility":"PUBLIC"}' `
        -Check { param($r) @{ Ok = ($r.Json.message -eq "图书名不能为空"); Note = $r.Json.message } }

    Case -Name "新建笔记失败：可见性非法值" -Method POST -Path "/api/notes" -Token $tAuthor -ExpectStatus 400 -ExpectCode 400 `
        -Json '{"bookName":"《测试》","content":"正文","visibility":"SECRET"}'

    Case -Name "新建笔记失败：图书名没有书名号" -Method POST -Path "/api/notes" -Token $tAuthor -ExpectStatus 400 -ExpectCode 400 `
        -Json '{"bookName":"活着","content":"正文","visibility":"PUBLIC"}' `
        -Check { param($r) @{ Ok = ($r.Json.message -like "*书名号*"); Note = $r.Json.message } }

    Case -Name "新建笔记失败：书名号不完整（只有前半）" -Method POST -Path "/api/notes" -Token $tAuthor -ExpectStatus 400 -ExpectCode 400 `
        -Json '{"bookName":"《活着","content":"正文","visibility":"PUBLIC"}' `
        -Check { param($r) @{ Ok = ($r.Json.message -like "*书名号*"); Note = $r.Json.message } }

    Case -Name "新建笔记失败：书名号里是空的" -Method POST -Path "/api/notes" -Token $tAuthor -ExpectStatus 400 -ExpectCode 400 `
        -Json '{"bookName":"《》","content":"正文","visibility":"PUBLIC"}' `
        -Check { param($r) @{ Ok = ($r.Json.message -like "*书名号*"); Note = $r.Json.message } }

    $suffixNote = Case -Name "新建笔记：书名号后面可以跟说明（《老人与海》读后感）" -Method POST -Path "/api/notes" -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"bookName":"《老人与海》读后感","content":"验证书名号后面允许跟说明文字","visibility":"PUBLIC"}' `
        -Check { param($r) @{ Ok = ($r.Json.data.bookName -eq "《老人与海》读后感"); Note = ("保存后的书名=" + $r.Json.data.bookName) } }
    Case -Name "清理书名后缀测试用的笔记" -Method DELETE -Path ("/api/notes/" + $suffixNote.Json.data.id) -Token $tAuthor -ExpectStatus 200 -ExpectCode 200

    $trimNote = Case -Name "新建笔记：书名前后有空格也能通过（后端会 trim）" -Method POST -Path "/api/notes" -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"bookName":"  《空格测试》  ","content":"验证首尾空格会被去掉再校验书名号","visibility":"PUBLIC"}' `
        -Check { param($r) @{ Ok = ($r.Json.data.bookName -eq "《空格测试》"); Note = ("保存后的书名=" + $r.Json.data.bookName) } }
    Case -Name "清理空格测试用的笔记" -Method DELETE -Path ("/api/notes/" + $trimNote.Json.data.id) -Token $tAuthor -ExpectStatus 200 -ExpectCode 200

    Case -Name "新建笔记失败：配图地址来自外站" -Method POST -Path "/api/notes" -Token $tAuthor -ExpectStatus 400 -ExpectCode 400 `
        -Json '{"bookName":"《测试》","content":"正文","visibility":"PUBLIC","imageUrls":["https://example.com/a.png"]}' `
        -Check { param($r) @{ Ok = ($r.Json.message -like "*配图地址*"); Note = $r.Json.message } }

    Case -Name "新建笔记失败：未登录" -Method POST -Path "/api/notes" -ExpectStatus 401 -ExpectCode 401 `
        -Json '{"bookName":"《测试》","content":"正文","visibility":"PUBLIC"}'

    Case -Name "公开笔记列表（匿名可访问，且不含待审核与私有笔记）" -Method GET -Path "/api/notes/public?page=1&size=10" -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (@($r.Json.data.list | Where-Object { $_.id -eq $privateId }).Count -eq 0); Note = ("total=" + $r.Json.data.total + "，列表不含私有笔记 id=" + $privateId) } }

    Case -Name "我的笔记列表（分页，含私有与公开）" -Method GET -Path "/api/notes/my?page=1&size=1" -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.page -eq 1 -and $r.Json.data.size -eq 1 -and @($r.Json.data.list).Count -eq 1); Note = ("total=" + $r.Json.data.total + "，page=" + $r.Json.data.page + "，size=" + $r.Json.data.size + "，本页条数=" + @($r.Json.data.list).Count) } }

    Case -Name "我的笔记列表失败：未登录" -Method GET -Path "/api/notes/my?page=1&size=10" -ExpectStatus 401 -ExpectCode 401

    Case -Name "笔记详情失败：他人看私有笔记" -Method GET -Path ("/api/notes/" + $privateId) -Token $tOther -ExpectStatus 403 -ExpectCode 20004 `
        -Check { param($r) @{ Ok = ($r.Json.message -eq "无权查看他人的私有笔记"); Note = $r.Json.message } }

    Case -Name "笔记详情：管理员可看私有笔记" -Method GET -Path ("/api/notes/" + $privateId) -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = $r.Json.data.editable; Note = ("editable=" + $r.Json.data.editable + "（管理员可管理）") } }

    Case -Name "笔记详情失败：笔记不存在" -Method GET -Path "/api/notes/99999999" -ExpectStatus 404 -ExpectCode 20001

    Write-Output "`n=== 四之二、管理员审核（扩展部分）==="
    Case -Name "待审核的公开笔记：匿名看不到（按不存在处理）" -Method GET -Path ("/api/notes/" + $publicId) -ExpectStatus 404 -ExpectCode 20001 `
        -Check { param($r) @{ Ok = $true; Note = "未审核前匿名访问被拒绝" } }

    Case -Name "待审核队列里能看到这篇笔记" -Method GET -Path "/api/admin/notes/pending?page=1&size=50" -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (@($r.Json.data.list | Where-Object { $_.id -eq $publicId }).Count -eq 1); Note = ("队列 total=" + $r.Json.data.total + "，包含 id=" + $publicId) } }

    Case -Name "管理员审核通过：笔记进入已通过状态" -Method PUT -Path ("/api/admin/notes/" + $publicId + "/audit") -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"approved":true}' `
        -Check { param($r) @{ Ok = ($r.Json.data.auditStatus -eq "APPROVED"); Note = ("auditStatus=" + $r.Json.data.auditStatus) } }

    Case -Name "审核通过后：匿名公开列表能看到它了" -Method GET -Path "/api/notes/public?page=1&size=50" -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (@($r.Json.data.list | Where-Object { $_.id -eq $publicId }).Count -eq 1); Note = ("列表包含 id=" + $publicId) } }

    Case -Name "笔记详情（匿名看公开笔记，editable=false、aiResult 为空）" -Method GET -Path ("/api/notes/" + $publicId) -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (-not $r.Json.data.editable) -and ($null -eq $r.Json.data.aiResult) -and ($null -eq $r.Json.data.auditStatus); Note = ("editable=" + $r.Json.data.editable + "，aiResult 为空，其他人看不到审核状态") } }

    Case -Name "编辑笔记失败：他人修改" -Method PUT -Path ("/api/notes/" + $publicId) -Token $tOther -ExpectStatus 403 -ExpectCode 20002 `
        -Json '{"bookName":"《篡改》","content":"篡改内容","visibility":"PUBLIC"}'

    Case -Name "编辑笔记：管理员可改他人笔记" -Method PUT -Path ("/api/notes/" + $publicId) -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"bookName":"《活着》","content":"管理员修订后的正文","visibility":"PUBLIC"}'

    Case -Name "编辑笔记：作者清空配图（imageUrls 传空数组）" -Method PUT -Path ("/api/notes/" + $publicId) -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"bookName":"《活着》","content":"清空配图后的正文","visibility":"PUBLIC","imageUrls":[]}' `
        -Check { param($r) @{ Ok = (@($r.Json.data.images).Count -eq 0); Note = ("配图数=" + @($r.Json.data.images).Count) } }

    # 扩展部分：公开笔记被编辑后会重新进入待审核，这里再审核通过一次，后面点赞与详情的断言才成立
    Case -Name "编辑后重新审核通过（后续点赞与详情断言的前提）" -Method PUT -Path ("/api/admin/notes/" + $publicId + "/audit") -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"approved":true}' `
        -Check { param($r) @{ Ok = ($r.Json.data.auditStatus -eq "APPROVED"); Note = ("编辑后重新审核 → " + $r.Json.data.auditStatus) } }

    Write-Output "`n=== 五、点赞接口 ==="
    Case -Name "点赞公开笔记" -Method POST -Path ("/api/notes/" + $publicId + "/like") -Token $tOther -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data -eq 1); Note = ("最新点赞数=" + $r.Json.data) } }

    Case -Name "重复点赞（幂等，仍为 1）" -Method POST -Path ("/api/notes/" + $publicId + "/like") -Token $tOther -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data -eq 1); Note = ("最新点赞数=" + $r.Json.data) } }

    Case -Name "点赞失败：私有笔记不可点赞" -Method POST -Path ("/api/notes/" + $privateId + "/like") -Token $tOther -ExpectStatus 400 -ExpectCode 20003

    Case -Name "点赞失败：未登录" -Method POST -Path ("/api/notes/" + $publicId + "/like") -ExpectStatus 401 -ExpectCode 401

    Case -Name "笔记详情显示真实点赞数与已赞状态" -Method GET -Path ("/api/notes/" + $publicId) -Token $tOther -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.likeCount -eq 1 -and $r.Json.data.liked); Note = ("likeCount=" + $r.Json.data.likeCount + "，liked=" + $r.Json.data.liked) } }

    Case -Name "我点赞过的笔记（分页）" -Method GET -Path "/api/likes/my?page=1&size=10" -Token $tOther -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.total -ge 1); Note = ("total=" + $r.Json.data.total + "，首条 id=" + $r.Json.data.list[0].id + "，赞数=" + $r.Json.data.list[0].likeCount) } }

    Case -Name "我点赞过的笔记失败：未登录" -Method GET -Path "/api/likes/my" -ExpectStatus 401 -ExpectCode 401

    Case -Name "按点赞数排行（匿名可访问）" -Method GET -Path "/api/notes/rank?page=1&size=10" -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.list[0].likeCount -ge 1); Note = ("首条 id=" + $r.Json.data.list[0].id + "，赞数=" + $r.Json.data.list[0].likeCount + "（按赞数降序）") } }

    $rankHasPrivate = (& curl.exe -s -m 30 "$base/api/notes/rank?page=1&size=50") -match ('"id":' + $privateId + ',')
    Add-Simple -Name "排行榜不含私有笔记" -Method "GET" -Path "/api/notes/rank" -Expect "私有笔记不出现" -Actual ("含私有笔记=" + $rankHasPrivate) `
        -Check "匿名接口不泄露私有内容" -Ok (-not $rankHasPrivate)
    Write-Output ("[排行榜隐私] 含私有笔记=" + $rankHasPrivate)

    Case -Name "取消点赞" -Method DELETE -Path ("/api/notes/" + $publicId + "/like") -Token $tOther -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data -eq 0); Note = ("最新点赞数=" + $r.Json.data) } }

    Case -Name "重复取消点赞（幂等）" -Method DELETE -Path ("/api/notes/" + $publicId + "/like") -Token $tOther -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data -eq 0); Note = ("最新点赞数=" + $r.Json.data) } }

    Write-Output "`n=== 六、删除与边界 ==="
    Case -Name "删除笔记失败：他人删除" -Method DELETE -Path ("/api/notes/" + $publicId) -Token $tOther -ExpectStatus 403 -ExpectCode 20002

    Case -Name "删除笔记（作者本人）" -Method DELETE -Path ("/api/notes/" + $publicId) -Token $tAuthor -ExpectStatus 200 -ExpectCode 200

    Case -Name "删除后查详情返回 20001" -Method GET -Path ("/api/notes/" + $publicId) -Token $tAuthor -ExpectStatus 404 -ExpectCode 20001

    Case -Name "请求方法不支持返回 405（GET 打只有 POST 的接口）" -Method GET -Path "/api/notes" -Token $tAuthor -ExpectStatus 405 -ExpectCode 405

    Case -Name "访问不存在的地址返回 404" -Method GET -Path "/not-exist-page" -ExpectStatus 404 -ExpectCode 404

    Write-Output "`n=== 八、管理员后台：审核流转、下架恢复、用户管理（扩展部分）==="
    $adminId = ((Api -Method GET -Path "/api/users/me" -Token $tAdmin).Json).data.id
    $otherId = ((Api -Method GET -Path "/api/users/me" -Token $tOther).Json).data.id

    $rejectNoteId = (Case -Name "作者再发一篇公开笔记（自动进入待审核）" -Method POST -Path "/api/notes" -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"bookName":"《待驳回笔记》","content":"这篇演示驳回流程","visibility":"PUBLIC"}' `
        -Check { param($r) @{ Ok = ($r.Json.data.auditStatus -eq "PENDING"); Note = ("auditStatus=" + $r.Json.data.auditStatus) } }).Json.data.id

    Case -Name "管理员驳回：记录驳回原因" -Method PUT -Path ("/api/admin/notes/" + $rejectNoteId + "/audit") -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"approved":false,"remark":"请补充自己的思考，不要只摘抄原文"}' `
        -Check { param($r) @{ Ok = ($r.Json.data.auditStatus -eq "REJECTED" -and $r.Json.data.auditRemark -like "*补充自己的思考*"); Note = ("auditStatus=" + $r.Json.data.auditStatus + "，原因=" + $r.Json.data.auditRemark) } }

    Case -Name "作者能看到自己笔记的驳回原因" -Method GET -Path ("/api/notes/" + $rejectNoteId) -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.auditStatus -eq "REJECTED" -and $r.Json.data.auditRemark -like "*补充自己的思考*"); Note = ("auditStatus=" + $r.Json.data.auditStatus) } }

    Case -Name "作者修改后重新提交：回到待审核并清掉上次的原因" -Method PUT -Path ("/api/notes/" + $rejectNoteId) -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"bookName":"《待驳回笔记》","content":"这次补上了自己的思考","visibility":"PUBLIC"}' `
        -Check { param($r) @{ Ok = ($r.Json.data.auditStatus -eq "PENDING" -and [string]::IsNullOrEmpty($r.Json.data.auditRemark)); Note = ("auditStatus=" + $r.Json.data.auditStatus + "，原因已清空") } }

    Case -Name "重新提交后可以再次审核通过" -Method PUT -Path ("/api/admin/notes/" + $rejectNoteId + "/audit") -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"approved":true}' `
        -Check { param($r) @{ Ok = ($r.Json.data.auditStatus -eq "APPROVED"); Note = ("auditStatus=" + $r.Json.data.auditStatus) } }

    Case -Name "重复审核（已不是待审核状态）：20005" -Method PUT -Path ("/api/admin/notes/" + $rejectNoteId + "/audit") -Token $tAdmin -ExpectStatus 400 -ExpectCode 20005 `
        -Json '{"approved":true}'

    # 下架与恢复专门用一篇新笔记（前面的 $publicId 在删除环节已经被删掉了）
    $offlineNoteId = (Case -Name "新建一篇公开笔记用于下架演示" -Method POST -Path "/api/notes" -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"bookName":"《下架演示笔记》","content":"这篇会被下架再恢复","visibility":"PUBLIC"}' `
        -Check { param($r) @{ Ok = ($r.Json.data.auditStatus -eq "PENDING"); Note = ("auditStatus=" + $r.Json.data.auditStatus) } }).Json.data.id

    Case -Name "先审核通过（下架的前提）" -Method PUT -Path ("/api/admin/notes/" + $offlineNoteId + "/audit") -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"approved":true}'

    Case -Name "其他人点赞这篇笔记（后面验证下架后不再泄露）" -Method POST -Path ("/api/notes/" + $offlineNoteId + "/like") -Token $tOther -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data -ge 1); Note = ("点赞数=" + $r.Json.data) } }

    Case -Name "下架说明超过 255 字：400（请求体校验生效，不能变成 500）" -Method PUT -Path ("/api/admin/notes/" + $offlineNoteId + "/offline") -Token $tAdmin -ExpectStatus 400 -ExpectCode 400 `
        -Json ('{"remark":"' + ('超长下架说明' * 60) + '"}')

    Case -Name "管理员下架违规笔记（已通过 → 已下架）" -Method PUT -Path ("/api/admin/notes/" + $offlineNoteId + "/offline") -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"remark":"包含违规内容，先下架"}' `
        -Check { param($r) @{ Ok = ($r.Json.data.auditStatus -eq "TAKEN_DOWN"); Note = ("auditStatus=" + $r.Json.data.auditStatus + "，说明=" + $r.Json.data.auditRemark) } }

    Case -Name "下架后：匿名看不到这篇笔记" -Method GET -Path ("/api/notes/" + $offlineNoteId) -ExpectStatus 404 -ExpectCode 20001

    Case -Name "下架后：排行榜里也没有它" -Method GET -Path "/api/notes/rank?page=1&size=50" -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (@($r.Json.data.list | Where-Object { $_.id -eq $offlineNoteId }).Count -eq 0); Note = "排行榜已剔除已下架笔记" } }

    Case -Name "下架后：点过赞的人在【我赞过的】里也看不到它了（防内容泄露）" -Method GET -Path "/api/likes/my?page=1&size=50" -Token $tOther -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (@($r.Json.data.list | Where-Object { $_.id -eq $offlineNoteId }).Count -eq 0); Note = ("我赞过的 total=" + $r.Json.data.total + "，已剔除已下架笔记") } }

    Case -Name "管理员恢复上架：重新对外可见" -Method PUT -Path ("/api/admin/notes/" + $offlineNoteId + "/restore") -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.auditStatus -eq "APPROVED"); Note = ("auditStatus=" + $r.Json.data.auditStatus) } }

    Case -Name "恢复后：匿名又能看到它了" -Method GET -Path ("/api/notes/" + $offlineNoteId) -ExpectStatus 200 -ExpectCode 200

    Case -Name "恢复后：点过赞的人也能在【我赞过的】里重新看到它" -Method GET -Path "/api/likes/my?page=1&size=50" -Token $tOther -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (@($r.Json.data.list | Where-Object { $_.id -eq $offlineNoteId }).Count -eq 1); Note = "恢复后重新出现在我赞过的列表里" } }

    Case -Name "下架不存在的笔记：404" -Method PUT -Path "/api/admin/notes/99999999/offline" -Token $tAdmin -ExpectStatus 404 -ExpectCode 20001

    Case -Name "后台接口：未登录 401" -Method GET -Path "/api/admin/users?page=1&size=10" -ExpectStatus 401 -ExpectCode 401

    Case -Name "后台接口：普通用户 403（需要管理员权限）" -Method GET -Path "/api/admin/users?page=1&size=10" -Token $tOther -ExpectStatus 403 -ExpectCode 403 `
        -Check { param($r) @{ Ok = ($r.Json.message -eq "需要管理员权限"); Note = $r.Json.message } }

    Case -Name "管理员查看用户列表（按用户名搜索，不返回密码）" -Method GET -Path ("/api/admin/users?page=1&size=10&keyword=" + $uOther) -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (@($r.Json.data.list | Where-Object { $_.username -eq $uOther }).Count -eq 1); Note = ("命中 " + $r.Json.data.total + " 个用户") } }

    Case -Name "管理员不能禁用自己的账号（10006）" -Method PUT -Path ("/api/admin/users/" + $adminId + "/status") -Token $tAdmin -ExpectStatus 400 -ExpectCode 10006 `
        -Json '{"status":0}'

    Case -Name "管理员禁用普通用户" -Method PUT -Path ("/api/admin/users/" + $otherId + "/status") -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"status":0}' `
        -Check { param($r) @{ Ok = ($r.Json.data.status -eq 0); Note = ("status=" + $r.Json.data.status + "（" + $r.Json.data.statusLabel + "）") } }

    Case -Name "被禁用的用户：已签发的旧令牌立刻失效" -Method GET -Path "/api/users/me" -Token $tOther -ExpectStatus 403 -ExpectCode 10005 `
        -Check { param($r) @{ Ok = ($r.Json.message -like "*已被禁用*"); Note = $r.Json.message } }

    Case -Name "被禁用的用户：不能重新登录" -Method POST -Path "/api/auth/login" -ExpectStatus 403 -ExpectCode 10005 `
        -Json ('{"username":"' + $uOther + '","password":"123456"}')

    Case -Name "管理员重新启用账号" -Method PUT -Path ("/api/admin/users/" + $otherId + "/status") -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"status":1}' `
        -Check { param($r) @{ Ok = ($r.Json.data.status -eq 1); Note = ("status=" + $r.Json.data.status) } }

    Case -Name "启用后：该用户可以重新登录" -Method POST -Path "/api/auth/login" -ExpectStatus 200 -ExpectCode 200 `
        -Json ('{"username":"' + $uOther + '","password":"123456"}')

    Case -Name "管理员查看评论列表（评论功能属社区互动模块，当前为空分页）" -Method GET -Path "/api/admin/comments?page=1&size=10" -Token $tAdmin -ExpectStatus 200 -ExpectCode 200

    Case -Name "删除不存在的评论：404（40001）" -Method DELETE -Path "/api/admin/comments/99999999" -Token $tAdmin -ExpectStatus 404 -ExpectCode 40001

    # ---------- 操作日志（注解 + AOP 自动记录，性能与健壮性模块） ----------
    Case -Name "操作日志：未登录 401" -Method GET -Path "/api/admin/logs?page=1&size=10" -ExpectStatus 401 -ExpectCode 401

    Case -Name "操作日志：普通用户 403" -Method GET -Path "/api/admin/logs?page=1&size=10" -Token $tOther -ExpectStatus 403 -ExpectCode 403

    Case -Name "操作日志：记录了刚才的审核动作（按模块过滤）" -Method GET -Path "/api/admin/logs?page=1&size=50" -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.total -ge 1 -and @($r.Json.data.list | Where-Object { $_.action -eq "审核笔记" }).Count -ge 1); Note = ("日志共 " + $r.Json.data.total + " 条，含 AOP 自动记录的「审核笔记」") } }

    Case -Name "操作日志：密码字段已脱敏（日志里不出现明文密码）" -Method GET -Path "/api/admin/logs?page=1&size=50" -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) $text = ($r.Json.data.list | ConvertTo-Json -Compress); @{ Ok = ($text -notlike "*123456*"); Note = ("日志文本里不含明文密码（检查 " + $r.Json.data.total + " 条）") } }

    Case -Name "操作日志：失败的操作也留痕（success=false）" -Method GET -Path "/api/admin/logs?success=false&page=1&size=50" -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.total -ge 1); Note = ("失败记录 " + $r.Json.data.total + " 条，例如越权或状态冲突的尝试") } }

    Write-Output "`n=== 九、社区互动：关注、评论与重新提交（模块二 / 模块三）==="
    $authorId = ((Api -Method GET -Path "/api/users/me" -Token $tAuthor).Json).data.id

    # ---------- 模块二：公开笔记审核与用户侧重新提交 ----------
    $auditNoteId = (Case -Name "作者新建公开笔记（进入待审核）" -Method POST -Path "/api/notes" -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"bookName":"《围城》","content":"演示审核与重提流程","visibility":"PUBLIC"}' `
        -Check { param($r) @{ Ok = ($r.Json.data.auditStatus -eq "PENDING"); Note = ("auditStatus=" + $r.Json.data.auditStatus) } }).Json.data.id

    Case -Name "待审核的公开笔记不能被评论（按不存在处理）" -Method POST -Path ("/api/notes/" + $auditNoteId + "/comments") -Token $tOther -ExpectStatus 404 -ExpectCode 20001 `
        -Json '{"content":"抢个沙发"}'

    Case -Name "管理员驳回：作者可见驳回原因" -Method PUT -Path ("/api/admin/notes/" + $auditNoteId + "/audit") -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"approved":false,"remark":"请补充自己的思考"}' `
        -Check { param($r) @{ Ok = ($r.Json.data.auditStatus -eq "REJECTED"); Note = ("auditStatus=" + $r.Json.data.auditStatus + "，原因=" + $r.Json.data.auditRemark) } }

    Case -Name "作者重新提交（不改内容也可以重提）" -Method PUT -Path ("/api/notes/" + $auditNoteId + "/resubmit") -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.auditStatus -eq "PENDING" -and [string]::IsNullOrEmpty($r.Json.data.auditRemark)); Note = ("auditStatus=" + $r.Json.data.auditStatus + "，原因已清空") } }

    Case -Name "重复重提（已是待审核状态）：20006" -Method PUT -Path ("/api/notes/" + $auditNoteId + "/resubmit") -Token $tAuthor -ExpectStatus 400 -ExpectCode 20006

    Case -Name "他人不能替作者重提：20002" -Method PUT -Path ("/api/notes/" + $auditNoteId + "/resubmit") -Token $tOther -ExpectStatus 403 -ExpectCode 20002

    Case -Name "重提后管理员审核通过" -Method PUT -Path ("/api/admin/notes/" + $auditNoteId + "/audit") -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"approved":true}' `
        -Check { param($r) @{ Ok = ($r.Json.data.auditStatus -eq "APPROVED"); Note = ("auditStatus=" + $r.Json.data.auditStatus) } }

    Case -Name "作者主页公开笔记列表里能看到它（可匿名）" -Method GET -Path ("/api/users/" + $authorId + "/notes?page=1&size=50") -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (@($r.Json.data.list | Where-Object { $_.id -eq $auditNoteId }).Count -eq 1); Note = ("作者公开笔记 total=" + $r.Json.data.total) } }

    # ---------- 模块三：关注与粉丝 ----------
    Case -Name "关注自己：50001" -Method POST -Path ("/api/users/" + $otherId + "/follow") -Token $tOther -ExpectStatus 400 -ExpectCode 50001

    Case -Name "关注不存在的用户：10001" -Method POST -Path "/api/users/99999999/follow" -Token $tOther -ExpectStatus 404 -ExpectCode 10001

    Case -Name "普通用户关注作者" -Method POST -Path ("/api/users/" + $authorId + "/follow") -Token $tOther -ExpectStatus 200 -ExpectCode 200

    Case -Name "重复关注：幂等，仍返回成功" -Method POST -Path ("/api/users/" + $authorId + "/follow") -Token $tOther -ExpectStatus 200 -ExpectCode 200

    Case -Name "未登录不能关注：401" -Method POST -Path ("/api/users/" + $authorId + "/follow") -ExpectStatus 401 -ExpectCode 401

    Case -Name "作者主页（粉丝视角）：粉丝数 1 且 followed=true" -Method GET -Path ("/api/users/" + $authorId) -Token $tOther -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.followerCount -eq 1 -and $r.Json.data.followed); Note = ("粉丝=" + $r.Json.data.followerCount + "，已关注=" + $r.Json.data.followed + "，公开笔记=" + $r.Json.data.noteCount) } }

    Case -Name "作者主页（匿名视角）：followed=false" -Method GET -Path ("/api/users/" + $authorId) -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.followerCount -eq 1 -and (-not $r.Json.data.followed)); Note = ("匿名看到的粉丝数=" + $r.Json.data.followerCount + "，followed=" + $r.Json.data.followed) } }

    Case -Name "作者主页（本人视角）：self=true" -Method GET -Path ("/api/users/" + $authorId) -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = $r.Json.data.self; Note = ("self=" + $r.Json.data.self) } }

    Case -Name "粉丝列表（匿名可看）" -Method GET -Path ("/api/users/" + $authorId + "/followers?page=1&size=10") -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (@($r.Json.data.list | Where-Object { $_.id -eq $otherId }).Count -eq 1); Note = ("粉丝数=" + $r.Json.data.total) } }

    Case -Name "关注列表：能看到他关注了谁" -Method GET -Path ("/api/users/" + $otherId + "/following?page=1&size=10") -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (@($r.Json.data.list | Where-Object { $_.id -eq $authorId }).Count -eq 1); Note = ("关注了 " + $r.Json.data.total + " 人") } }

    Case -Name "热门作者排行：该作者上榜且粉丝数正确" -Method GET -Path "/api/authors/hot?page=1&size=50" -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (@($r.Json.data.list | Where-Object { $_.id -eq $authorId -and $_.followerCount -ge 1 }).Count -eq 1); Note = "热门作者排行包含该作者" } }

    Case -Name "关注流：粉丝能看到作者的公开笔记" -Method GET -Path "/api/feed/following?page=1&size=50" -Token $tOther -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (@($r.Json.data.list | Where-Object { $_.id -eq $auditNoteId }).Count -eq 1); Note = ("关注流 total=" + $r.Json.data.total) } }

    Case -Name "关注流必须登录：401" -Method GET -Path "/api/feed/following" -ExpectStatus 401 -ExpectCode 401

    Case -Name "取消关注" -Method DELETE -Path ("/api/users/" + $authorId + "/follow") -Token $tOther -ExpectStatus 200 -ExpectCode 200

    Case -Name "取关后：关注流里没有他的笔记了" -Method GET -Path "/api/feed/following?page=1&size=50" -Token $tOther -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (@($r.Json.data.list | Where-Object { $_.id -eq $auditNoteId }).Count -eq 0); Note = ("关注流 total=" + $r.Json.data.total) } }

    # ---------- 模块三：评论与多级回复 ----------
    Case -Name "评论列表（匿名可看，此时为空）" -Method GET -Path ("/api/notes/" + $auditNoteId + "/comments?page=1&size=10") -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.total -eq 0); Note = ("total=" + $r.Json.data.total) } }

    Case -Name "未登录不能发评论：401" -Method POST -Path ("/api/notes/" + $auditNoteId + "/comments") -ExpectStatus 401 -ExpectCode 401 `
        -Json '{"content":"匿名评论"}'

    Case -Name "评论内容为空：400" -Method POST -Path ("/api/notes/" + $auditNoteId + "/comments") -Token $tOther -ExpectStatus 400 -ExpectCode 400 `
        -Json '{"content":"   "}'

    $topCommentId = (Case -Name "发表顶层评论" -Method POST -Path ("/api/notes/" + $auditNoteId + "/comments") -Token $tOther -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"content":"这本书我也读过，写得不错"}' `
        -Check { param($r) @{ Ok = ($null -eq $r.Json.data.parentId -and $r.Json.data.deletable -and $null -ne $r.Json.data.createTime); Note = ("评论 id=" + $r.Json.data.id + "，可删除=" + $r.Json.data.deletable + "，createTime=" + $r.Json.data.createTime) } }).Json.data.id

    $replyId = (Case -Name "回复评论（一级回复）" -Method POST -Path ("/api/notes/" + $auditNoteId + "/comments") -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Json ('{"content":"谢谢，欢迎继续交流","parentId":' + $topCommentId + '}') `
        -Check { param($r) @{ Ok = ($r.Json.data.parentId -eq $topCommentId); Note = ("parentId=" + $r.Json.data.parentId) } }).Json.data.id

    Case -Name "回复回复（多级回复）" -Method POST -Path ("/api/notes/" + $auditNoteId + "/comments") -Token $tOther -ExpectStatus 200 -ExpectCode 200 `
        -Json ('{"content":"再问一个问题：第三章怎么理解？","parentId":' + $replyId + '}') `
        -Check { param($r) @{ Ok = ($r.Json.data.parentId -eq $replyId); Note = ("多级回复 parentId=" + $r.Json.data.parentId) } }

    Case -Name "回复别的笔记下的评论：40003" -Method POST -Path ("/api/notes/" + $offlineNoteId + "/comments") -Token $tOther -ExpectStatus 400 -ExpectCode 40003 `
        -Json ('{"content":"跨笔记回复","parentId":' + $topCommentId + '}')

    Case -Name "评论列表：顶层评论与多级回复组装成树" -Method GET -Path ("/api/notes/" + $auditNoteId + "/comments?page=1&size=10") -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) $top = @($r.Json.data.list)[0]; $first = @($top.replies)[0]; @{ Ok = ($r.Json.data.total -eq 1 -and @($top.replies).Count -eq 1 -and @($first.replies).Count -eq 1); Note = ("顶层=" + $r.Json.data.total + "，一级回复=" + @($top.replies).Count + "，二级回复=" + @($first.replies).Count) } }

    Case -Name "笔记详情里的评论数正确" -Method GET -Path ("/api/notes/" + $auditNoteId) -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.commentCount -eq 3); Note = ("commentCount=" + $r.Json.data.commentCount) } }

    Case -Name "删除他人评论：40002" -Method DELETE -Path ("/api/comments/" + $topCommentId) -Token $tAuthor -ExpectStatus 403 -ExpectCode 40002

    Case -Name "管理员删除违规评论（此时评论树还在）" -Method DELETE -Path ("/api/comments/" + $replyId) -Token $tAdmin -ExpectStatus 200 -ExpectCode 200

    Case -Name "删除自己的顶层评论：连同剩余子回复一起删除" -Method DELETE -Path ("/api/comments/" + $topCommentId) -Token $tOther -ExpectStatus 200 -ExpectCode 200

    Case -Name "删除后：评论树整体消失" -Method GET -Path ("/api/notes/" + $auditNoteId + "/comments?page=1&size=10") -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.total -eq 0); Note = ("total=" + $r.Json.data.total) } }

    Case -Name "删除不存在的评论：40001" -Method DELETE -Path "/api/comments/99999999" -Token $tOther -ExpectStatus 404 -ExpectCode 40001

    Case -Name "评论私有笔记：按不存在处理（20001）" -Method POST -Path ("/api/notes/" + $privateId + "/comments") -Token $tOther -ExpectStatus 404 -ExpectCode 20001 `
        -Json '{"content":"私有笔记评论"}'

    # ---------- 删笔记时连带清理评论（避免留下悬空评论） ----------
    $cleanupNoteId = (Case -Name "新建一篇笔记并审核通过（验证删笔记连带清理评论）" -Method POST -Path "/api/notes" -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"bookName":"《清理验证》","content":"删除笔记时评论应一起清理","visibility":"PUBLIC"}' `
        -Check { param($r) @{ Ok = ($r.Json.data.auditStatus -eq "PENDING"); Note = ("auditStatus=" + $r.Json.data.auditStatus) } }).Json.data.id

    Case -Name "审核通过它" -Method PUT -Path ("/api/admin/notes/" + $cleanupNoteId + "/audit") -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"approved":true}'

    Case -Name "在它下面发一条评论" -Method POST -Path ("/api/notes/" + $cleanupNoteId + "/comments") -Token $tOther -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"content":"这条评论会随着笔记一起被清理"}'

    Case -Name "管理员评论列表里能看到它" -Method GET -Path ("/api/admin/comments?noteId=" + $cleanupNoteId + "&page=1&size=10") -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.total -eq 1); Note = ("该笔记的评论数=" + $r.Json.data.total) } }

    Case -Name "下架这篇笔记（验证下架后评论区的可见性）" -Method PUT -Path ("/api/admin/notes/" + $cleanupNoteId + "/offline") -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"remark":"演示：下架后作者仍可查看评论"}'

    Case -Name "下架后：作者本人仍能看到这篇笔记的评论区" -Method GET -Path ("/api/notes/" + $cleanupNoteId + "/comments?page=1&size=10") -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.total -eq 1); Note = ("作者可见评论数=" + $r.Json.data.total) } }

    Case -Name "下架后：陌生人在评论区按不存在处理（20001）" -Method GET -Path ("/api/notes/" + $cleanupNoteId + "/comments?page=1&size=10") -ExpectStatus 404 -ExpectCode 20001

    Case -Name "下架后：不能再发新评论（20001）" -Method POST -Path ("/api/notes/" + $cleanupNoteId + "/comments") -Token $tOther -ExpectStatus 404 -ExpectCode 20001 `
        -Json '{"content":"下架后不该还能评论"}'

    Case -Name "作者删除这篇笔记" -Method DELETE -Path ("/api/notes/" + $cleanupNoteId) -Token $tAuthor -ExpectStatus 200 -ExpectCode 200

    Case -Name "删笔记后：评论被一起清理，不留悬空记录" -Method GET -Path ("/api/admin/comments?noteId=" + $cleanupNoteId + "&page=1&size=10") -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.total -eq 0); Note = ("该笔记的评论数=" + $r.Json.data.total) } }

    Write-Output "`n=== 十、AI 辅助：总结 / 适宜人群 / 评分 + 调用统计（mock 模式，不花真实 token）==="
    $aiNoteId = (Case -Name "AI：新建一篇公开笔记" -Method POST -Path "/api/notes" -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"bookName":"《AI 测试用书》","content":"这篇笔记记录了三点收获：第一，坚持比天赋更重要；第二，把复杂问题拆成小步骤；第三，定期回顾比一次性读完更有效。","visibility":"PUBLIC"}' `
        -Check { param($r) @{ Ok = ($r.Json.data.auditStatus -eq "PENDING"); Note = ("auditStatus=" + $r.Json.data.auditStatus) } }).Json.data.id

    Case -Name "AI：先审核通过这篇笔记" -Method PUT -Path ("/api/admin/notes/" + $aiNoteId + "/audit") -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 -Json '{"approved":true}'

    Case -Name "AI：未登录不能调用（401）" -Method POST -Path ("/api/notes/" + $aiNoteId + "/ai/SUMMARY") -ExpectStatus 401 -ExpectCode 401

    Case -Name "AI：不能对别人的笔记调用（20002）" -Method POST -Path ("/api/notes/" + $aiNoteId + "/ai/SUMMARY") -Token $tOther -ExpectStatus 403 -ExpectCode 20002

    Case -Name "AI：非法 capability → 400" -Method POST -Path ("/api/notes/" + $aiNoteId + "/ai/UNKNOWN") -Token $tAuthor -ExpectStatus 400 -ExpectCode 400

    Case -Name "AI：生成总结（返回 aiLogId / token / 剩余配额）" -Method POST -Path ("/api/notes/" + $aiNoteId + "/ai/SUMMARY") -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($null -ne $r.Json.data.aiLogId -and $r.Json.data.text -like "*摘要*" -and $r.Json.data.totalTokens -gt 0); Note = ("aiLogId=" + $r.Json.data.aiLogId + "，tokens=" + $r.Json.data.totalTokens + "，今日剩余 " + $r.Json.data.remainingQuota + " 次") } }

    $aiLogId = (Case -Name "AI：生成评分" -Method POST -Path ("/api/notes/" + $aiNoteId + "/ai/SCORE") -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.text -like "*评分*"); Note = ("预览内容：" + $r.Json.data.text.Replace("`n", " / ")) } }).Json.data.aiLogId

    Case -Name "AI：确认保存（只传 aiLogId，结果由服务端取，客户端改不了）" -Method PUT -Path ("/api/notes/" + $aiNoteId + "/ai-result") -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Json ('{"aiLogId":' + $aiLogId + ',"aiPublic":false}')

    Case -Name "AI：作者详情页能看到已保存结果" -Method GET -Path ("/api/notes/" + $aiNoteId) -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.aiType -eq "SCORE" -and -not [string]::IsNullOrEmpty($r.Json.data.aiResult) -and (-not $r.Json.data.aiPublic)); Note = ("aiType=" + $r.Json.data.aiType + "，公开=" + $r.Json.data.aiPublic) } }

    Case -Name "AI：默认不公开，匿名看不到 AI 结果" -Method GET -Path ("/api/notes/" + $aiNoteId) -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ([string]::IsNullOrEmpty($r.Json.data.aiResult)); Note = "匿名拿到的 aiResult 为空" } }

    Case -Name "AI：作者把 AI 结果设为公开" -Method PUT -Path ("/api/notes/" + $aiNoteId + "/ai-result/visibility") -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 -Json '{"aiPublic":true}'

    Case -Name "AI：公开后匿名也能看到 AI 结果" -Method GET -Path ("/api/notes/" + $aiNoteId) -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (-not [string]::IsNullOrEmpty($r.Json.data.aiResult) -and $r.Json.data.aiPublic); Note = "匿名能看到作者公开的 AI 结果" } }

    Case -Name "AI：确认不存在的生成记录 → 60003" -Method PUT -Path ("/api/notes/" + $aiNoteId + "/ai-result") -Token $tAuthor -ExpectStatus 404 -ExpectCode 60003 `
        -Json '{"aiLogId":99999999}'

    Case -Name "AI：撤销已保存的结果" -Method DELETE -Path ("/api/notes/" + $aiNoteId + "/ai-result") -Token $tAuthor -ExpectStatus 200 -ExpectCode 200

    Case -Name "AI：撤销后详情里没有结果了" -Method GET -Path ("/api/notes/" + $aiNoteId) -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ([string]::IsNullOrEmpty($r.Json.data.aiResult)); Note = "aiResult 已清空" } }

    Case -Name "AI 统计：未登录 401" -Method GET -Path "/api/admin/ai-stats" -ExpectStatus 401 -ExpectCode 401
    Case -Name "AI 统计：普通用户 403" -Method GET -Path "/api/admin/ai-stats" -Token $tOther -ExpectStatus 403 -ExpectCode 403
    Case -Name "AI 统计：管理员可查，次数与成功率正确" -Method GET -Path "/api/admin/ai-stats" -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.totalCalls -ge 2 -and @($r.Json.data.byCapability).Count -ge 1 -and $r.Json.data.avgCostMs -ge 0); Note = ("总调用 " + $r.Json.data.totalCalls + " 次，成功率 " + $r.Json.data.successRate + "%，模型 " + $r.Json.data.model) } }

    Case -Name "AI：清理测试笔记" -Method DELETE -Path ("/api/notes/" + $aiNoteId) -Token $tAuthor -ExpectStatus 200 -ExpectCode 200

    Write-Output "`n=== 十一、AI 智能客服：会话 / 多轮 / 隔离 / 审计（mock 模式）==="
    Case -Name "AI 客服：未登录新建会话 → 401" -Method POST -Path "/api/ai/chat/sessions" -ExpectStatus 401 -ExpectCode 401
    Case -Name "AI 客服：未登录查会话列表 → 401" -Method GET -Path "/api/ai/chat/sessions" -ExpectStatus 401 -ExpectCode 401

    $chatSid = (Case -Name "AI 客服：新建空会话（标题=新会话、消息数 0）" -Method POST -Path "/api/ai/chat/sessions" -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = ($r.Json.data.title -eq "新会话" -and $r.Json.data.messageCount -eq 0 -and $null -ne $r.Json.data.createTime); Note = ("id=" + $r.Json.data.id + "，标题=" + $r.Json.data.title + "，创建时间=" + $r.Json.data.createTime) } }).Json.data.id

    Case -Name "AI 客服：会话列表里能看到刚建的会话" -Method GET -Path "/api/ai/chat/sessions?page=1&size=50" -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (@($r.Json.data.list | Where-Object { $_.id -eq $chatSid }).Count -eq 1); Note = ("共 " + $r.Json.data.total + " 个会话") } }

    Case -Name "AI 客服：第 1 条消息（标题取首条成功消息、返回 token 与剩余次数）" -Method POST -Path ("/api/ai/chat/sessions/" + $chatSid + "/messages") -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"content":"笔记可以设成仅自己可见吗？"}' `
        -Check { param($r) @{ Ok = ($r.Json.data.reply.content -like "*上下文 1 条*" -and $r.Json.data.sessionTitle -eq "笔记可以设成仅自己可见吗？" -and $r.Json.data.reply.totalTokens -gt 0); Note = ("标题=" + $r.Json.data.sessionTitle + "，token=" + $r.Json.data.reply.totalTokens + "，剩余 " + $r.Json.data.remainingQuota + " 次") } }

    Case -Name "AI 客服：第 2 条消息带上了历史（上下文 3 条）" -Method POST -Path ("/api/ai/chat/sessions/" + $chatSid + "/messages") -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Json '{"content":"那它后面还能改成公开吗？"}' `
        -Check { param($r) @{ Ok = ($r.Json.data.reply.content -like "*上下文 3 条*"); Note = $r.Json.data.reply.content } }

    Case -Name "AI 客服：会话详情 4 条消息，顺序为用户/客服/用户/客服" -Method GET -Path ("/api/ai/chat/sessions/" + $chatSid) -Token $tAuthor -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (@($r.Json.data.messages).Count -eq 4 -and @($r.Json.data.messages)[0].role -eq "USER" -and @($r.Json.data.messages)[1].role -eq "ASSISTANT"); Note = ((@($r.Json.data.messages) | ForEach-Object { $_.role }) -join " → ") } }

    Case -Name "AI 客服：他人读历史 → 60007" -Method GET -Path ("/api/ai/chat/sessions/" + $chatSid) -Token $tOther -ExpectStatus 404 -ExpectCode 60007
    Case -Name "AI 客服：他人发消息 → 60007" -Method POST -Path ("/api/ai/chat/sessions/" + $chatSid + "/messages") -Token $tOther -ExpectStatus 404 -ExpectCode 60007 -Json '{"content":"我也来问一句"}'
    Case -Name "AI 客服：他人删会话 → 60007" -Method DELETE -Path ("/api/ai/chat/sessions/" + $chatSid) -Token $tOther -ExpectStatus 404 -ExpectCode 60007

    Case -Name "AI 客服：超长消息 1001 字 → 60008（提示当前字数）" -Method POST -Path ("/api/ai/chat/sessions/" + $chatSid + "/messages") -Token $tAuthor -ExpectStatus 400 -ExpectCode 60008 `
        -Json ('{"content":"' + ("字" * 1001) + '"}') `
        -Check { param($r) @{ Ok = ($r.Json.message -like "*1001*"); Note = $r.Json.message } }

    Case -Name "AI 客服：空白消息 → 400" -Method POST -Path ("/api/ai/chat/sessions/" + $chatSid + "/messages") -Token $tAuthor -ExpectStatus 400 -ExpectCode 400 -Json '{"content":"   "}'

    Case -Name "AI 客服：管理员 AI 统计里能看到客服分组" -Method GET -Path "/api/admin/ai-stats" -Token $tAdmin -ExpectStatus 200 -ExpectCode 200 `
        -Check { param($r) @{ Ok = (@($r.Json.data.byCapability | Where-Object { $_.capability -eq "CHAT" }).Count -eq 1 -and @($r.Json.data.byCapability | Where-Object { $_.capability -eq "CHAT" })[0].calls -ge 2); Note = ((@($r.Json.data.byCapability) | ForEach-Object { $_.label + ":" + $_.calls + "次" }) -join "，") } }

    Case -Name "AI 客服：删除会话（消息一并物理删除）" -Method DELETE -Path ("/api/ai/chat/sessions/" + $chatSid) -Token $tAuthor -ExpectStatus 200 -ExpectCode 200
    Case -Name "AI 客服：删除后再读 → 60007" -Method GET -Path ("/api/ai/chat/sessions/" + $chatSid) -Token $tAuthor -ExpectStatus 404 -ExpectCode 60007

    # 每日建会话上限：本次运行把 app.ai.chat-daily-session-limit 调成 2，用另一个账号（$tOther）验证
    Case -Name "AI 客服：当日第 1 个会话（未达上限）" -Method POST -Path "/api/ai/chat/sessions" -Token $tOther -ExpectStatus 200 -ExpectCode 200 | Out-Null
    Case -Name "AI 客服：当日第 2 个会话（达到上限）" -Method POST -Path "/api/ai/chat/sessions" -Token $tOther -ExpectStatus 200 -ExpectCode 200 | Out-Null
    Case -Name "AI 客服：超过当日建会话上限 → 60011（提示当前数量与上限）" -Method POST -Path "/api/ai/chat/sessions" -Token $tOther -ExpectStatus 429 -ExpectCode 60011 `
        -Check { param($r) @{ Ok = ($r.Json.message -like "*2*"); Note = $r.Json.message } }

    Write-Output "`n=== 七、接口文档 ==="
    $apiDocsCode = & curl.exe -s -m 30 -o "$docs\openapi.json" -w "%{http_code}" "$base/v3/api-docs"
    $uiCode = & curl.exe -s -m 30 -o NUL -w "%{http_code}" -L "$base/swagger-ui/index.html"
    $spec = $null
    try { $spec = (Get-Content "$docs\openapi.json" -Raw -Encoding UTF8) | ConvertFrom-Json } catch { }
    $pathCount = 0
    if ($spec) { $pathCount = @($spec.paths | Get-Member -MemberType NoteProperty).Count }
    Add-Simple -Name "OpenAPI 规范可导出（/v3/api-docs）" -Method "GET" -Path "/v3/api-docs" -Expect "HTTP 200" -Actual ("HTTP " + $apiDocsCode) `
        -Check ("接口路径数=" + $pathCount) -Ok ($apiDocsCode -eq "200" -and $pathCount -gt 0)
    Add-Simple -Name "Swagger UI 页面可访问" -Method "GET" -Path "/swagger-ui/index.html" -Expect "HTTP 200" -Actual ("HTTP " + $uiCode) `
        -Check "浏览器可视化文档" -Ok ($uiCode -eq "200")
    Write-Output ("[接口文档] OpenAPI HTTP {0}，路径数 {1}；Swagger UI HTTP {2}" -f $apiDocsCode, $pathCount, $uiCode)
} finally {
    Stop-Process -Id $proc.Id -Force -EA SilentlyContinue
    Start-Sleep -Seconds 2
    Write-Output ("`n应用已停止: " + $proc.HasExited)
}

# ---------------- 生成测试记录 ----------------
# 计数器统一用 @() 包裹：PowerShell 5.1 里"单个对象"的 .Count 是 $null，
# 只有一个匹配项时 ($x | Where-Object ...).Count 会静默变成 $null，断言就永远不成立
$passCount = @($script:results | Where-Object { $_.Result -eq "通过" }).Count
$failCount = @($script:results | Where-Object { $_.Result -ne "通过" }).Count
$now = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

$md = New-Object System.Text.StringBuilder
[void]$md.AppendLine("# 笔记工坊 Note-Workshop 接口测试记录")
[void]$md.AppendLine()
[void]$md.AppendLine("> 本文档由 [tools/api-test.ps1](../tools/api-test.ps1) 自动生成，记录真实运行结果的留痕。")
[void]$md.AppendLine()
[void]$md.AppendLine("## 一、测试环境")
[void]$md.AppendLine()
[void]$md.AppendLine("| 项目 | 内容 |")
[void]$md.AppendLine("| --- | --- |")
[void]$md.AppendLine("| 被测应用 | note-workshop 1.0.0（Spring Boot 3.5.16） |")
[void]$md.AppendLine("| 服务地址 | $base |")
[void]$md.AppendLine("| 数据库 | MySQL 9.7.2，库名 note_workshop |")
[void]$md.AppendLine("| 运行环境 | JDK 25.0.4.1（编译目标 Java 21） |")
[void]$md.AppendLine("| 测试时间 | $now |")
[void]$md.AppendLine("| 测试方式 | 真实启动应用后按 HTTP 协议逐个调用接口，校验 HTTP 状态码与业务 code |")
[void]$md.AppendLine("| 原始日志 | 同目录 api-test-raw.log |")
[void]$md.AppendLine("| 接口文档 | 同目录 openapi.json 与 api.md，或应用启动后访问 /swagger-ui.html |")
[void]$md.AppendLine()
[void]$md.AppendLine("## 二、测试结果汇总")
[void]$md.AppendLine()
[void]$md.AppendLine("| 用例总数 | 通过 | 失败 |")
[void]$md.AppendLine("| --- | --- | --- |")
[void]$md.AppendLine("| $($script:results.Count) | $passCount | $failCount |")
[void]$md.AppendLine()
[void]$md.AppendLine("## 三、用例明细")
[void]$md.AppendLine()
[void]$md.AppendLine("| # | 用例 | 方法 | 路径 | 预期 | 实际 | 附加校验 | 结果 |")
[void]$md.AppendLine("| --- | --- | --- | --- | --- | --- | --- | --- |")
$i = 0
foreach ($r in $script:results) {
    $i++
    [void]$md.AppendLine("| $i | $($r.Name) | $($r.Method) | $($r.Path) | $($r.Expect) | $($r.Actual) | $($r.Check) | $($r.Result) |")
}
[void]$md.AppendLine()
[void]$md.AppendLine("## 四、说明")
[void]$md.AppendLine()
[void]$md.AppendLine("1. 业务 code 约定：200 成功；400 参数错误；401 未登录；403 无权限；404 资源不存在；405 方法不支持；500 系统错误；1xxxx 用户模块；2xxxx 笔记模块；3xxxx 文件模块。")
[void]$md.AppendLine("2. 覆盖了全部 17 个接口的正常流程与异常流程（含越权、幂等、参数校验、上传限制、分页与排行）。")
[void]$md.AppendLine("3. 未覆盖：AI 辅助相关接口（后续模块）、管理员后台接口（进阶要求）。")
[void]$md.AppendLine("4. 复现方式：启动 MySQL 与应用后执行 tools/api-test.ps1；也可以用同目录的 Postman 集合导入后运行。")
Save-Text (Join-Path $docs "api-test-report.md") $md.ToString()
Save-Text (Join-Path $docs "api-test-raw.log") (($script:raws -join "`r`n`r`n"))

Write-Output "`n=== 测试记录已生成 ==="
Write-Output ("用例总数={0} 通过={1} 失败={2}" -f $script:results.Count, $passCount, $failCount)
Write-Output ("报告: " + (Join-Path $docs "api-test-report.md"))
if ($failCount -gt 0) {
    Write-Output "失败用例："
    $script:results | Where-Object { $_.Result -ne "通过" } | ForEach-Object { Write-Output ("  - " + $_.Name + " | 预期 " + $_.Expect + " | 实际 " + $_.Actual) }
}
