@echo off
set "HERE=%~dp0"
cd /d "%HERE%"
set "PY=%HERE%.venv\Scripts\python.exe"
if not exist "%PY%" (
  echo ERRORE: venv non trovato. Esegui prima setup_venv.ps1 dalla cartella tools.
  pause
  exit /b 1
)
echo [VisionLab] Aggiorno pip...
"%PY%" -m pip install --upgrade pip
echo [VisionLab] Riparo numpy (RECORD mancante dopo uv / install a meta)...
"%PY%" -m pip install --ignore-installed "numpy>=1.26.0,<2.2.0"
echo [VisionLab] Installo TensorFlow, onnx2tf e dipendenze export...
"%PY%" -m pip install --ignore-installed -r "%HERE%requirements-export.txt" --extra-index-url https://pypi.ngc.nvidia.com
echo.
echo Fatto. Riprova dalla root VisionLab:
echo   tools\.venv\Scripts\python.exe tools\export_yolo_tflite.py --weights tools\weights\yolo11n.pt --out tools\weights\yolov8n_float32.tflite
pause
