# MISEBASE 文档一致性检查（Stage 1）
#
# 只检查事实一致性，不做完整 Markdown 校验：
#   - 13 模块与 settings.gradle.kts 一致；
#   - README 的数据库/测试/模块/决策事实；
#   - P01 / A03A / A-G2-001 的修订标记；
#   - 决策登记册的 10/18 状态；
#   - Stage 1 八份交付文档存在；
#   - README 与 Stage 1 文档的相对链接可解析。
#
# 退出码：0 = PASS；1 = 不一致。

#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$Root = ''
)

$ErrorActionPreference = 'Stop'

$scriptDirectory = $PSScriptRoot
if ([string]::IsNullOrEmpty($scriptDirectory)) {
    if (-not [string]::IsNullOrEmpty($PSCommandPath)) { $scriptDirectory = Split-Path -Parent $PSCommandPath }
    else { $scriptDirectory = (Get-Location).Path }
}
if ([string]::IsNullOrEmpty($Root)) { $Root = Split-Path -Parent $scriptDirectory }

$failures = New-Object 'System.Collections.Generic.List[string]'
$checks = New-Object 'System.Collections.Generic.List[string]'

function Read-Text {
    param([string]$RelativePath)
    $path = Join-Path $Root $RelativePath
    if (-not (Test-Path -LiteralPath $path)) {
        $failures.Add("缺少文件：$RelativePath")
        return ''
    }
    return (Get-Content -LiteralPath $path -Raw -Encoding UTF8)
}

function Assert-Contains {
    param([string]$RelativePath, [string]$Text, [string]$Needle, [string]$Label)
    if ($Text.Contains($Needle)) {
        $checks.Add("OK  $RelativePath 包含 $Label")
    } else {
        $failures.Add("$RelativePath 缺少 $Label（期望包含：$Needle）")
    }
}

function Assert-NotContains {
    param([string]$RelativePath, [string]$Text, [string]$Needle, [string]$Label)
    if (-not $Text.Contains($Needle)) {
        $checks.Add("OK  $RelativePath 不含 $Label")
    } else {
        $failures.Add("$RelativePath 仍包含 $Label（不应包含：$Needle）")
    }
}

# 1. 模块集（MB-D-004：Stage 5 起 14 模块）
$settingsPath = Join-Path $Root 'settings.gradle.kts'
if (-not (Test-Path -LiteralPath $settingsPath)) {
    $failures.Add('缺少 settings.gradle.kts')
} else {
    $settings = Get-Content -LiteralPath $settingsPath -Raw -Encoding UTF8
    $modules = @([regex]::Matches($settings, 'include\(":([^"]+)"\)') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne 'build-logic' })
    $expected = @('app', 'core:common', 'core:domain', 'core:database', 'core:data', 'core:designsystem', 'core:platform',
        'feature:ingredients', 'feature:sauces', 'feature:recipes', 'feature:products', 'feature:inventory', 'feature:scan', 'feature:settings')
    if ($modules.Count -eq 14) { $checks.Add('OK  settings.gradle.kts 模块数 = 14') }
    else { $failures.Add("settings.gradle.kts 模块数应为 14，实际 $($modules.Count)") }
    foreach ($id in $expected) {
        if ($modules -notcontains $id) { $failures.Add("settings.gradle.kts 缺少模块 :$id") }
    }
    foreach ($forbidden in @('feature:planning', 'feature:procurement', 'core:model')) {
        if ($modules -contains $forbidden) { $failures.Add("settings.gradle.kts 仍包含禁止模块 :$forbidden") }
    }
}

# 2. README 事实
$readme = Read-Text 'README.md'
if ($readme -ne '') {
    Assert-Contains 'README.md' $readme '13 模块' '13 模块事实'
    Assert-Contains 'README.md' $readme '197 例 JVM 测试' '197 例测试事实'
    Assert-Contains 'README.md' $readme 'schema **v1 / 31 表**' 'schema v1/31 表事实'
    Assert-Contains 'README.md' $readme 'DORMANT' '休眠表标注'
    Assert-Contains 'README.md' $readme 'MB-D-007 仍为 `PROPOSED`' 'MB-D-007 未签收边界'
    Assert-NotContains 'README.md' $readme '188 例' '旧测试口径 188'
    Assert-NotContains 'README.md' $readme '15 模块' '旧模块口径 15'
}

# 3. P01 / A03A / A-G2-001 修订标记
$p01 = Read-Text 'docs/01-product/P01-PRODUCT-BASELINE.md'
if ($p01 -ne '') {
    Assert-Contains 'P01' $p01 'Superseded' 'Superseded 说明'
    Assert-Contains 'P01' $p01 'MB-D-002' 'MB-D-002 依据'
    Assert-Contains 'P01' $p01 '采购清单' '采购清单归属说明'
}
$a03a = Read-Text 'docs/02-architecture/A03A-MODULE-DECOMPOSITION-REVIEW.md'
if ($a03a -ne '') {
    Assert-Contains 'A03A' $a03a 'MISEBASE 13 模块目标' '13 模块目标章节'
    Assert-Contains 'A03A' $a03a '15 模块' 'G2 15 模块历史说明'
    Assert-Contains 'A03A' $a03a 'MB-D-003' 'MB-D-003 依据'
}
$archive = Read-Text 'docs/00-governance/A-G2-001-G2-ARCHIVE.md'
if ($archive -ne '') {
    Assert-Contains 'A-G2-001' $archive '## 14. Errata' 'Errata 章节'
    Assert-Contains 'A-G2-001' $archive '197' '197 更正'
    Assert-Contains 'A-G2-001' $archive 'R11 / R12' 'R11/R12 说明'
}

# 4. 决策登记册状态
$decisions = Read-Text 'docs/05-handover/MB-S0-07-DECISION-REGISTER.md'
if ($decisions -ne '') {
    Assert-Contains 'MB-DECISIONS' $decisions '10 项已 `ACCEPTED`' '10 项已接受'
    Assert-Contains 'MB-DECISIONS' $decisions 'MB-D-007' 'MB-D-007 条目'
    Assert-Contains 'MB-DECISIONS' $decisions 'G2-S1' 'G2-S1 测试口径'
    Assert-NotContains 'MB-DECISIONS' $decisions '全部为 `PROPOSED`，0 项 `ACCEPTED`' '旧的 0 项接受表述'
}

# 5. Stage 1 交付文档存在
$stage1Docs = @(
    'docs/06-stage1/MB-S1-01-EXECUTION-REPORT.md',
    'docs/06-stage1/MB-S1-02-MODULE-ADJUSTMENT.md',
    'docs/06-stage1/MB-S1-03-DEPENDENCY-CHECK.md',
    'docs/06-stage1/MB-S1-04-DORMANT-TABLES.md',
    'docs/06-stage1/MB-S1-05-MIGRATION-READINESS.md',
    'docs/06-stage1/MB-S1-06-BACKUP-GAP-ANALYSIS.md',
    'docs/06-stage1/MB-S1-07-UI-ISOLATION.md',
    'docs/06-stage1/MB-S1-08-DOC-CORRECTIONS.md'
)
foreach ($doc in $stage1Docs) {
    if (Test-Path -LiteralPath (Join-Path $Root $doc)) { $checks.Add("OK  存在 $doc") }
    else { $failures.Add("缺少 Stage 1 文档：$doc") }
}

# 6. 相对链接可解析（README + Stage 1 文档）
$linkDocs = @('README.md') + $stage1Docs
foreach ($doc in $linkDocs) {
    $path = Join-Path $Root $doc
    if (-not (Test-Path -LiteralPath $path)) { continue }
    $text = Get-Content -LiteralPath $path -Raw -Encoding UTF8
    $baseDir = Split-Path -Parent $path
    foreach ($match in [regex]::Matches($text, '\]\(([^)#]+)(?:#[^)]*)?\)')) {
        $target = $match.Groups[1].Value.Trim()
        if ($target -match '^(https?:|mailto:|#)') { continue }
        if ($target.StartsWith('<') -and $target.EndsWith('>')) { $target = $target.Trim('<', '>') }
        $resolved = Join-Path $baseDir $target
        if (-not (Test-Path -LiteralPath $resolved)) {
            $failures.Add("$doc 的链接无法解析：$target")
        }
    }
    $checks.Add("OK  $doc 相对链接检查完成")
}

foreach ($line in $checks) { Write-Host $line }
Write-Host ''
if ($failures.Count -gt 0) {
    Write-Host 'DOCUMENT CONSISTENCY FAILED:'
    foreach ($failure in $failures) { Write-Host "  - $failure" }
    exit 1
}
Write-Host 'PASS: document facts, revision markers, decision states and links are consistent.'
exit 0
