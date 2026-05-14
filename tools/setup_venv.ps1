$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $here

$venvPython = Join-Path $here ".venv\Scripts\python.exe"
if (-not (Test-Path $venvPython)) {
    Write-Host "Creo virtual environment in: $here\.venv"
    py -3 -m venv .venv
    if ($LASTEXITCODE -ne 0) {
        python -m venv .venv
    }
}

& (Join-Path $here ".venv\Scripts\python.exe") -m pip install --upgrade pip
& (Join-Path $here ".venv\Scripts\pip.exe") install -r (Join-Path $here "requirements.txt")

Write-Host "OK."
Write-Host ""
Write-Host "Per export TFLite serve anche TensorFlow (numpy < 2.2). Da tools:"
Write-Host "  install_export_deps.bat"
Write-Host "  (oppure: py.cmd -m pip install -r requirements-export.txt)"
Write-Host ""
Write-Host "PowerShell blocca spesso Activate.ps1 (Execution Policy). Alternative:"
Write-Host "  1) CMD:    call .\activate_venv.bat"
Write-Host "  2) Ovunque: .\py.cmd download_weights.py"
Write-Host "  3) PS solo per questa sessione:  powershell -NoProfile -ExecutionPolicy Bypass"
Write-Host "     poi:   .\.venv\Scripts\Activate.ps1"
Write-Host "  4) Policy consigliata (solo utente):"
Write-Host "       Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned"
