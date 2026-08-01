package app.linkshare.model

enum class FileCategory { Photos, Videos, Music, Apps, Documents, Folders, All }

data class SelectableFile(
    val id: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long = 0L,
    val isDirectory: Boolean = false,
    val category: FileCategory = FileCategory.All,
    val isAvailable: Boolean = true
)

data class SelectionState(
    val selected: List<SelectableFile> = emptyList(),
    val activeCategory: FileCategory = FileCategory.All,
    val query: String = ""
) {
    val totalBytes: Long get() = selected.sumOf { it.sizeBytes }
}

data class TransferRecipient(
    val device: PeerDevice,
    val trusted: Boolean = false,
    val approved: Boolean = false
)

data class TransferBatch(
    val id: String,
    val files: List<SelectableFile>,
    val recipients: List<TransferRecipient>,
    val direction: TransferDirection = TransferDirection.Send
)

enum class TransferDirection { Send, Receive }
