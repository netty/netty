# ----------------------------------------------------------------------------
# Copyright 2026 The Netty Project
#
# The Netty Project licenses this file to you under the Apache License,
# version 2.0 (the "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at:
#
#   https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------
#
# PowerShell port of mvn_affected.sh for the Windows build, which must not run under Git bash
# (its PATH puts GNU coreutils' link.exe in front of the MSVC linker, which breaks the quiche build).
# Keep the behavior in sync with mvn_affected.sh, see there for the description of NETTY_AFFECTED_MODULES.
#
# Usage: mvn_affected.ps1 <maven arguments>
#
# NETTY_MVN can be used to override the maven executable (default ./mvnw.cmd).

$ErrorActionPreference = 'Stop'

Set-Location (Join-Path $PSScriptRoot '..' '..')

$mvn = if ($env:NETTY_MVN) { $env:NETTY_MVN } else { './mvnw.cmd' }
$affected = if ($env:NETTY_AFFECTED_MODULES) { $env:NETTY_AFFECTED_MODULES } else { 'ALL' }
$mavenArgs = @($args)

function Invoke-Maven {
    & $mvn @args
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

if ($affected -eq 'ALL') {
    Invoke-Maven @mavenArgs
    exit 0
}
if ($affected -eq 'NONE') {
    Write-Host 'No module affected by the changes, skipping build'
    exit 0
}

# Second pass operates on the output of the first one, so don't clean it away again.
$argsWithoutClean = @($mavenArgs | Where-Object { $_ -ne 'clean' })

Write-Host "Building affected modules: $affected"
Write-Host "Pass 1/2: build (and install) $affected and their dependencies without tests"
$pass1Args = $mavenArgs + @('-pl', $affected, '-am', '-DskipTests=true')
Invoke-Maven @pass1Args
Write-Host "Pass 2/2: run the tests of $affected"
$pass2Args = $argsWithoutClean + @('-pl', $affected)
Invoke-Maven @pass2Args
