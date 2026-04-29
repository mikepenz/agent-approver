package com.mikepenz.agentbelay.di

import kotlinx.coroutines.CoroutineScope

/**
 * Runtime values that must be supplied to the [AppGraph] at construction time.
 *
 * Provided into the graph via [AppGraph.Factory.create] so individual bindings
 * can depend on these values via constructor injection or `@Provides` parameters.
 */
data class AppEnvironment(
    val dataDir: String,
    val devMode: Boolean,
    val appScope: CoroutineScope,
)
