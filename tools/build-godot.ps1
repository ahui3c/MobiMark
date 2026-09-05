param([Parameter(Mandatory=$true)][string]$Godot)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$enginePath = (Resolve-Path -LiteralPath $Godot).Path
$gamePath = Join-Path $projectRoot 'godot'
$packPath = Join-Path $projectRoot 'app/src/main/assets/prism_front.pck'
& $enginePath --headless --path $gamePath --editor --import
if ($LASTEXITCODE -ne 0) { throw 'Godot import failed' }
& $enginePath --headless --path $gamePath --script res://tools/prepare_imports.gd
if ($LASTEXITCODE -ne 0) { throw 'Texture configuration failed' }
& $enginePath --headless --path $gamePath --editor --import
if ($LASTEXITCODE -ne 0) { throw 'Compressed texture import failed' }
& $enginePath --headless --path $gamePath --export-pack 'Android pack' $packPath
if ($LASTEXITCODE -ne 0) { throw 'Godot pack export failed' }
Get-Item -LiteralPath $packPath
