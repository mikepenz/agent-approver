package com.mikepenz.agentbelay.risk

/**
 * Initialization states for the OpenAI API (llama.cpp) backend.
 */
enum class OpenaiApiInitState {
    /** Initial state before any connection attempt. */
    IDLE,

    /** Checking connectivity and fetching available models. */
    CONNECTING,

    /** Connected successfully; models are available for selection. */
    READY,

    /** Connection failed or server returned an error. */
    ERROR,
}
