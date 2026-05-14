# VisionLab (Android)

**VisionLab** è un’applicazione Android di **visione artificiale** in tempo reale e da **galleria**, pensata come laboratorio per provare modelli **TensorFlow Lite** (in particolare **YOLO** in formato TFLite float32) con **CameraX**, overlay delle **bounding box** e metriche di **prestazione** (FPS e latenza).

Questo repository contiene:

- **`app/`** — progetto Android (Kotlin, Jetpack Compose, CameraX, TFLite).
- **`tools/`** — ambiente Python (venv) e script per **scaricare i pesi** Ultralytics ed **esportare** un modello YOLO in `.tflite` compatibile con l’app.

---

## Indice

1. [Requisiti](#requisiti)
2. [Struttura del repository](#struttura-del-repository)
3. [Applicazione Android](#applicazione-android)
   - [Architettura e moduli principali](#architettura-e-moduli-principali)
   - [Modelli e asset](#modelli-e-asset)
   - [Build e avvio](#build-e-avvio)
   - [Funzionalità UI](#funzionalità-ui)
   - [Fotocamera e prestazioni](#fotocamera-e-prestazioni)
   - [Dipendenze rilevanti](#dipendenze-rilevanti)
   - [Log e debug](#log-e-debug)
   - [Limitazioni note](#limitazioni-note)
4. [Cartella `tools/` (Python)](#cartella-tools-python)
   - [Perché due file `requirements`](#perché-due-file-requirements)
   - [Creazione del venv (Windows)](#creazione-del-venv-windows)
   - [Download dei pesi `.pt`](#download-dei-pt)
   - [Export verso TFLite](#export-verso-tflite)
   - [Integrazione con l’app](#integrazione-con-lapp)
   - [Risoluzione problemi comuni (tools)](#risoluzione-problemi-comuni-tools)
5. [Licenza e contributi](#licenza-e-contributi)

---

## Requisiti

### Per l’app Android

- **Android Studio** (versione recente, con Android SDK e build tools).
- **JDK 17** (allineato alle impostazioni Gradle del progetto).
- Dispositivo o emulatore con **API** compatibile con `minSdk` / `compileSdk` definiti in `app/build.gradle.kts`.

### Per gli script in `tools/`

- **Windows** (gli script `.ps1`, `.bat`, `.cmd` sono orientati a questo ambiente; su Linux/macOS si possono replicare i comandi manualmente).
- **Python 3.10+** consigliato (il launcher `py` su Windows è supportato dagli script).
- Connessione **Internet** per `pip` e per il download dei pesi Ultralytics.

---

## Struttura del repository

| Percorso | Descrizione |
|----------|-------------|
| `app/` | Modulo applicativo Android (Compose, ViewModel, detector, UI). |
| `app/src/main/assets/models/` | Posizione prevista per i file **`.tflite`** (vedi `.gitignore`: i binari grandi possono essere esclusi dal versionamento). |
| `app/src/main/assets/labels/` | Etichette **COCO** (o analoghe) per mappare class_id → nome. |
| `gradle/` | Wrapper Gradle. |
| `tools/` | Script Python, requirements, pesi scaricati in `tools/weights/`, output export. |
| `tools/weights/` | Directory di destinazione per `.pt` scaricati (`download_weights.py`); solo `.gitkeep` è versionato di default. |

---

## Applicazione Android

### Architettura e moduli principali

L’app segue un flusso tipico **CameraX → analisi frame → inferenza TFLite → post-processing (NMS, letterbox) → UI (overlay)**.

Elementi concettuali:

- **`ModelRegistry`** — elenco dei modelli disponibili (path sotto `assets/models/`, dimensione input, uso GPU opzionale, ecc.).
- **Detector YOLO** — preprocessing (ridimensionamento / letterbox coerente con l’export), interpretazione tensori di output, **NMS**, disegno box in coordinate immagine.
- **ViewModel** — stato UI (modalità Live/Gallery, modello selezionato, risultati, metriche FPS/ms), caricamento modello in modo asincrono per non bloccare l’UI.
- **Schermata principale** — switch **Live** / **Galleria**, selezione modello, overlay rilevamenti e metriche.

Per i dettagli implementativi, esplora `app/src/main/java/com/visionlab/app/`.

### Modelli e asset

- Il **nome file** del modello TFLite deve combaciare con quanto registrato in `ModelRegistry` (es. default tipico: `yolov8n_float32.tflite` in `assets/models/`).
- Le **label** devono essere coerenti con il dataset di addestramento del modello (per i pesi predefiniti Ultralytics/YOLO spesso si usano le **80 classi COCO**).
- Il repository può **ignorare** i `.tflite` in `assets/models/` (vedi `.gitignore`) per evitare commit di file molto grandi: in quel caso vanno **generati o copiati** localmente dopo l’export (vedi sezione [Integrazione con l’app](#integrazione-con-lapp)).

### Build e avvio

1. Apri la cartella del progetto in **Android Studio**.
2. Attendi **Gradle Sync**.
3. Collega un dispositivo o avvia un emulatore.
4. Esegui la configuration **Run** sul modulo `app`.

Se manca `local.properties`, Android Studio lo rigenera con il path dell’SDK.

### Funzionalità UI

- **Modalità Live** — anteprima camera con analisi continua e overlay in tempo reale.
- **Modalità Galleria** — scelta di un’immagine dal dispositivo, inferenza singola, overlay sul risultato.
- **Selezione modello** — consente di confrontare varianti TFLite (float32, eventuali altri file registrati).
- **Metriche** — indicazioni approssimative di **FPS** e **tempo di inferenza** utili per profilazione rapida su dispositivo.

### Fotocamera e prestazioni

- **CameraX** gestisce preview e pipeline di analisi immagine.
- **TensorFlow Lite** con supporto opzionale **GPU** (`tensorflow-lite-gpu` e API correlate): su dispositivi compatibili può accelerare l’inferenza; in caso di errori o dispositivi non supportati si può ricadere su **CPU** (dipende da come è esposto il flag nel registry / UI).
- Risoluzione e strategia di acquisizione possono influenzare **FPS** e **qualità** del rilevamento: risoluzioni molto alte aumentano il costo per frame.

### Dipendenze rilevanti

Da `app/build.gradle.kts` (versioni indicative, verificare nel file):

- **Jetpack Compose** + **Material 3** — UI dichiarativa.
- **CameraX** — camera e analisi frame.
- **TensorFlow Lite** — runtime inferenza; modulo **GPU** per delega accelerata quando disponibile.

È stato curato l’allineamento delle versioni TFLite per evitare conflitti tra artifact diversi (es. transitivi non allineati).

### Log e debug

- Filtra **Logcat** con tag applicativi (es. `VisionLab` o il package `com.visionlab.app` secondo implementazione) per messaggi di caricamento modello, errori TFLite o CameraX.

### Limitazioni note

- Prestazioni e supporto **GPU** variano per **vendor** e driver.
- Modelli e **dimensione input** devono essere coerenti con l’export (es. 640×640) e con il preprocessing nell’app.
- Alcuni dispositivi impongono limiti su risoluzioni o formati di immagine in analisi: in caso di preview nera o crash, verificare permessi camera e profilo CameraX.

---

## Cartella `tools/` (Python)

La cartella `tools/` fornisce una pipeline **riproducibile** per:

1. Creare un **virtual environment** dedicato (consigliato: `tools/.venv`).
2. Installare **Ultralytics** (`requirements.txt`).
3. Installare lo stack **TensorFlow / onnx2tf / …** per la conversione (`requirements-export.txt`).
4. **Scaricare** i file `.pt` (es. `yolo11n.pt`) in `tools/weights/`.
5. **Esportare** un file `.tflite` float32 pronto per l’app.

### Perché due file `requirements`

| File | Scopo |
|------|--------|
| `requirements.txt` | Dipendenze “leggere” per Ultralytics / uso base (es. `ultralytics>=8.3.0`). |
| `requirements-export.txt` | Stack pesante per la catena **PyTorch → ONNX → SavedModel → TFLite**: `tensorflow`, `onnx2tf`, versioni di `numpy` compatibili con TF su Windows, ecc. |

**Ordine consigliato:** prima `requirements.txt`, poi `requirements-export.txt` (spesso con `--ignore-installed` per evitare conflitti su `numpy`, come negli script forniti).

### Creazione del venv (Windows)

Script utili (nella cartella `tools/`):

| Script | Ruolo |
|--------|--------|
| `setup_venv.ps1` | Crea `tools/.venv` e installa dipendenze base. |
| `activate_venv.bat` | Attiva il venv in una sessione `cmd`. |
| `py.cmd` / `env.cmd` | Comodi wrapper per invocare il Python del venv. |
| `install_export_deps.bat` | Installa le dipendenze di export con i flag raccomandati (include indice extra NVIDIA per alcuni wheel). |

**Policy di esecuzione PowerShell:** se `setup_venv.ps1` è bloccato, da amministratore o per sessione può essere necessario `Set-ExecutionPolicy RemoteSigned` (valuta i rischi per la tua organizzazione).

### Download dei pesi `.pt`

`download_weights.py`:

- Default: scarica `yolo11n.pt` in `tools/weights/`.
- Parametri: `--model` (uno o più nomi), `--out-dir` (directory di destinazione).

Esempio:

```bat
cd tools
.venv\Scripts\activate
python download_weights.py
python download_weights.py --model yolov8n.pt yolo11n.pt
```

### Export verso TFLite

`export_yolo_tflite.py`:

- Esegue preflight: imposta `YOLO_AUTOINSTALL=0` e verifica presenza di **TensorFlow** e **onnx2tf** (messaggi guida se mancano).
- Default pesi: `tools/weights/yolo11n.pt`.
- Default output: `yolov8n_float32.tflite` nella directory corrente (parametro `--out`).
- Chiama `YOLO.export(format="tflite", imgsz=..., int8=False)` per ottenere **float32** (più semplice da integrare nell’MVP Android).

Esempio:

```bat
cd tools
.venv\Scripts\activate
python -m pip install -r requirements.txt
python -m pip install --ignore-installed -r requirements-export.txt --extra-index-url https://pypi.ngc.nvidia.com
python export_yolo_tflite.py --weights weights\yolo11n.pt --imgsz 640 --out yolov8n_float32.tflite
```

In alternativa, dopo aver preparato il venv, eseguire **`install_export_deps.bat`** se l’export fallisce per dipendenze mancanti o conflitti, come suggerito nel docstring dello script.

### Integrazione con l’app

1. Genera (o copia) il file **`.tflite`** nella cartella:

   `app/src/main/assets/models/`

2. Assicurati che il **nome file** corrisponda a una voce in `ModelRegistry` (o aggiungi una nuova voce con nome, input size e opzioni GPU coerenti).

3. **Ricompila** l’app: gli asset sotto `src/main/assets/` vengono inclusi nell’APK.

> Nota: se `.gitignore` esclude i `.tflite`, il modello resta solo sulla tua macchina finché non lo aggiungi esplicitamente (o usi Git LFS / release artifact).

### Risoluzione problemi comuni (tools)

| Sintomo | Azione suggerita |
|---------|------------------|
| `Manca TensorFlow` / `Manca onnx2tf` | Esegui `install_export_deps.bat` o il comando `pip` completo con `requirements-export.txt` e l’extra index NVIDIA. |
| Conflitto **numpy** / installazioni corrotte | Chiudi IDE o processi che usano il venv; reinstalla con `--ignore-installed` come negli script. |
| Ultralytics tenta `uv pip` o auto-install fragile | Lo script imposta `YOLO_AUTOINSTALL=0` dopo preflight: mantieni dipendenze allineate manualmente. |
| `Accesso negato` su Windows | Chiudi terminali/IDE che lockano `.venv`; riapri prompt come utente con permessi sulla cartella. |
| Pesi non trovati | Esegui `download_weights.py` o passa `--weights` con path assoluto/relativo corretto. |

---

## Licenza e contributi

- Verifica la presenza di un file `LICENSE` nella root del repository per i termini di distribuzione.
- Per contributi: fork, branch feature, pull request con descrizione chiara delle modifiche e test manuali su dispositivo reale ove possibile.

---

## Riferimento rapido comandi Git

```bash
git add .
git commit -m "Aggiunge README e documentazione tools/app"
git remote add origin https://github.com/clipsound/android-visionlab.git
git branch -M main
git push -u origin main
```

Se `origin` esiste già, usa `git remote set-url origin <url>` invece di `add`.
