@echo off
set "HERE=%~dp0"
"%HERE%.venv\Scripts\python.exe" "%HERE%download_weights.py" %*
