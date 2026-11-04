# MISEBASE 休眠表引用扫描（MB-S1-001 / MB-D-005）
#
# 11 张规划/采购/供应商表在 schema v1 中保留，状态 DORMANT：
#   - 不删除、不迁移、不做结构性改造、不创建 Schema v2；
#   - 禁止新代码引用；只允许 core/database 的结构定义与约束测试引用。
#
# 扫描范围：app、core、feature 下的 *.kt（排除 build 目录）。
# 引用允许位置：config/dormant-tables.json 的 allowedReferenceRoots。
# 退出码：0 = PASS；1 = 发现允许范围外的引用；2 = 配置错误。

#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$Root = '',
    [string]$ConfigPath = '',
    [switch]$SelfTest,
    [string]$ReportPath = ''
)

$ErrorActionPreference = 'Stop'

trap {
    Write-Host ("UNCAUGHT: " + $_.Exception.Message)
    Write-Host ("STACK: " + $_.ScriptStackTrace)
    exit 1
}

$scriptDirectory = $PSScriptRoot
if ([string]::IsNullOrEmpty($scriptDirectory)) {
    if (-not [string]::IsNullOrEmpty($PSCommandPath)) {
        $scriptDirectory = Split-Path -Parent $PSCommandPath
    } else {
        $scriptDirectory = (Get-Location).Path
    }
}
$repositoryRoot = Split-Path -Parent $scriptDirectory
if ([string]::IsNullOrEmpty($Root)) { $Root = $repositoryRoot }
if ([string]::IsNullOrEmpty($ConfigPath)) { $ConfigPath = Join-Path $repositoryRoot 'config\dormant-tables.json' }

function Read-DormantConfig {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { throw "missing dormant table config: $Path" }
    return (Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json)
}

function Test-AllowedPath {
    param([string]$RelativePath, $Config)
    $normalized = $RelativePath.Replace('\', '/')
    foreach ($allowed in @($Config.allowedReferenceRoots)) {
        if ($normalized.StartsWith([string]$allowed)) { return $true }
    }
    return $false
}

function Get-ReferenceKind {
    param([string]$RelativePath)
    $normalized = $RelativePath.Replace('\', '/')
    if ($normalized -match '/src/main/') { return 'main' }
    if ($normalized -match '/src/test/') { return 'test' }
    if ($normalized -match '/src/androidTest/') { return 'androidTest' }
    if ($normalized -like 'core/database/schemas/*') { return 'schema' }
    return 'other'
}

function Invoke-DormantScan {
    param([string]$ProjectRoot, $Config)
    $results = @()
    $files = @()
    foreach ($sourceRoot in @('app', 'core', 'feature')) {
        $absolute = Join-Path $ProjectRoot $sourceRoot
        if (-not (Test-Path -LiteralPath $absolute)) { continue }
        $files += @(Get-ChildItem -LiteralPath $absolute -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch '[\\/]build[\\/]' })
    }

    foreach ($table in @($Config.tables)) {
        $pattern = '(?<![A-Za-z0-9_])' + [regex]::Escape([string]$table.name) + '(?![A-Za-z0-9_])'
        $mainCount = 0
        $testCount = 0
        $outside = New-Object 'System.Collections.Generic.List[object]'
        $total = 0
        foreach ($file in $files) {
            $relative = $file.FullName.Substring($ProjectRoot.Length).TrimStart('\', '/')
            $lines = @(Get-Content -LiteralPath $file.FullName -Encoding UTF8)
            for ($i = 0; $i -lt $lines.Count; $i++) {
                if (-not [regex]::IsMatch($lines[$i], $pattern)) { continue }
                $total += 1
                $kind = Get-ReferenceKind -RelativePath $relative
                if ($kind -eq 'main') { $mainCount += 1 }
                elseif ($kind -eq 'test' -or $kind -eq 'androidTest') { $testCount += 1 }
                if (-not (Test-AllowedPath -RelativePath $relative -Config $Config)) {
                    $outside.Add([pscustomobject]@{ Table = [string]$table.name; File = $relative; Line = ($i + 1); Text = $lines[$i].Trim() })
                }
            }
        }
        $results += [pscustomobject]@{
            Table = [string]$table.name
            Area = [string]$table.area
            Status = [string]$Config.status
            Main = $mainCount
            Test = $testCount
            Outside = $outside.Count
            Total = $total
            Violations = $outside.ToArray()
        }
    }
    return [pscustomobject]@{ Files = $files.Count; Tables = $results }
}

function Invoke-SelfTest {
    param($Config)
    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('misebase-dormant-selftest-' + [guid]::NewGuid().ToString('N'))
    $failures = New-Object 'System.Collections.Generic.List[string]'
    try {
        $injected = Join-Path $tempRoot 'app/src/main/java/com/culinary/InjectedDormantReference.kt'
        New-Item -ItemType Directory -Path (Split-Path -Parent $injected) -Force | Out-Null
        Set-Content -LiteralPath $injected -Value 'package com.culinary' -Encoding UTF8
        Add-Content -LiteralPath $injected -Value 'internal val injected = "supplier"' -Encoding UTF8
        $result = Invoke-DormantScan -ProjectRoot $tempRoot -Config $Config
        $supplier = @($result.Tables | Where-Object { $_.Table -eq 'supplier' })[0]
        if ($null -eq $supplier -or $supplier.Outside -lt 1) {
            $failures.Add('failed to detect an injected supplier reference outside core/database')
        } else {
            Write-Host ("  injected reference: {0} outside reference(s) detected in app" -f $supplier.Outside)
        }
    } finally {
        if (Test-Path -LiteralPath $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
    }
    if ($failures.Count -gt 0) {
        Write-Host ''
        Write-Host 'SELF-TEST FAILED:'
        foreach ($failure in $failures) { Write-Host "  - $failure" }
        exit 1
    }
    Write-Host ''
    Write-Host 'SELF-TEST PASS: injected dormant-table reference was detected.'
    exit 0
}

try {
    $config = Read-DormantConfig -Path $ConfigPath
} catch {
    Write-Host "CONFIG ERROR: $($_.Exception.Message)"
    exit 2
}

if ($SelfTest) {
    Write-Host 'MISEBASE dormant-table scanner self-test'
    Invoke-SelfTest -Config $config
    exit 0
}

$result = Invoke-DormantScan -ProjectRoot $Root -Config $config
$lines = New-Object 'System.Collections.Generic.List[string]'
$lines.Add('MISEBASE DORMANT TABLE REFERENCE SCAN')
$lines.Add("Decision: $($config.decision); status: $($config.status); schema: v$($config.schemaVersionFrozen) (frozen)")
$lines.Add("Kotlin files scanned: $($result.Files)")
$lines.Add("Tables: $(@($result.Tables).Count) (expected 11)")
$lines.Add('')
$lines.Add(('{0,-28} {1,-8} {2,6} {3,6} {4,8} {5,9}' -f 'table', 'area', 'main', 'test', 'outside', 'status'))
foreach ($row in $result.Tables) {
    $lines.Add(('{0,-28} {1,-8} {2,6} {3,6} {4,8} {5,9}' -f $row.Table, $row.Area, $row.Main, $row.Test, $row.Outside, $row.Status))
}
$violations = @($result.Tables | ForEach-Object { $_.Violations } | Where-Object { $null -ne $_ })
$lines.Add('')
if ($violations.Count -eq 0) {
    $lines.Add('Result: PASS (no references outside core/database structural code/tests)')
} else {
    $lines.Add("Result: FAIL ($($violations.Count) reference(s) outside allowed roots)")
}
foreach ($line in $lines) { Write-Host $line }

if ($violations.Count -gt 0) {
    Write-Host ''
    Write-Host 'OUTSIDE REFERENCES:'
    foreach ($violation in $violations) {
        Write-Host ("  {0}  {1}:{2}  {3}" -f $violation.Table, $violation.File, $violation.Line, $violation.Text)
    }
}

if ($ReportPath -ne '') {
    $report = New-Object 'System.Collections.Generic.List[string]'
    $report.Add('# 休眠表引用扫描结果（生成物）')
    $report.Add('')
    $report.Add(('生成时间：{0}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')))
    $report.Add('')
    foreach ($line in $lines) { $report.Add($line) }
    if ($violations.Count -gt 0) {
        $report.Add('')
        $report.Add('## 允许范围外引用')
        foreach ($violation in $violations) {
            $report.Add(('- `{0}` `{1}:{2}` `{3}`' -f $violation.Table, $violation.File, $violation.Line, $violation.Text))
        }
    }
    $report | Set-Content -LiteralPath $ReportPath -Encoding UTF8
}

if ($violations.Count -gt 0) { exit 1 }
Write-Host ''
Write-Host 'PASS: all dormant tables are unreferenced outside core/database structural code and tests.'
exit 0
