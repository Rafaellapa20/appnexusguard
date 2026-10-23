package com.nexusguard.android

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexusguard.android.data.local.NexusGuardDatabase
import com.nexusguard.android.data.repository.CleanCategoryType
import com.nexusguard.android.data.repository.CleanerRepository
import com.nexusguard.android.domain.FileClassifier
import com.nexusguard.android.domain.ScannedFileCandidate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CleanFlowInstrumentedTest {

    private lateinit var database: NexusGuardDatabase
    private lateinit var cleanerRepository: CleanerRepository
    private lateinit var tempFakeDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, NexusGuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cleanerRepository = CleanerRepository(context, database.dao())
        tempFakeDir = File(context.cacheDir, "fake_mediastore_${System.currentTimeMillis()}").apply {
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        tempFakeDir.deleteRecursively()
        database.close()
    }

    @Test
    fun completeCleanFlow_analysisToSelectionToNexusTrashAndHistory() = runBlocking {
        // 1. Prepara ficheiro falso e pasta vazia (simulando candidatos de limpeza)
        val junkFile = File(tempFakeDir, "residual.tmp").apply {
            writeBytes(ByteArray(64 * 1024) { 0x42 })
        }
        val emptySubFolder = File(tempFakeDir, "pasta_vazia").apply { mkdirs() }

        // 2. Valida classificação de domínio (análise)
        assertTrue(FileClassifier.isFilesystemJunkFile(junkFile.name, "Download/", junkFile.length()))
        val detectedEmpty = FileClassifier.findEmptyFoldersBottomUp(tempFakeDir)
        assertTrue(detectedEmpty.contains(emptySubFolder.absolutePath))

        // 3. Seleção (segura por omissão em FILESYSTEM_JUNK e EMPTY_FOLDERS)
        assertTrue(CleanCategoryType.FILESYSTEM_JUNK.isSafePreselected)
        assertTrue(CleanCategoryType.EMPTY_FOLDERS.isSafePreselected)
        assertFalse(CleanCategoryType.SIMILAR_PHOTOS.isSafePreselected)

        val candidate = ScannedFileCandidate(
            id = 999L,
            contentUri = Uri.fromFile(junkFile).toString(),
            displayName = junkFile.name,
            relativePath = "Download/",
            sizeBytes = junkFile.length(),
            dateModifiedSeconds = System.currentTimeMillis() / 1000L,
            mimeType = "application/octet-stream",
            isPreselectedByDefault = true
        )

        // 4. Pedido de Lixo (Lixo Nexus para file:// + remoção da pasta vazia) e registo no Histórico
        val completion = cleanerRepository.deleteEmptyFoldersAndRecord(
            emptyFolders = listOf(emptySubFolder.absolutePath),
            trashedFiles = listOf(candidate)
        )

        assertFalse("O ficheiro original deve ter sido movido para o Lixo Nexus", junkFile.exists())
        assertFalse("A pasta vazia deve ter sido removida", emptySubFolder.exists())
        assertTrue("Deve permitir restauro durante 30 dias", completion.canRestore)
        assertEquals(1, completion.removedFilesCount)
        assertEquals(1, completion.removedEmptyFoldersCount)

        // 5. Verifica registo no Histórico (Room) e restauro a partir do Lixo Nexus
        val historyList = database.dao().getAllHistoryOnce()
        assertEquals(1, historyList.size)
        val entry = historyList.first()
        assertEquals("CLEAN", entry.actionType)
        assertTrue(entry.canRestore)
        assertFalse(entry.isRestored)

        cleanerRepository.markHistoryEntryRestored(entry.id)
        assertTrue("O ficheiro deve voltar ao caminho original após restaurar", junkFile.exists())
        val updatedHistory = database.dao().getAllHistoryOnce().first()
        assertTrue(updatedHistory.isRestored)
    }
}
