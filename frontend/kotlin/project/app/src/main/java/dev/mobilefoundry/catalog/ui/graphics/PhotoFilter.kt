package dev.mobilefoundry.catalog.ui.graphics

import dev.mobilefoundry.graphics.ImageAdjustments

enum class PhotoFilter(val label: String, val adjustments: ImageAdjustments) {
    NATURAL("Natural", ImageAdjustments.make()),
    MONO("Mono", ImageAdjustments.make(saturation = 0f, vignette = .15f)),
    VIVID("Vivid", ImageAdjustments.make(exposure = .15f, saturation = 1.45f, vignette = .2f)),
    SOFT("Soft", ImageAdjustments.make(exposure = .25f, saturation = .8f, vignette = .15f)),
}
