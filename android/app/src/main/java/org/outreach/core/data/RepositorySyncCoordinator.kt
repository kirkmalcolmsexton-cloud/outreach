package org.outreach.core.data

import org.outreach.core.model.AppConfig

class RepositorySyncCoordinator(
    private val repositoryProvider: () -> OutreachRepository?
) {
    suspend fun sync(configOverride: AppConfig? = null): Result<Unit> {
        val repository = repositoryProvider()
            ?: return Result.failure(IllegalStateException("Repository not initialized"))
        return runCatching {
            if (configOverride != null) {
                repository.setConfig(configOverride)
            }
            repository.flushPendingSync()
            repository.syncFromSheet()
        }
    }
}
