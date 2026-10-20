// The on-device model, delivered by Google Play as an AI pack (Play for On-device AI).
//
// fast-follow: Play downloads it right after the app installs, and unpacks it to internal storage
// where it has a real file path. LiteRT-LM only opens a model by path, so an install-time pack
// (read through AssetManager) would have to be copied out and stored twice.
//
// The model file is never committed: run scripts/stage_model.sh before building a bundle.
plugins {
    alias(libs.plugins.androidAiPack)
}

aiPack {
    packName = "gemma_pack"
    dynamicDelivery {
        deliveryType = "fast-follow"
    }
}
