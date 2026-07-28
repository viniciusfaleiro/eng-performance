<#
  Build the app, package it as a Docker image, and bring up the full local environment
  (PostgreSQL + the app) with docker compose. Windows counterpart of run-local.sh.

    .\scripts\run-local.ps1          build image + start db & app  -> http://localhost:8080
    .\scripts\run-local.ps1 down     stop and remove the containers (keeps the DB volume)
    .\scripts\run-local.ps1 logs     follow the app logs

  Prerequisites: Docker Desktop + JDK 21. Run from anywhere; the script cd's to the repo root.
#>
param([ValidateSet('up', 'down', 'logs')][string]$Action = 'up')

$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$Image = 'eng-performance:local'

switch ($Action) {
  'down' { docker compose --profile full down; exit $LASTEXITCODE }
  'logs' { docker compose --profile full logs -f app; exit $LASTEXITCODE }
}

Write-Host '==> [1/4] Building the boot jar (.\gradlew.bat :bootstrap:bootJar)...' -ForegroundColor Cyan
.\gradlew.bat :bootstrap:bootJar -q
if ($LASTEXITCODE -ne 0) { throw 'gradle build failed' }

$jar = Get-ChildItem app/bootstrap/build/libs/bootstrap-*-SNAPSHOT.jar |
  Where-Object { $_.Name -notlike '*-plain.jar' } | Select-Object -First 1
if (-not $jar) { throw 'boot jar not found under app/bootstrap/build/libs' }

Write-Host "==> [2/4] Building the Docker image $Image (from $($jar.Name))..." -ForegroundColor Cyan
Copy-Item $jar.FullName app.jar -Force
try {
  docker build -t $Image .
  if ($LASTEXITCODE -ne 0) { throw 'docker build failed' }
}
finally { Remove-Item app.jar -ErrorAction SilentlyContinue }

Write-Host '==> [3/4] Starting db + app (docker compose, profile "full")...' -ForegroundColor Cyan
docker compose --profile full up -d
if ($LASTEXITCODE -ne 0) { throw 'docker compose up failed' }

Write-Host '==> [4/4] Waiting for the app on http://localhost:8080 ...' -ForegroundColor Cyan
for ($i = 0; $i -lt 90; $i++) {
  try {
    Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/' -TimeoutSec 3 | Out-Null
    Write-Host ''
    Write-Host 'Up and running -> http://localhost:8080' -ForegroundColor Green
    Write-Host '   logs:  .\scripts\run-local.ps1 logs'
    Write-Host '   stop:  .\scripts\run-local.ps1 down'
    exit 0
  }
  catch { Start-Sleep -Seconds 2 }
}
Write-Host '!! The app did not become healthy in time. Check: docker compose --profile full logs app' -ForegroundColor Red
exit 1
