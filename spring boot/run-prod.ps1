$ErrorActionPreference = 'Stop'
$envFile = Join-Path $PSScriptRoot 'env.prod.ps1'
if (Test-Path $envFile) {
  Write-Host "Loading production environment from $envFile"
  . $envFile
} else {
  Write-Warning "env.prod.ps1 not found. Create it from env.prod.sample.ps1"
}
if (-not $env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE = 'prod' }
if (-not $env:PORT) { $env:PORT = '8080' }
Write-Host "Running Spring Boot (prod profile) on port $env:PORT"
./mvnw.cmd -DskipTests spring-boot:run

