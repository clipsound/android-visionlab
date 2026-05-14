package com.visionlab.app.camera;

import android.util.Size;
import androidx.annotation.NonNull;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;

/** Constructs {@link ResolutionStrategy} from Java because its ctor is not callable from Kotlin here. */
public final class ImageAnalysisResolutionSelectors {
    private ImageAnalysisResolutionSelectors() {}

    @NonNull
    public static ResolutionSelector analysis640x480() {
        return new ResolutionSelector.Builder()
                .setResolutionStrategy(
                        new ResolutionStrategy(
                                new Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build();
    }
}
