package com.sabasa.nottel.service

import kotlinx.coroutines.flow.MutableStateFlow

object ForwardingState {
    // Single source of truth for pause/resume — observed by service, notification, and UI
    val isActive = MutableStateFlow(true)
}