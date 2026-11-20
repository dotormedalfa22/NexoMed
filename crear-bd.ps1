# Crea la base NexoMed y el usuario app_nexomed con la contraseña de .env (BD_CONTRASENA).
# Pide la contraseña de root de MariaDB. Se ejecuta una sola vez; si ya existen, no cambia nada.
# Uso:  powershell -ExecutionPolicy Bypass -File crear-bd.ps1
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path '.env')) { throw 'Falta .env (copie .env.example y llene los valores).' }
$linea = Get-Content '.env' | Where-Object { $_ -match '^BD_CONTRASENA=' } | Select-Object -First 1
$contrasena = ($linea -split '=', 2)[1]
if (-not $contrasena) { throw 'BD_CONTRASENA está vacía en .env' }

$mysql = 'C:\Program Files\MariaDB 10.11\bin\mysql.exe'
if (-not (Test-Path $mysql)) { $mysql = 'mysql' }

$sql = (Get-Content 'sql\00_crear_bd_y_usuario.sql' -Raw) -replace 'CAMBIAR_CONTRASENA', $contrasena.Replace("'", "''")
$sql | & $mysql -u root -p --default-character-set=utf8mb4
if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la base (revise la contraseña de root).' }
Write-Host 'Base NexoMed y usuario app_nexomed listos. Las tablas se crean al iniciar la aplicación (iniciar.ps1).'
