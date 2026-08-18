$ErrorActionPreference = "Stop"

$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($bytes)
} finally {
    $rng.Dispose()
}

$token = [Convert]::ToBase64String($bytes)
$token = $token.TrimEnd('=').Replace('+', '-').Replace('/', '_')

Write-Host "HEALTH_GUARD_DEVICE_TOKEN"
Write-Host $token
Write-Host ""
Write-Host "Copia este valor en Supabase Secrets y en la configuracion de Health Guard."
Write-Host "No lo agregues al repositorio ni lo compartas publicamente."
