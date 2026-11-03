# CULINARY 模块依赖规则检查（A03A §6.1 / §6.2）
#
# 只依赖文本解析，不需要 Gradle 启动；用于本地与 CI 的快速门禁。
# 规则来源：D-004 已接受的 15 模块与依赖方向。

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot

$expectedModules = @(
    'app',
    'core:common', 'core:domain', 'core:database', 'core:data', 'core:designsystem', 'core:platform',
    'feature:ingredients', 'feature:sauces', 'feature:recipes', 'feature:products',
    'feature:inventory', 'feature:planning', 'feature:procurement', 'feature:settings'
)

$featureModules = $expectedModules | Where-Object { $_ -like 'feature:*' }

$allowed = @{
    'app'          = @($featureModules) + @('core:data', 'core:platform', 'core:designsystem')
    'core:common'  = @()
    'core:domain'  = @('core:common')
    'core:database' = @('core:common')
    'core:data'    = @('core:domain', 'core:database', 'core:common')
    'core:designsystem' = @('core:common')
    'core:platform' = @('core:domain', 'core:common')
}
foreach ($feature in $featureModules) {
    $allowed[$feature] = @('core:domain', 'core:designsystem', 'core:common')
}

# 1. settings.gradle.kts 必须恰好声明 15 个模块，且不得出现 core:model。
$settingsText = Get-Content (Join-Path $root 'settings.gradle.kts') -Raw
$declared = [regex]::Matches($settingsText, 'include\(":([^"]+)"\)') |
    ForEach-Object { $_.Groups[1].Value } |
    Where-Object { $_ -notin @('build-logic') }

$failures = [System.Collections.Generic.List[string]]::new()

if ($declared.Count -ne 15) {
    $failures.Add("模块数量应为 15，实际 $($declared.Count)")
}
foreach ($module in $expectedModules) {
    if ($module -notin $declared) { $failures.Add("缺少模块 :$module") }
}
foreach ($module in $declared) {
    if ($module -notin $expectedModules) { $failures.Add("出现未批准模块 :$module") }
}
if ($declared -contains 'core:model') {
    $failures.Add('禁止创建 :core:model（D-004 / R01 0.2.2）')
}

# 2. 逐模块检查 project(...) 依赖是否落在允许集合内。
$edges = @()
foreach ($module in $expectedModules) {
    if ($module -eq 'app') {
        $buildFile = Join-Path $root 'app/build.gradle.kts'
    } else {
        $parts = $module.Split(':')
        $buildFile = Join-Path (Join-Path $root ($parts -join '/')) 'build.gradle.kts'
    }

    if (-not (Test-Path $buildFile)) {
        $failures.Add("缺少构建文件：$buildFile")
        continue
    }

    $text = Get-Content $buildFile -Raw
    $targets = [regex]::Matches($text, 'project\(":([^"]+)"\)') | ForEach-Object { $_.Groups[1].Value }
    foreach ($target in $targets) {
        $edges += "$module -> $target"
        if ($allowed[$module] -notcontains $target) {
            $failures.Add("禁止的依赖：$module -> $target（A03A §6.2）")
        }
    }
}

# 3. 明确禁止：任何模块依赖 :app。
foreach ($edge in $edges) {
    if ($edge -like '* -> app') { $failures.Add("禁止的依赖：$edge") }
}

Write-Host "CULINARY module dependency check"
Write-Host "  modules: $($declared.Count) (expected 15)"
Write-Host "  declared edges: $($edges.Count)"
$edges | Sort-Object -Unique | ForEach-Object { Write-Host "    $_" }

if ($failures.Count -gt 0) {
    Write-Host ''
    Write-Host 'FAILED:'
    $failures | ForEach-Object { Write-Host "  - $_" }
    exit 1
}

Write-Host ''
Write-Host 'PASS: module count, module set and dependency directions match A03A.'
exit 0
