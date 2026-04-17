package com.mikepenz.agentbuddy.di

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

/**
 * App-scoped [androidx.lifecycle.ViewModelProvider.Factory] backed by Metro.
 *
 * Reads the ViewModel multibinding maps populated by `@ContributesIntoMap` +
 * `@ViewModelKey` (and the assisted-factory variants) and uses them to create
 * ViewModel instances. Wired into Compose at the root via
 * [dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory] in `Main.kt`.
 */
@ContributesBinding(AppScope::class)
@Inject
class InjectedViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, Provider<ViewModel>>,
    override val assistedFactoryProviders:
        Map<KClass<out ViewModel>, Provider<ViewModelAssistedFactory>>,
    override val manualAssistedFactoryProviders:
        Map<KClass<out ManualViewModelAssistedFactory>, Provider<ManualViewModelAssistedFactory>>,
) : MetroViewModelFactory()
