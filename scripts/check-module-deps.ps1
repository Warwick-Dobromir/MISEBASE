# MISEBASE 模块依赖检查（MB-S5-001 / MB-D-004）
#
# 规则来源：config/module-dependencies.json（14 模块白名单 + 允许边 + 源码 import 规则）。
# MB-D-004：Stage 5 起模块集为 14（Stage 1–4 的 13 + :feature:scan）。
# 检查内容：
#   1. settings.gradle.kts 的模块集与配置一致；
#   2. 每个模块 build.gradle.kts 的 project(...) 声明边落在允许集合；
#   3. 扫描 src/main/**/*.kt 的 import，映射到模块并检查分层禁忌；
#   4. 对声明边 + import 边的并集做拓扑排序，发现循环即失败；
#   5. -SelfTest 在临时目录注入违规，验证检查器确实能捕获。
#
# 退出码：0 = PASS；1 = 违规；2 = 配置/环境错误。

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

# Windows PowerShell 5.1 在部分 -File 调用下不会填 $PSScriptRoot，这里做一次兜底。
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
if ([string]::IsNullOrEmpty($ConfigPath)) { $ConfigPath = Join-Path $repositoryRoot 'config\module-dependencies.json' }

function New-Violation {
    param(
        [string]$Code,
        [string]$Message,
        [string]$File = '',
        [int]$Line = 0
    )
    [pscustomobject]@{ Code = $Code; Message = $Message; File = $File; Line = $Line }
}

function Read-Config {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "missing dependency config: $Path"
    }
    $raw = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    return $raw | ConvertFrom-Json
}

function Get-DeclaredModules {
    param([string]$ProjectRoot)
    $settingsPath = Join-Path $ProjectRoot 'settings.gradle.kts'
    if (-not (Test-Path -LiteralPath $settingsPath)) {
        throw "missing settings.gradle.kts under $ProjectRoot"
    }
    $text = Get-Content -LiteralPath $settingsPath -Raw -Encoding UTF8
    return @([regex]::Matches($text, 'include\(":([^"]+)"\)') |
        ForEach-Object { $_.Groups[1].Value } |
        Where-Object { $_ -ne 'build-logic' })
}

function Get-AllowedFor {
    param($Config, [string]$ModuleId)
    $property = $Config.allowedDependencies.PSObject.Properties[$ModuleId]
    if ($null -eq $property) { return @() }
    return @($property.Value)
}

function Get-ModuleById {
    param($Config, [string]$ModuleId)
    return @($Config.modules | Where-Object { $_.id -eq $ModuleId }) | Select-Object -First 1
}

function Resolve-ModuleForFqn {
    param($Config, [string]$Fqn)
    # 最长包前缀优先，避免 app 的 com.culinary 抢先匹配 core/feature 包。
    $ordered = @($Config.modules | Sort-Object { $_.package.Length } -Descending)
    foreach ($module in $ordered) {
        $prefix = [string]$module.package
        if ($Fqn -eq $prefix -or $Fqn.StartsWith($prefix + '.')) {
            return [string]$module.id
        }
    }
    return $null
}

function Get-BuildEdges {
    param([string]$ProjectRoot, $Config)
    $edges = New-Object 'System.Collections.Generic.List[object]'
    $violations = New-Object 'System.Collections.Generic.List[object]'
    foreach ($module in $Config.modules) {
        $buildFile = Join-Path (Join-Path $ProjectRoot $module.dir) 'build.gradle.kts'
        if (-not (Test-Path -LiteralPath $buildFile)) {
            $violations.Add((New-Violation -Code 'V-MISSING-BUILD' -Message "缺少构建文件：$($module.dir)/build.gradle.kts" -File $buildFile))
            continue
        }
        $text = Get-Content -LiteralPath $buildFile -Raw -Encoding UTF8
        $targets = @([regex]::Matches($text, 'project\(":([^"]+)"\)') | ForEach-Object { $_.Groups[1].Value })
        foreach ($target in $targets) {
            $edges.Add([pscustomobject]@{ Source = [string]$module.id; Target = $target; Kind = 'declared'; File = $buildFile; Line = 0 })
            $allowed = Get-AllowedFor -Config $Config -ModuleId $module.id
            if ($allowed -notcontains $target) {
                if ($target -eq 'app') {
                    $violations.Add((New-Violation -Code 'V-APP-DEPENDENCY' -Message "禁止依赖 :app：$($module.id) -> $target" -File $buildFile))
                } else {
                    $violations.Add((New-Violation -Code 'V-DECLARED-EDGE' -Message "声明依赖不在允许集合：$($module.id) -> $target" -File $buildFile))
                }
            }
        }
    }
    return [pscustomobject]@{ Edges = $edges.ToArray(); Violations = $violations.ToArray() }
}

function Test-ImportPrefix {
    param([string]$Fqn, [string]$Prefix)
    if ($Prefix.EndsWith('.')) { return $Fqn.StartsWith($Prefix) }
    return ($Fqn -eq $Prefix) -or $Fqn.StartsWith($Prefix + '.')
}

function Get-ImportRecords {
    param([string]$ProjectRoot, $Config)
    $edges = New-Object 'System.Collections.Generic.List[object]'
    $violations = New-Object 'System.Collections.Generic.List[object]'
    $rules = $Config.rules

    foreach ($module in $Config.modules) {
        $sourceId = [string]$module.id
        $sourceType = [string]$module.type
        $mainRoot = Join-Path (Join-Path $ProjectRoot $module.dir) 'src/main'
        if (-not (Test-Path -LiteralPath $mainRoot)) { continue }
        $files = @(Get-ChildItem -LiteralPath $mainRoot -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue)
        foreach ($file in $files) {
            $lines = @(Get-Content -LiteralPath $file.FullName -Encoding UTF8)
            $imports = @()
            for ($i = 0; $i -lt $lines.Count; $i++) {
                $match = [regex]::Match($lines[$i], '^\s*import\s+([A-Za-z0-9_.]+)')
                if ($match.Success) { $imports += $match.Groups[1].Value }
            }
            $relative = $file.FullName.Substring($ProjectRoot.Length).TrimStart('\', '/')

            $usesCompose = $false
            foreach ($candidate in $imports) {
                foreach ($composePrefix in @($rules.featureComposeImportPrefixes)) {
                    if (Test-ImportPrefix -Fqn $candidate -Prefix ([string]$composePrefix)) { $usesCompose = $true; break }
                }
                if ($usesCompose) { break }
            }

            for ($i = 0; $i -lt $lines.Count; $i++) {
                $match = [regex]::Match($lines[$i], '^\s*import\s+([A-Za-z0-9_.]+)')
                if (-not $match.Success) { continue }
                $fqn = $match.Groups[1].Value

                # 模块无关的分层禁忌：无论 import 是否映射到本项目模块都要检查。
                if ($sourceId -eq 'core:domain') {
                    foreach ($prefix in @($rules.coreDomainImportPrefixes)) {
                        if (Test-ImportPrefix -Fqn $fqn -Prefix ([string]$prefix)) {
                            $violations.Add((New-Violation -Code 'V-DOMAIN-ANDROID' -Message "core:domain 不得依赖 Android/Room/Compose/Hilt 或实现模块：$fqn" -File $relative -Line ($i + 1)))
                            break
                        }
                    }
                }
                if ($sourceId -eq 'core:data') {
                    foreach ($prefix in @($rules.coreDataImportPrefixes)) {
                        if (Test-ImportPrefix -Fqn $fqn -Prefix ([string]$prefix)) {
                            $violations.Add((New-Violation -Code 'V-DATA-SAF' -Message "core:data 不得依赖 Android SAF/Uri：$fqn" -File $relative -Line ($i + 1)))
                            break
                        }
                    }
                }
                if ($sourceType -eq 'feature' -and $usesCompose) {
                    foreach ($forbidden in @($rules.featureUiForbiddenImportPrefixes)) {
                        if (Test-ImportPrefix -Fqn $fqn -Prefix ([string]$forbidden)) {
                            $violations.Add((New-Violation -Code 'V-UI-DAO' -Message "Compose 页面不得直接依赖 DAO/SQLite/Data：$fqn" -File $relative -Line ($i + 1)))
                            break
                        }
                    }
                }

                $targetId = Resolve-ModuleForFqn -Config $Config -Fqn $fqn
                if ($null -eq $targetId -or $targetId -eq $sourceId) { continue }
                $edges.Add([pscustomobject]@{ Source = $sourceId; Target = $targetId; Kind = 'import'; File = $relative; Line = ($i + 1) })

                $allowed = Get-AllowedFor -Config $Config -ModuleId $sourceId
                if ($allowed -notcontains $targetId) {
                    $violations.Add((New-Violation -Code 'V-IMPORT-UNDECLARED' -Message "源码 import 了未允许的模块：$sourceId -> $targetId（$fqn）" -File $relative -Line ($i + 1)))
                }
                $target = Get-ModuleById -Config $Config -ModuleId $targetId
                if ($null -eq $target) { continue }
                $targetType = [string]$target.type

                if ($sourceType -eq 'feature' -and $targetType -eq 'feature' -and $rules.featureToFeature) {
                    $violations.Add((New-Violation -Code 'V-FEATURE-FEATURE' -Message "禁止 feature 之间直接依赖：$sourceId -> $targetId" -File $relative -Line ($i + 1)))
                }
                if ($sourceType -eq 'feature' -and (@($rules.featureToCoreImpl) -contains $targetId)) {
                    $violations.Add((New-Violation -Code 'V-FEATURE-IMPL' -Message "禁止 feature 依赖实现模块：$sourceId -> $targetId" -File $relative -Line ($i + 1)))
                }
                if ($sourceId -eq 'core:database' -and (@($rules.coreDatabaseTo) -contains $targetId)) {
                    $violations.Add((New-Violation -Code 'V-DB-REVERSE' -Message "禁止 core:database 反向依赖：$sourceId -> $targetId" -File $relative -Line ($i + 1)))
                }
                if ($targetId -eq $rules.anyImportModule -and $sourceId -ne $rules.anyImportModule) {
                    $violations.Add((New-Violation -Code 'V-APP-DEPENDENCY' -Message "禁止依赖 app：$sourceId -> $targetId" -File $relative -Line ($i + 1)))
                }
            }
        }
    }
    return [pscustomobject]@{ Edges = $edges.ToArray(); Violations = $violations.ToArray() }
}

function Get-TopologicalOrder {
    param($ModuleIds, $Edges)
    $ids = @($ModuleIds)
    $dependencies = @{}
    foreach ($id in $ids) {
        $dependencies[$id] = New-Object 'System.Collections.Generic.List[string]'
    }
    foreach ($edge in $Edges) {
        $source = [string]$edge.Source
        $target = [string]$edge.Target
        if ($dependencies.ContainsKey($source) -and $dependencies.ContainsKey($target)) {
            if (-not $dependencies[$source].Contains($target)) {
                $dependencies[$source].Add($target)
            }
        }
    }
    $remaining = @{}
    foreach ($id in $ids) { $remaining[$id] = $dependencies[$id].Count }
    $order = New-Object 'System.Collections.Generic.List[string]'
    while ($order.Count -lt $ids.Count) {
        $ready = @($ids | Where-Object { $remaining[$_] -eq 0 -and -not $order.Contains($_) } | Sort-Object)
        if ($ready.Count -eq 0) {
            $cycle = @($ids | Where-Object { -not $order.Contains($_) } | Sort-Object)
            return [pscustomobject]@{ Order = $order; Cycle = $cycle }
        }
        foreach ($id in $ready) {
            $order.Add([string]$id)
            foreach ($dependent in $ids) {
                if ($dependencies[$dependent].Contains([string]$id)) {
                    $remaining[$dependent] = [int]$remaining[$dependent] - 1
                }
            }
        }
    }
    return [pscustomobject]@{ Order = $order; Cycle = @() }
}

function Invoke-DependencyCheck {
    param([string]$ProjectRoot, $Config)
    $violations = New-Object 'System.Collections.Generic.List[object]'
    $declared = @(Get-DeclaredModules -ProjectRoot $ProjectRoot)
    $expectedIds = @($Config.modules | ForEach-Object { [string]$_.id })

    if ($declared.Count -ne [int]$Config.expectedModuleCount) {
        $violations.Add((New-Violation -Code 'V-MODULE-COUNT' -Message "模块数量应为 $($Config.expectedModuleCount)，实际 $($declared.Count)"))
    }
    foreach ($id in $expectedIds) {
        if ($declared -notcontains $id) {
            $violations.Add((New-Violation -Code 'V-MODULE-MISSING' -Message "缺少模块 :$id"))
        }
    }
    foreach ($id in $declared) {
        if ($expectedIds -notcontains $id) {
            $violations.Add((New-Violation -Code 'V-MODULE-UNKNOWN' -Message "出现未批准模块 :$id"))
        }
    }
    foreach ($forbidden in @($Config.forbiddenModuleIds)) {
        if ($declared -contains $forbidden) {
            $violations.Add((New-Violation -Code 'V-MODULE-FORBIDDEN' -Message "出现禁止模块 :$forbidden"))
        }
    }

    $build = Get-BuildEdges -ProjectRoot $ProjectRoot -Config $Config
    foreach ($v in $build.Violations) { $violations.Add($v) }
    $imports = Get-ImportRecords -ProjectRoot $ProjectRoot -Config $Config
    foreach ($v in $imports.Violations) { $violations.Add($v) }

    $allEdges = @($build.Edges) + @($imports.Edges)
    $topo = Get-TopologicalOrder -ModuleIds $expectedIds -Edges $allEdges
    if ($topo.Cycle.Count -gt 0) {
        $violations.Add((New-Violation -Code 'V-CYCLE' -Message ("检测到循环依赖，未排序节点：" + ($topo.Cycle -join ', '))))
    }

    return [pscustomobject]@{
        Modules = $declared
        DeclaredEdges = $build.Edges
        ImportEdges = $imports.Edges
        TopoOrder = $topo.Order.ToArray()
        Cycle = $topo.Cycle
        Violations = $violations.ToArray()
    }
}

function Write-InjectedFile {
    param([string]$Path, [string]$Package, [string[]]$Imports)
    $directory = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $directory)) { New-Item -ItemType Directory -Path $directory -Force | Out-Null }
    $lines = @("package $Package", '')
    foreach ($import in $Imports) { $lines += "import $import" }
    $lines += ''
    $lines += 'internal object InjectedDependencyProbe'
    Set-Content -LiteralPath $Path -Value ($lines -join [Environment]::NewLine) -Encoding UTF8
}

function New-TempModuleTree {
    param($Config, [string]$TempRoot)
    New-Item -ItemType Directory -Path $TempRoot -Force | Out-Null
    $settings = @('rootProject.name = "selftest"', '')
    foreach ($module in $Config.modules) { $settings += "include(`":$($module.id)`")" }
    Set-Content -LiteralPath (Join-Path $TempRoot 'settings.gradle.kts') -Value ($settings -join [Environment]::NewLine) -Encoding UTF8

    foreach ($module in $Config.modules) {
        $moduleDir = Join-Path $TempRoot $module.dir
        New-Item -ItemType Directory -Path $moduleDir -Force | Out-Null
        $build = @('dependencies {')
        foreach ($dependency in (Get-AllowedFor -Config $Config -ModuleId $module.id)) {
            $build += "    implementation(project(`":$dependency`"))"
        }
        $build += '}'
        Set-Content -LiteralPath (Join-Path $moduleDir 'build.gradle.kts') -Value ($build -join [Environment]::NewLine) -Encoding UTF8
    }
}

function Get-ViolationCodes {
    param($Result)
    return @($Result.Violations | ForEach-Object { $_.Code } | Sort-Object -Unique)
}

function Invoke-SelfTest {
    param($Config)
    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('misebase-deps-selftest-' + [guid]::NewGuid().ToString('N'))
    $failures = New-Object 'System.Collections.Generic.List[string]'
    try {
        New-TempModuleTree -Config $Config -TempRoot $tempRoot

        $clean = Invoke-DependencyCheck -ProjectRoot $tempRoot -Config $Config
        Write-Host ("  clean tree: modules={0}, declared={1}, imports={2}, violations={3}" -f $clean.Modules.Count, $clean.DeclaredEdges.Count, $clean.ImportEdges.Count, $clean.Violations.Count)
        if ($clean.Violations.Count -ne 0) {
            $failures.Add('clean tree should have zero violations')
            foreach ($v in $clean.Violations) { Write-Host ("    unexpected: {0} {1}" -f $v.Code, $v.Message) }
        }
        if ($clean.TopoOrder.Count -ne $Config.modules.Count) {
            $failures.Add('clean tree topological order should cover all modules')
        }

        # 注入 1：feature -> feature import
        Write-InjectedFile -Path (Join-Path $tempRoot 'feature/ingredients/src/main/java/com/culinary/feature/ingredients/InjectedFeatureImport.kt') -Package 'com.culinary.feature.ingredients' -Imports @('com.culinary.feature.sauces.SaucesRoute')
        # 注入 2：feature -> core:database import
        Write-InjectedFile -Path (Join-Path $tempRoot 'feature/inventory/src/main/java/com/culinary/feature/inventory/InjectedDatabaseImport.kt') -Package 'com.culinary.feature.inventory' -Imports @('com.culinary.core.database.dao.InventoryBatchDao')
        # 注入 3：core:database -> core:domain import
        Write-InjectedFile -Path (Join-Path $tempRoot 'core/database/src/main/java/com/culinary/core/database/InjectedDomainImport.kt') -Package 'com.culinary.core.database' -Imports @('com.culinary.core.domain.model.Ingredient')
        # 注入 4：core:domain -> Android/Room import
        Write-InjectedFile -Path (Join-Path $tempRoot 'core/domain/src/main/java/com/culinary/core/domain/InjectedAndroidImport.kt') -Package 'com.culinary.core.domain' -Imports @('androidx.room.RoomDatabase')
        # 注入 5：Compose 页面 -> Room DAO
        Write-InjectedFile -Path (Join-Path $tempRoot 'feature/recipes/src/main/java/com/culinary/feature/recipes/InjectedUiDaoImport.kt') -Package 'com.culinary.feature.recipes' -Imports @('androidx.compose.runtime.Composable', 'androidx.room.Query')
        # 注入 8：feature:scan -> feature:products（扫码不得依赖商品/库存实现）
        Write-InjectedFile -Path (Join-Path $tempRoot 'feature/scan/src/main/java/com/culinary/feature/scan/InjectedScanFeatureImport.kt') -Package 'com.culinary.feature.scan' -Imports @('com.culinary.feature.products.FeatureProductsModule')

        $injected = Invoke-DependencyCheck -ProjectRoot $tempRoot -Config $Config
        $codes = Get-ViolationCodes -Result $injected
        Write-Host ("  injected imports: violations={0}, codes={1}" -f $injected.Violations.Count, ($codes -join ','))
        foreach ($expected in @('V-FEATURE-FEATURE', 'V-FEATURE-IMPL', 'V-DB-REVERSE', 'V-DOMAIN-ANDROID', 'V-UI-DAO', 'V-IMPORT-UNDECLARED')) {
            if ($codes -notcontains $expected) {
                $failures.Add("failed to detect injected violation: $expected")
            }
        }

        # 注入 6：模块数量（删除一个 include）
        $settingsPath = Join-Path $tempRoot 'settings.gradle.kts'
        $originalSettings = Get-Content -LiteralPath $settingsPath -Raw -Encoding UTF8
        $brokenSettings = $originalSettings -replace '(?m)^include\(":feature:settings"\)\s*$', ''
        Set-Content -LiteralPath $settingsPath -Value $brokenSettings -Encoding UTF8 -NoNewline
        $countResult = Invoke-DependencyCheck -ProjectRoot $tempRoot -Config $Config
        if ((Get-ViolationCodes -Result $countResult) -notcontains 'V-MODULE-COUNT') {
            $failures.Add('failed to detect injected module count mismatch')
        } else {
            Write-Host '  injected module count: detected V-MODULE-COUNT'
        }
        Set-Content -LiteralPath $settingsPath -Value $originalSettings -Encoding UTF8 -NoNewline

        # 注入 7：循环依赖（core:common 反向依赖 feature:ingredients）
        $commonBuild = Join-Path $tempRoot 'core/common/build.gradle.kts'
        $originalCommon = Get-Content -LiteralPath $commonBuild -Raw -Encoding UTF8
        Set-Content -LiteralPath $commonBuild -Value ($originalCommon + [Environment]::NewLine + 'dependencies { implementation(project(":feature:ingredients")) }') -Encoding UTF8
        $cycleResult = Invoke-DependencyCheck -ProjectRoot $tempRoot -Config $Config
        if ((Get-ViolationCodes -Result $cycleResult) -notcontains 'V-CYCLE') {
            $failures.Add('failed to detect injected cycle')
        } else {
            Write-Host '  injected cycle: detected V-CYCLE'
        }
        Set-Content -LiteralPath $commonBuild -Value $originalCommon -Encoding UTF8
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
    Write-Host 'SELF-TEST PASS: all injected violations were detected.'
    exit 0
}

try {
    $config = Read-Config -Path $ConfigPath
} catch {
    Write-Host "CONFIG ERROR: $($_.Exception.Message)"
    exit 2
}

if ($SelfTest) {
    Write-Host 'MISEBASE dependency checker self-test (violation injection)'
    Invoke-SelfTest -Config $config
    exit 0
}

$result = Invoke-DependencyCheck -ProjectRoot $Root -Config $config
$lines = New-Object 'System.Collections.Generic.List[string]'
$lines.Add('MISEBASE 14-MODULE DEPENDENCY CHECK')
$lines.Add("Decision: $($config.decision); stage: $($config.stage)")
$lines.Add("Modules: $($result.Modules.Count) (expected $($config.expectedModuleCount))")
$lines.Add("Declared dependency edges: $($result.DeclaredEdges.Count)")
$lines.Add("Source import edges: $($result.ImportEdges.Count)")
$lines.Add('Topological order (dependencies first):')
for ($i = 0; $i -lt $result.TopoOrder.Count; $i++) {
    $lines.Add(('  {0,2}. {1}' -f ($i + 1), $result.TopoOrder[$i]))
}
$lines.Add("Result: $(if ($result.Violations.Count -eq 0) { 'PASS (DAG, no violations)' } else { 'FAIL' })")

foreach ($line in $lines) { Write-Host $line }
if ($result.Violations.Count -gt 0) {
    Write-Host ''
    Write-Host 'VIOLATIONS:'
    foreach ($violation in $result.Violations) {
        $location = $violation.File
        if ($violation.Line -gt 0) { $location = "$location`:$($violation.Line)" }
        Write-Host ("  [{0}] {1} {2}" -f $violation.Code, $violation.Message, $location)
    }
}

if ($ReportPath -ne '') {
    $report = New-Object 'System.Collections.Generic.List[string]'
    $report.Add('# MISEBASE 14 模块依赖检查结果（生成物）')
    $report.Add('')
    $report.Add(('生成时间：{0}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')))
    $report.Add('')
    foreach ($line in $lines) { $report.Add($line) }
    if ($result.Violations.Count -gt 0) {
        $report.Add('')
        $report.Add('## Violations')
        foreach ($violation in $result.Violations) {
            $report.Add(('- `{0}` {1} `{2}:{3}`' -f $violation.Code, $violation.Message, $violation.File, $violation.Line))
        }
    }
    $report | Set-Content -LiteralPath $ReportPath -Encoding UTF8
}

if ($result.Violations.Count -gt 0) { exit 1 }
Write-Host ''
Write-Host 'PASS: module set, declared edges, source imports and topology match MB-D-004.'
exit 0
