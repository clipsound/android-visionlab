"""
Esporta YOLO (Ultralytics) in TFLite FLOAT32 per VisionLab.

Prerequisiti:
  - tools/requirements.txt (ultralytics)
  - tools/requirements-export.txt (TensorFlow, numpy, onnx2tf, …)

Da cartella tools:
  py.cmd -m pip install -r requirements.txt
  py.cmd -m pip install --ignore-installed -r requirements-export.txt --extra-index-url https://pypi.ngc.nvidia.com
  py.cmd export_yolo_tflite.py

Se l'export fallisce dopo tentativi Ultralytics con `uv`, esegui:
  install_export_deps.bat

Poi copia il .tflite in:
  app/src/main/assets/models/yolov8n_float32.tflite
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path


def _preflight_export_deps() -> None:
    """Evita che Ultralytics invochi `uv pip` (spesso fragile su Windows) se mancano pacchetti."""
    os.environ.setdefault("YOLO_AUTOINSTALL", "0")
    tools = Path(__file__).resolve().parent
    hint = (
        f"Dalla cartella `{tools}` esegui:\n"
        "  install_export_deps.bat\n"
        "oppure:\n"
        f'  .venv\\Scripts\\python.exe -m pip install --ignore-installed -r requirements-export.txt '
        f"--extra-index-url https://pypi.ngc.nvidia.com\n"
    )
    try:
        import tensorflow  # noqa: F401
    except ImportError as e:
        raise SystemExit(
            "Manca TensorFlow (serve per la catena ONNX -> SavedModel -> TFLite).\n\n"
            + hint
            + "\nSe vedi errori 'Accesso negato' o install a metà, chiudi IDE/terminali che usano il venv.\n\n"
            f"Dettaglio: {e}"
        ) from e
    try:
        import onnx2tf  # noqa: F401
    except ImportError as e:
        raise SystemExit(
            "Manca onnx2tf (Ultralytics lo usa per convertire ONNX in SavedModel).\n\n" + hint + f"\nDettaglio: {e}"
        ) from e


def main() -> int:
    _preflight_export_deps()

    tools_dir = Path(__file__).resolve().parent
    default_weights = tools_dir / "weights" / "yolo11n.pt"

    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--weights",
        type=str,
        default=str(default_weights),
        help=f"Percorso al .pt (default: {default_weights})",
    )
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument("--out", type=str, default="yolov8n_float32.tflite")
    args = parser.parse_args()

    from ultralytics import YOLO

    weights = Path(args.weights)
    if not weights.is_file():
        raise SystemExit(
            f"File non trovato: {weights}\n"
            f"Suggerimento: esegui prima `python download_weights.py` (venv attivo) "
            f"oppure passa --weights con un path valido.",
        )

    model = YOLO(str(weights))

    # int8=False -> TFLite float32 (più semplice da integrare nell'app MVP)
    exported = model.export(format="tflite", imgsz=args.imgsz, int8=False)

    if isinstance(exported, (list, tuple)):
        exported_path = Path(str(exported[0]))
    else:
        exported_path = Path(str(exported))

    if not exported_path.exists():
        raise SystemExit(f"Export fallito: file non trovato: {exported_path}")

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_bytes(exported_path.read_bytes())
    print(f"OK: scritto {out_path.resolve()} (copiato da {exported_path.resolve()})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
