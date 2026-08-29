param(
    [string]$GradleExecutable = (Join-Path $PSScriptRoot '..\gradlew.bat')
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$signingDirectory = Join-Path $repositoryRoot '.local-signing'
$keystorePath = Join-Path $signingDirectory 'bt-notifier-upload.jks'
$encryptedPasswordPath = Join-Path $signingDirectory 'bt-notifier-upload-password.dpapi'
$keyAlias = 'bt-notifier-upload'

if (-not (Test-Path -LiteralPath $signingDirectory -PathType Container)) {
    New-Item -ItemType Directory -Path $signingDirectory | Out-Null
}

$hasKeystore = Test-Path -LiteralPath $keystorePath -PathType Leaf
$hasEncryptedPassword = Test-Path -LiteralPath $encryptedPasswordPath -PathType Leaf

if ($hasKeystore -xor $hasEncryptedPassword) {
    throw 'The upload keystore and its encrypted password must either both exist or both be absent. Restore the missing file before building.'
}

if (-not $hasKeystore) {
    $passwordBytes = [byte[]]::new(32)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($passwordBytes)
    $plainPassword = [Convert]::ToBase64String($passwordBytes).TrimEnd('=').Replace('+', 'A').Replace('/', 'B') + 'Aa9!'

    & keytool `
        -genkeypair `
        -keystore $keystorePath `
        -storetype JKS `
        -storepass $plainPassword `
        -keypass $plainPassword `
        -alias $keyAlias `
        -keyalg RSA `
        -keysize 4096 `
        -validity 10000 `
        -dname 'CN=BT Notifier Upload, O=STFUAI, C=US'

    if ($LASTEXITCODE -ne 0) {
        throw "keytool failed with exit code $LASTEXITCODE."
    }

    $securePassword = ConvertTo-SecureString $plainPassword -AsPlainText -Force
    $securePassword | ConvertFrom-SecureString | Set-Content -LiteralPath $encryptedPasswordPath -Encoding Ascii
}

$encryptedPassword = (Get-Content -LiteralPath $encryptedPasswordPath -Raw).Trim()
$securePassword = ConvertTo-SecureString $encryptedPassword
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)

try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $env:BT_NOTIFIER_UPLOAD_PASSWORD = $plainPassword

    Push-Location $repositoryRoot
    try {
        & $GradleExecutable testDebugUnitTest lintDebug bundleRelease
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:BT_NOTIFIER_UPLOAD_PASSWORD = $null
    $plainPassword = $null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}

$bundlePath = Join-Path $repositoryRoot 'build\app\outputs\bundle\release\app-release.aab'
if (-not (Test-Path -LiteralPath $bundlePath -PathType Leaf)) {
    throw "The signed bundle was not produced at $bundlePath."
}

Write-Output "Signed bundle: $bundlePath"
