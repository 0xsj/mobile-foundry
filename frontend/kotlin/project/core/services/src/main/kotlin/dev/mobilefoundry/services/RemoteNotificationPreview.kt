package dev.mobilefoundry.services

/** Generic navigation hint; no session/account authority or server-rendered content. */
@ConsistentCopyVisibility
data class RemoteNotificationPreview private constructor(val id: String) {
    val payload get() = mapOf("version" to "1", "kind" to "push_preview", "preview_id" to id)
    companion object {
        fun make(id: String): RemoteNotificationPreview? = if (RemoteServiceAdmission.uuid(id)) RemoteNotificationPreview(id) else null
        fun decode(payload: Map<String, String>): RemoteNotificationPreview? =
            if (payload.keys == setOf("version", "kind", "preview_id") && payload["version"] == "1" && payload["kind"] == "push_preview") payload["preview_id"]?.let(::make) else null
    }
}

sealed interface NotificationOpen {
    val previewId: String
    val isRemote: Boolean
    val id get() = (if (isRemote) "push_preview:" else "local_preview:") + previewId
    data class Local(val preview: LocalNotificationPreview) : NotificationOpen {
        override val previewId get() = preview.id
        override val isRemote = false
    }
    data class Remote(val preview: RemoteNotificationPreview) : NotificationOpen {
        override val previewId get() = preview.id
        override val isRemote = true
    }
}
