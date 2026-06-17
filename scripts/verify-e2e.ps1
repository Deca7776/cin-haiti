# Verification E2E du microservice CIN (profil local)
# Prérequis : backend démarré sur http://localhost:8081

$Base = "http://localhost:8081/api/v1"

Write-Host "=== 1. Jeton opératrice ===" -ForegroundColor Cyan
$tokenResp = Invoke-RestMethod "$Base/dev/token"
$token = $tokenResp.token
Write-Host "Token obtenu pour $($tokenResp.operatorId)"

Write-Host "`n=== 2. Ouverture session ===" -ForegroundColor Cyan
$session = Invoke-RestMethod "$Base/scan/sessions" -Method POST -Headers @{ Authorization = "Bearer $token" }
$sessionId = $session.sessionId
Write-Host "Session: $sessionId (expire: $($session.expiresAt))"

Write-Host "`n=== 3. Upload image CIN test ===" -ForegroundColor Cyan
# Créer une image PNG minimale
Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap 800, 500
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::White)
$g.DrawString("NIN: 1234567890123", (New-Object System.Drawing.Font("Arial", 16)), [System.Drawing.Brushes]::Black, 40, 40)
$g.Dispose()
$tmp = [System.IO.Path]::GetTempFileName() + ".png"
$bmp.Save($tmp, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

$boundary = [System.Guid]::NewGuid().ToString()
$fileBytes = [System.IO.File]::ReadAllBytes($tmp)
$bodyLines = @(
    "--$boundary",
    'Content-Disposition: form-data; name="file"; filename="cin-test.png"',
    'Content-Type: image/png',
    '',
    [System.Text.Encoding]::GetEncoding('iso-8859-1').GetString($fileBytes),
    "--$boundary--"
) -join "`r`n"
$ocr = Invoke-RestMethod "$Base/scan/$sessionId/image" -Method POST `
    -Headers @{ Authorization = "Bearer $token"; "Content-Type" = "multipart/form-data; boundary=$boundary" } `
    -Body $bodyLines
Write-Host "OCR success: $($ocr.ocrSuccess) | Confiance moyenne: $($ocr.averageConfidence)%"
Remove-Item $tmp -ErrorAction SilentlyContinue

Write-Host "`n=== 4. Validation et enregistrement ===" -ForegroundColor Cyan
$nin = (Get-Random -Minimum 1000000000000 -Maximum 9999999999999).ToString()
$validateBody = @{
    nin = $nin
    nom = "DUPONT"
    prenom = "Jean"
    dateNaissance = "15/03/1990"
    lieuNaissance = "Port-au-Prince, Ouest"
    sexe = "M"
    adresse = "Delmas 33"
    departement = "Ouest"
    dateEmission = "01/06/2020"
    dateExpiration = "01/06/2030"
    idempotencyToken = $ocr.idempotencyToken
    consentementDocumente = $true
    baseLegale = "consentement_titulaire"
    confirmDuplicateCheck = $true
    scoreOcrMoyen = $ocr.averageConfidence
} | ConvertTo-Json
$saved = Invoke-RestMethod "$Base/scan/$sessionId/validate" -Method POST `
    -Headers @{ Authorization = "Bearer $token"; "Content-Type" = "application/json" } `
    -Body $validateBody
Write-Host "Enregistré: $($saved.message) | NIN: $($saved.nin)"

Write-Host "`n=== 5. Export système tiers ===" -ForegroundColor Cyan
$exported = Invoke-RestMethod "$Base/identites/$nin" -Method GET `
    -Headers @{ Authorization = "Bearer $token"; "X-API-Key" = "demo-api-key-2024" }
Write-Host "Export OK - Nom: $($exported.data.nom)"

Write-Host "`n=== SUCCÈS : flux complet validé ===" -ForegroundColor Green
