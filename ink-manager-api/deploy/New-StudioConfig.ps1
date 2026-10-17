param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z][a-z0-9-]{2,39}$')][string]$StudioId,
    [Parameter(Mandatory=$true)][ValidatePattern('^(?=.{1,253}$)([a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,63}$')][string]$Domain,
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}$')][string]$ImageTag,
    [string]$OutputPath = (Join-Path $PSScriptRoot '../.env')
)
$ErrorActionPreference = 'Stop'
function New-InkSecret {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
}
$path = [IO.Path]::GetFullPath($OutputPath)
$lines = @(
    "INK_STUDIO_ID=$StudioId",
    "POSTGRES_DB=ink_manager",
    "POSTGRES_USER=ink_manager",
    ("POSTGRES_PASSWORD=" + (New-InkSecret)),
    ("JWT_SECRET=" + (New-InkSecret)),
    ("STUDIO_REGISTRATION_CODE=" + (New-InkSecret)),
    "INK_DOMAIN=$Domain",
    "INK_IMAGE_TAG=$ImageTag"
)
# CreateNew refuses to replace an existing installation's secrets.
$stream = [IO.File]::Open($path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
try {
    $writer = New-Object IO.StreamWriter($stream, (New-Object Text.UTF8Encoding($false)))
    try { $writer.WriteLine(($lines -join [Environment]::NewLine)) } finally { $writer.Dispose() }
} finally { $stream.Dispose() }
Write-Output 'Configuracao criada. Proteja o arquivo: ele contem credenciais e convite.'
Write-Output 'Execute o Compose de producao com --env-file apontando para esse arquivo.'
