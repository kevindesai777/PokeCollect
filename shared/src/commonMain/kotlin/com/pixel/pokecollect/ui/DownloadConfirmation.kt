package com.pixel.pokecollect.ui

import androidx.compose.runtime.Composable

/**
 * Opens the store's own confirmation for the model download: over mobile data, or for an app that
 * wasn't installed from the store. A no-op where the platform delivers nothing (iOS).
 */
@Composable
expect fun rememberDownloadConfirmation(): () -> Unit
