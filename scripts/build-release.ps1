#requires -Version 5.1
<#
.SYNOPSIS
    MISEBASE 本地发布构建（MB-S6-001 §3.4 / MB-D-010：不依赖远端 CI）。

.DESCRIPTION
    可重复执行的发布流水线：
      1. 校验工具链（JDK 17、Android SDK 36、keytool）；
      2. 从 local.properties 或同名环境变量读取发布签名（缺失即失败，仓库内不落任何凭据）；
      3. 运行全量门禁：test、assembleRelease、ktlintCheck、detekt；
      4. 校验 APK 签名、包名、versionCode/versionName；
      5. 输出 SHA-256，并把 APK 与校验文件复制到 dist/。

.PARAMETER SkipTests
    跳过 test（仅用于本地快速冒烟，正式发布不要使用）。

.PARAMETER DistDir
    产物输出目录，默认 dist/（已被 .gitignore 忽略）。

.EXAMPLE
    .\scripts\build-release.ps1

.EXAMPLE
    .\scripts\build-release.ps1 -SkipTests
#>
[CmdletBinding()]
param(
    [switch]$SkipTests,
    [string]$DistDir = 'dist'
)

# 原生工具（java/keytool/apksigner/gradle）会把进度写到 stderr；
# 在 PowerShell 5.1 下 Stop 会把它当作终止错误，因此统一 Continue + 显式检查退出码。
$ErrorActionPreference = 'Continue'

$scriptDirectory = $PSScriptRoot
if ([string]::IsNullOrEmpty($scriptDirectory)) {
    $scriptDirectory = Split-Path -Parent $PSCommandPath
}
$root = Split-Path -Parent $scriptDirectory
Set-Location $root

function Write-Step { param([string]$Message) Write-Host "==> $Message" -ForegroundColor Cyan }
function Fail { param([string]$Message) Write-Host "[FAIL] $Message" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------------------
# 1. 工具链
# ---------------------------------------------------------------------------
Write-Step '1/6 校验工具链'

$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
if (-not (Get-Command $java -ErrorAction SilentlyContinue)) {
    Fail '找不到 java。请安装 JDK 17 或更高版本，并设置 JAVA_HOME。'
}
$javaVersion = (& $java -version 2>&1 | Out-String)
if ($javaVersion -notmatch 'version "(\d+)') {
    Fail "无法识别 Java 版本：$javaVersion"
}
$javaMajor = [int]$matches[1]
if ($javaMajor -lt 17) {
    Fail "需要 JDK 17 或更高版本（AGP 8.14），当前：$($javaVersion.Split([Environment]::NewLine)[0])"
}

$localPropertiesPath = Join-Path $root 'local.properties'
if (-not (Test-Path -LiteralPath $localPropertiesPath)) {
    Fail '缺少 local.properties。请参考 docs/14-stage6/RELEASE-BUILD-GUIDE.md §2 创建。'
}

$properties = @{}
Get-Content -LiteralPath $localPropertiesPath -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*)$') { $properties[$matches[1]] = $matches[2].Trim() }
}

$sdkDir = if ($properties.ContainsKey('sdk.dir')) { $properties['sdk.dir'] } else { $env:ANDROID_HOME }
if ([string]::IsNullOrWhiteSpace($sdkDir) -or -not (Test-Path -LiteralPath $sdkDir)) {
    Fail "找不到 Android SDK（sdk.dir = '$sdkDir'）。请在 local.properties 写入 sdk.dir 或设置 ANDROID_HOME。"
}

$buildToolsRoot = Join-Path $sdkDir 'build-tools'
if (-not (Test-Path -LiteralPath $buildToolsRoot)) { Fail "缺少 $buildToolsRoot" }
$buildTools = Get-ChildItem -LiteralPath $buildToolsRoot -Directory |
    Sort-Object { [version]($_.Name -replace '[^0-9.]', '') } -Descending |
    Select-Object -First 1
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
$aapt2 = Join-Path $buildTools.FullName 'aapt2.exe'
foreach ($tool in @($apksigner, $aapt2)) {
    if (-not (Test-Path -LiteralPath $tool)) { Fail "缺少构建工具：$tool" }
}
if (-not (Test-Path -LiteralPath (Join-Path $sdkDir 'platforms\android-36'))) {
    Fail "缺少 Android SDK Platform 36（$sdkDir\platforms\android-36）"
}
Write-Host "    JDK: $($javaVersion.Split([Environment]::NewLine)[0])"
Write-Host "    SDK: $sdkDir"
Write-Host "    Build-Tools: $($buildTools.Name)"

# ---------------------------------------------------------------------------
# 2. 发布签名（只在仓库外；仓库内不含 .keystore / 密码）
# ---------------------------------------------------------------------------
Write-Step '2/6 校验发布签名'

$requiredKeys = @(
    'MISEBASE_RELEASE_STORE_FILE',
    'MISEBASE_RELEASE_STORE_PASSWORD',
    'MISEBASE_RELEASE_KEY_ALIAS',
    'MISEBASE_RELEASE_KEY_PASSWORD'
)
$signing = @{}
foreach ($key in $requiredKeys) {
    $value = if ($properties.ContainsKey($key)) { $properties[$key] } else { [Environment]::GetEnvironmentVariable($key) }
    if ([string]::IsNullOrWhiteSpace($value)) {
        Fail "缺少签名配置 $key（写入 local.properties 或设置同名环境变量）"
    }
    $signing[$key] = $value
}

$keystorePath = $signing['MISEBASE_RELEASE_STORE_FILE']
if (-not (Test-Path -LiteralPath $keystorePath)) { Fail "找不到 keystore：$keystorePath" }
if ($keystorePath.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase)) {
    Fail "keystore 位于仓库内（$keystorePath）。发布签名必须放在主仓库之外。"
}

$keytool = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\keytool.exe' } else { 'keytool' }
$keystoreListing = & $keytool -list -v -keystore $keystorePath `
    -alias $signing['MISEBASE_RELEASE_KEY_ALIAS'] `
    -storepass $signing['MISEBASE_RELEASE_STORE_PASSWORD'] 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) { Fail "keystore 无法打开或别名不存在：$keystoreListing" }

$certFingerprint = ''
if ($keystoreListing -match 'SHA256:\s*([0-9A-Fa-f:]+)') {
    $certFingerprint = ($matches[1] -replace ':', '').ToUpperInvariant()
}
if ([string]::IsNullOrEmpty($certFingerprint)) { Fail '无法读取 keystore 的 SHA-256 指纹' }
Write-Host "    Keystore: $keystorePath"
Write-Host "    Alias: $($signing['MISEBASE_RELEASE_KEY_ALIAS'])"
Write-Host "    Cert SHA-256: $certFingerprint"

# ---------------------------------------------------------------------------
# 3. 全量门禁
# ---------------------------------------------------------------------------
Write-Step '3/6 运行全量门禁（test / assembleRelease / ktlintCheck / detekt）'

$tasks = if ($SkipTests) {
    @('assembleRelease', 'ktlintCheck', 'detekt')
} else {
    @('test', 'assembleRelease', 'ktlintCheck', 'detekt')
}
$gradleArguments = @('--console=plain') + $tasks
& (Join-Path $root 'gradlew.bat') @gradleArguments
if ($LASTEXITCODE -ne 0) { Fail "Gradle 构建失败（退出码 $LASTEXITCODE）" }

$apkPath = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path -LiteralPath $apkPath)) { Fail "未生成 APK：$apkPath" }

# ---------------------------------------------------------------------------
# 4. 产物校验
# ---------------------------------------------------------------------------
Write-Step '4/6 校验 APK 签名与版本'

$badging = & $aapt2 dump badging $apkPath 2>&1 | Out-String
if ($badging -notmatch "package: name='com\.culinary'") { Fail 'applicationId 不是 com.culinary（包名锁定，禁止修改）' }
if ($badging -notmatch "versionCode='(\d+)'") { Fail '无法读取 versionCode' }
$apkVersionCode = $matches[1]
if ($badging -notmatch "versionName='([^']+)'") { Fail '无法读取 versionName' }
$apkVersionName = $matches[1]

$verifyOutput = & $apksigner verify --verbose --print-certs $apkPath 2>&1 | Out-String
$verifyExitCode = $LASTEXITCODE
if ($verifyExitCode -ne 0 -or $verifyOutput -notmatch 'Verifies') {
    Fail "APK 签名校验失败（apksigner 退出码 $verifyExitCode）：$verifyOutput"
}
if ($verifyOutput -notmatch 'SHA-256 digest:\s*([0-9a-fA-F]+)') { Fail 'apksigner 未输出证书 SHA-256' }
$apkCertFingerprint = $matches[1].ToUpperInvariant()
if ($apkCertFingerprint -ne $certFingerprint) {
    Fail "APK 证书指纹与 keystore 不一致（APK=$apkCertFingerprint，Keystore=$certFingerprint）"
}
Write-Host "    Package: com.culinary / versionCode=$apkVersionCode / versionName=$apkVersionName"
Write-Host "    APK cert SHA-256: $apkCertFingerprint（与 keystore 一致）"

# ---------------------------------------------------------------------------
# 5. 输出
# ---------------------------------------------------------------------------
Write-Step '5/6 输出产物'

$hash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash
$sizeMb = [math]::Round((Get-Item -LiteralPath $apkPath).Length / 1MB, 2)
$distPath = Join-Path $root $DistDir
New-Item -ItemType Directory -Force -Path $distPath | Out-Null
$artifactName = "MISEBASE-$apkVersionName-release.apk"
$artifactPath = Join-Path $distPath $artifactName
Copy-Item -LiteralPath $apkPath -Destination $artifactPath -Force
"$hash  $artifactName" | Set-Content -LiteralPath (Join-Path $distPath "$artifactName.sha256") -Encoding ASCII

Write-Step '6/6 完成'
Write-Host ''
Write-Host '发布产物' -ForegroundColor Green
Write-Host "    源 APK:   $apkPath"
Write-Host "    分发副本: $artifactPath"
Write-Host "    大小:     $sizeMb MB"
Write-Host "    SHA-256:  $hash"
Write-Host "    校验文件: $(Join-Path $distPath "$artifactName.sha256")"
Write-Host ''
Write-Host '提醒：keystore 与密码必须单独双备份；丢失后无法为已发布版本升级。' -ForegroundColor Yellow
