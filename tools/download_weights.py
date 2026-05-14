"""
Scarica automaticamente i weights Ultralytics (es. yolo11n.pt).

Uso (dalla cartella tools, con venv attivo):
  python download_weights.py
  python download_weights.py --model yolov8n.pt
  python download_weights.py --model yolo11n.pt yolov8n.pt
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--model",
        nargs="+",
        default=["yolo11n.pt"],
        help="Nome modello Ultralytics (es. yolo11n.pt, yolov8n.pt)",
    )
    parser.add_argument(
        "--out-dir",
        type=str,
        default="",
        help="Cartella di destinazione (default: tools/weights)",
    )
    args = parser.parse_args()

    tools_dir = Path(__file__).resolve().parent
    out_dir = Path(args.out_dir) if args.out_dir else (tools_dir / "weights")
    out_dir.mkdir(parents=True, exist_ok=True)

    from ultralytics import YOLO

    old = os.getcwd()
    try:
        os.chdir(out_dir)
        for name in args.model:
            if not name.endswith(".pt"):
                name = f"{name}.pt"
            path = out_dir / name
            if path.exists():
                print(f"Già presente: {path}")
                continue
            print(f"Download: {name} -> {out_dir}")
            YOLO(name)
            if not path.exists():
                raise SystemExit(f"Download fallito: file non trovato dopo YOLO({name!r}): {path}")
            print(f"OK: {path}")
    finally:
        os.chdir(old)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
