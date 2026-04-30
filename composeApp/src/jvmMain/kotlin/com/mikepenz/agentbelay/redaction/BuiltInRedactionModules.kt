package com.mikepenz.agentbelay.redaction

import com.mikepenz.agentbelay.redaction.modules.ApiKeyRedactionModule
import com.mikepenz.agentbelay.redaction.modules.EnvVarRedactionModule
import com.mikepenz.agentbelay.redaction.modules.JwtRedactionModule
import com.mikepenz.agentbelay.redaction.modules.PrivateKeyRedactionModule

/** Default module set wired into [com.mikepenz.agentbelay.di.AppProviders]. */
val builtInRedactionModules: List<RedactionModule> = listOf(
    ApiKeyRedactionModule,
    PrivateKeyRedactionModule,
    JwtRedactionModule,
    EnvVarRedactionModule,
)
