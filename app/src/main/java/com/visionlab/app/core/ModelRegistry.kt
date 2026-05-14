package com.visionlab.app.core

enum class VisionTask {
    OBJECT_DETECTION,
    INSTANCE_SEGMENTATION,
    POSE_ESTIMATION,
    DEPTH_ESTIMATION,
    TEXT_OCR,
}

data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val task: VisionTask,
    val assetPath: String,
    val notes: String,
)

object ModelRegistry {
    val models: List<ModelDescriptor> = listOf(
        ModelDescriptor(
            id = "yolo_tflite_default",
            displayName = "YOLO (TFLite) — detection",
            task = VisionTask.OBJECT_DETECTION,
            assetPath = "models/yolov8n_float32.tflite",
            notes = "Classi COCO (80): non include strumenti musicali (es. chitarra). Per vocaboli aperti usa YOLO-World o un modello custom. Export TFLite FLOAT32.",
        ),
        ModelDescriptor(
            id = "seg_stub",
            displayName = "Segmentazione (prossimo)",
            task = VisionTask.INSTANCE_SEGMENTATION,
            assetPath = "",
            notes = "Placeholder: aggiungi un modello .tflite e un adapter dedicato.",
        ),
        ModelDescriptor(
            id = "pose_stub",
            displayName = "Pose (prossimo)",
            task = VisionTask.POSE_ESTIMATION,
            assetPath = "",
            notes = "Placeholder: es. MoveNet / YOLO-pose in TFLite.",
        ),
        ModelDescriptor(
            id = "depth_stub",
            displayName = "Profondità (prossimo)",
            task = VisionTask.DEPTH_ESTIMATION,
            assetPath = "",
            notes = "Placeholder: depth TFLite o ONNX.",
        ),
        ModelDescriptor(
            id = "ocr_stub",
            displayName = "OCR (prossimo)",
            task = VisionTask.TEXT_OCR,
            assetPath = "",
            notes = "Placeholder: ML Kit Text Recognition o modello dedicato.",
        ),
    )

    fun byId(id: String): ModelDescriptor? = models.firstOrNull { it.id == id }
}
