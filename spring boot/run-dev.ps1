$ErrorActionPreference = 'Stop'
Write-Host "Running Spring Boot (dev profile) on port ${env:PORT -as [string] ?? '8080'}"
if (-not $env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE = 'default' }
if (-not $env:PORT) { $env:PORT = '8080' }
./mvnw.cmd -DskipTests spring-boot:run

