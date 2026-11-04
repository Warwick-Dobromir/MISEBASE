# MISEBASE Stage 1 Git 变更范围检查
#
# 检查 Stage 1 起点（f8de1bc）..HEAD 与工作树的变更是否落在 MB-S1-001 允许范围内：
#   - 文档、README、settings/app/feature 的 13 模块调整、config/scripts；
#   - 禁止 Schema/Entity/DAO/迁移/数据库文件、备份/恢复实现、feature:scan、功能页面、G2 历史报告。
#
# 退出码：0 = PASS；1 = 越界；2 = Git 错误。

#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$Root = '',
    [string]$Base = 'f8de1bc'
)

$ErrorActionPreference = 'Stop'

$scriptDirectory = $PSScriptRoot
if ([string]::IsNullOrEmpty($scriptDirectory)) {
    if (-not [string]::IsNullOrEmpty($PSCommandPath)) { $scriptDirectory = Split-Path -Parent $PSCommandPath }
    else { $scriptDirectory = (Get-Location).Path }
}
if ([string]::IsNullOrEmpty($Root)) { $Root = Split-Path -Parent $scriptDirectory }

function Invoke-Git {
    param([string[]]$Arguments)
    $output = & git -C $Root @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "git $($Arguments -join ' ') failed: $output" }
    return @($output)
}

$failures = New-Object 'System.Collections.Generic.List[string]'
$changed = New-Object 'System.Collections.Generic.List[object]'

try {
    $committed = Invoke-Git @('diff', '--name-status', "$Base..HEAD")
    foreach ($line in $committed) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $parts = $line -split "`t"
        if ($parts.Count -ge 2) {
            $changed.Add([pscustomobject]@{ Status = $parts[0]; Path = ($parts[$parts.Count - 1]).Replace('\', '/'); Source = 'committed' })
        }
    }
    $working = Invoke-Git @('status', '--porcelain=v1')
    foreach ($line in $working) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $status = $line.Substring(0, 2).Trim()
        $path = $line.Substring(3).Trim().Replace('\', '/')
        if ($path -match ' -> ') { $path = ($path -split ' -> ')[-1] }
        $changed.Add([pscustomobject]@{ Status = $status; Path = $path; Source = 'working' })
    }
} catch {
    Write-Host "GIT ERROR: $($_.Exception.Message)"
    exit 2
}

$allowedPrefixes = @(
    'README.md',
    'docs/',
    'settings.gradle.kts',
    'app/build.gradle.kts',
    'app/src/',
    'feature/',
    'config/',
    'scripts/'
)

$forbiddenPatterns = @(
    '^core/database/schemas/.*/2\.json$',
    '^core/database/src/main/.*/entity/',
    '^core/database/src/main/.*/dao/',
    '^core/database/src/main/.*/schema/',
    '^core/database/src/main/.*/CulinaryDatabase\.kt$',
    '^core/database/src/main/.*/CulinaryMigrations\.kt$',
    '^core/database/src/main/.*/CulinaryDatabaseCallback\.kt$',
    '^core/database/src/main/.*/DatabaseFactory\.kt$',
    '^core/data/src/main/.*/backup/',
    '^core/platform/src/main/.*/work/',
    '^core/data/src/main/',
    '^core/platform/src/main/',
    '^\.github/',
    '^build-logic/',
    '^gradle/',
    '^feature/scan/',
    '^docs/04-readiness/R1[1-9]-G2-STAGE[0-9]+-REPORT\.md$',
    '^docs/05-handover/S0-0[1-7]-.*\.md$',
    '^docs/05-handover/MB-S0-0[1-6]-.*\.md$'
)

foreach ($item in $changed) {
    $path = [string]$item.Path
    $isAllowed = $false
    foreach ($prefix in $allowedPrefixes) {
        if ($path -eq $prefix -or $path.StartsWith($prefix)) { $isAllowed = $true; break }
    }
    if (-not $isAllowed) {
        $failures.Add("路径不在允许范围：$path（$($item.Status)）")
        continue
    }
    foreach ($pattern in $forbiddenPatterns) {
        if ($path -match $pattern) {
            $failures.Add("禁止修改：$path（$($item.Status)）")
            break
        }
    }
    if ($item.Status -eq 'A' -and $path -match '^feature/.*/src/main/.*Screen\.kt$') {
        $failures.Add("Stage 1 不得新增功能页面：$path")
    }
}

# 13 模块与标签
$settings = Get-Content -LiteralPath (Join-Path $Root 'settings.gradle.kts') -Raw -Encoding UTF8
$modules = @([regex]::Matches($settings, 'include\(":([^"]+)"\)') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne 'build-logic' })
if ($modules.Count -ne 13) { $failures.Add("settings 模块数应为 13，实际 $($modules.Count)") }
foreach ($forbidden in @('feature:planning', 'feature:procurement', 'feature:scan', 'core:model')) {
    if ($modules -contains $forbidden) { $failures.Add("settings 仍包含禁止模块 :$forbidden") }
}

try {
    $framework = (@(Invoke-Git @('rev-parse', '--short=7', 'framework-0.1.0'))[0]).Trim()
    if ($framework -ne '2b523de') {
        $failures.Add("framework-0.1.0 指向 $framework，应为 2b523de")
    }
} catch {
    $failures.Add("无法解析 framework-0.1.0: $($_.Exception.Message)")
}

Write-Host "Stage 1 change scope: base=$Base, changed=$($changed.Count)"
foreach ($item in ($changed | Sort-Object Path)) {
    Write-Host ("  {0,-3} {1}  [{2}]" -f $item.Status, $item.Path, $item.Source)
}
Write-Host ''
if ($failures.Count -gt 0) {
    Write-Host 'SCOPE CHECK FAILED:'
    foreach ($failure in $failures) { Write-Host "  - $failure" }
    exit 1
}
Write-Host 'PASS: changes are within the Stage 1 allowed scope; framework-0.1.0 unchanged.'
exit 0
