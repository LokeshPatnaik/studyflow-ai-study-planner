$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
if (-not (Test-Path '.env')) { Copy-Item '.env.example' '.env' }
docker compose up --build -d
Start-Process 'http://localhost:5173'
Write-Host 'AI Study Planner Agent is starting at http://localhost:5173'

