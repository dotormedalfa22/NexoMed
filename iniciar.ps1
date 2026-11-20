# Arranca NexoMed en local: carga .env, compila si hace falta y corre el WAR ejecutable.
# Uso:  powershell -ExecutionPolicy Bypass -File iniciar.ps1 [-Compilar]
param([switch]$Compilar)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path '.env')) { throw 'Falta .env (copie .env.example y llene los valores).' }
Get-Content '.env' | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    Set-Item -Path "env:$k" -Value $v
}

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) { $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17' }
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'

if ($Compilar -or -not (Test-Path 'target\nexomed.war')) {
    $mvn = Get-ChildItem "$env:USERPROFILE\.m2\wrapper\dists" -Recurse -Filter mvn.cmd -ErrorAction SilentlyContinue |
           Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    if (-not $mvn) { $mvn = 'mvn' }   # requiere Maven 3.6.3+
    & $mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'La compilación falló' }
}

Write-Host "NexoMed en http://localhost:$($env:PUERTO)/nexomed/  (Swagger: /nexomed/swagger-ui.html)"
& $java -jar 'target\nexomed.war'
