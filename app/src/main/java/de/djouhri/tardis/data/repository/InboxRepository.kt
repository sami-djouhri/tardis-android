package de.djouhri.tardis.data.repository

import de.djouhri.tardis.data.api.InboxApi
import de.djouhri.tardis.data.demo.DemoData
import de.djouhri.tardis.data.demo.DemoModeManager
import de.djouhri.tardis.data.model.tardis.InboxActionRequest
import de.djouhri.tardis.data.model.tardis.InboxList
import de.djouhri.tardis.data.model.tardis.InboxNoteRequest
import de.djouhri.tardis.data.model.tardis.InboxNoteResponse
import de.djouhri.tardis.data.model.tardis.StreamItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InboxRepository @Inject constructor(
    private val api: InboxApi,
    private val demo: DemoModeManager,
) {
    suspend fun list(limit: Int = 30, includeDone: Boolean = false): Result<InboxList> {
        if (demo.isActive) return Result.success(DemoData.inbox)
        return runCatching { api.list(limit, includeDone) }
    }

    suspend fun stream(
        limit: Int = 120,
        typen: Set<String> = emptySet(),
        quellen: Set<String> = emptySet(),
    ): Result<List<StreamItem>> {
        if (demo.isActive) {
            return Result.success(
                DemoData.stream.filter { (typen.isEmpty() || it.kind in typen) && (quellen.isEmpty() || it.source in quellen) },
            )
        }
        return runCatching {
            api.stream(
                limit,
                kinds = typen.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(","),
                sources = quellen.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(","),
            )
        }
    }

    suspend fun createNote(
        title: String,
        content: String = "",
        tags: List<String> = emptyList(),
    ): Result<InboxNoteResponse> {
        if (demo.isActive) {
            return Result.success(
                InboxNoteResponse(path = "demo/$title.md", slug = "demo", createdAt = "2026-01-01T00:00:00Z"),
            )
        }
        return runCatching { api.createNote(InboxNoteRequest(title = title, content = content, tags = tags)) }
    }

    suspend fun action(
        externalId: String,
        action: String,
        snoozeMinutes: Int? = null,
        note: String? = null,
    ): Result<Unit> {
        if (demo.isActive) return Result.success(Unit)
        return runCatching {
            api.action(externalId, InboxActionRequest(action = action, snoozeMinutes = snoozeMinutes, note = note)).close()
        }
    }
}
