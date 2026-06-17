# Publier le projet sur GitHub (compte Deca7776)
# Executez ce script APRES avoir cree le depot sur https://github.com/new

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

$remote = "https://github.com/Deca7776/cin-haiti.git"

if (-not (git rev-parse --git-dir 2>$null)) {
    Write-Error "Pas de depot git ici. Lancez d'abord: git init -b main"
}

$existing = git remote get-url origin 2>$null
if ($LASTEXITCODE -ne 0) {
    git remote add origin $remote
    Write-Host "Remote origin ajoute: $remote"
} else {
    Write-Host "Remote origin existant: $existing"
}

Write-Host ""
Write-Host "Connexion GitHub (si pas encore fait) : gh auth login"
Write-Host "Puis push : git push -u origin main"
Write-Host ""

$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
if (Get-Command gh -ErrorAction SilentlyContinue) {
    $auth = gh auth status 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "GitHub CLI connecte — creation du depot..."
        gh repo create Deca7776/cin-haiti --public --source=. --remote=origin --push --description "Microservice numerisation CIN Haiti - ONI"
    } else {
        Write-Host "GitHub CLI non connecte. Etapes manuelles :"
        Write-Host "  1. https://github.com/new -> nom: cin-haiti, public, SANS README"
        Write-Host "  2. gh auth login"
        Write-Host "  3. Relancez ce script OU: git push -u origin main"
    }
} else {
    Write-Host "Installez GitHub CLI: winget install GitHub.cli"
    Write-Host "Puis: gh auth login && git push -u origin main"
}
