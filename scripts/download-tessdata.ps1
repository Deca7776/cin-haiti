# Télécharge fra.traineddata pour Tesseract (requis pour l'OCR)
$dest = Join-Path $PSScriptRoot "..\data\tessdata"
New-Item -ItemType Directory -Force -Path $dest | Out-Null
$url = "https://github.com/tesseract-ocr/tessdata/raw/main/fra.traineddata"
$out = Join-Path $dest "fra.traineddata"
if (Test-Path $out) {
    Write-Host "fra.traineddata deja present: $out"
    exit 0
}
Write-Host "Telechargement de fra.traineddata..."
Invoke-WebRequest -Uri $url -OutFile $out -UseBasicParsing
Write-Host "OK: $out"
