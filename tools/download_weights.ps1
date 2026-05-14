$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
& (Join-Path $here ".venv\Scripts\python.exe") (Join-Path $here "download_weights.py") @args
