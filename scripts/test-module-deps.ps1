# 依赖检查违规注入测试入口（MB-S1-001）
# 等价于：check-module-deps.ps1 -SelfTest

#requires -Version 5.1
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$scriptDirectory = $PSScriptRoot
if ([string]::IsNullOrEmpty($scriptDirectory)) { $scriptDirectory = Split-Path -Parent $PSCommandPath }
& (Join-Path $scriptDirectory 'check-module-deps.ps1') -SelfTest
exit $LASTEXITCODE
