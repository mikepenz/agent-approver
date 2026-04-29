package com.mikepenz.agentbelay.di

/**
 * Top-level DI scope marker for the application graph.
 *
 * Used as the scope argument to [dev.zacsweers.metro.DependencyGraph] and
 * [dev.zacsweers.metro.SingleIn] so that all app-scoped bindings live for the
 * lifetime of the [AppGraph].
 */
abstract class AppScope private constructor()
