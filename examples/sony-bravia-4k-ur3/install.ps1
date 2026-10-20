# Builds the launcher, installs it on the Sony BRAVIA 4K UR3 as Home, and pushes this TV's theme
# (dock, hidden tiles, columns). Run it again after changing the launcher code; for a dock or wallpaper
# change alone, pushing the theme is enough (see README.md).
#
#   powershell -ExecutionPolicy Bypass -File examples\sony-bravia-4k-ur3\install.ps1
#   powershell -ExecutionPolicy Bypass -File examples\sony-bravia-4k-ur3\install.ps1 -TvHost 192.168.178.40
param([string]$TvHost = '192.168.178.26')
$ErrorActionPreference = 'Stop'
$repository = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
& powershell -ExecutionPolicy Bypass -File (Join-Path $repository 'launcher\build-and-install.ps1') -TvHost $TvHost
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& powershell -ExecutionPolicy Bypass -File (Join-Path $repository 'tools\theme.ps1') -TvHost $TvHost (Join-Path $PSScriptRoot 'theme')
exit $LASTEXITCODE
