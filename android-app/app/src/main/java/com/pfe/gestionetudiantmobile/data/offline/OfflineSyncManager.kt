package com.pfe.gestionetudiantmobile.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.pfe.gestionetudiantmobile.data.repository.TeacherRepository
import com.pfe.gestionetudiantmobile.util.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object OfflineSyncManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    @Volatile
    private var registered = false

    fun register(context: Context) {
        val appContext = context.applicationContext
        if (!registered) {
            registered = true
            val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            connectivityManager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch { syncNow(appContext) }
                }
            })
        }
        scope.launch { syncNow(appContext) }
    }

    suspend fun syncNow(context: Context): OfflineSyncResult {
        val appContext = context.applicationContext
        if (!ConnectivityMonitor.isOnline(appContext)) {
            return OfflineSyncResult(synced = 0, remaining = OfflineStore(appContext).pendingCount())
        }

        return syncMutex.withLock {
            val store = OfflineStore(appContext)
            val repository = TeacherRepository()
            var synced = 0

            for (action in store.pendingActions()) {
                val result = when (action.type) {
                    PendingOfflineAction.TYPE_UPSERT_NOTE -> repository.upsertNote(
                        studentId = action.studentId ?: return@withLock OfflineSyncResult(synced, store.pendingCount()),
                        moduleId = action.moduleId ?: return@withLock OfflineSyncResult(synced, store.pendingCount()),
                        semestre = action.semestre.orEmpty(),
                        anneeAcademique = action.anneeAcademique.orEmpty(),
                        noteCc = action.noteCc,
                        noteExamen = action.noteExamen
                    )

                    PendingOfflineAction.TYPE_CREATE_ABSENCE -> repository.createAbsence(
                        studentId = action.studentId ?: return@withLock OfflineSyncResult(synced, store.pendingCount()),
                        moduleId = action.moduleId ?: return@withLock OfflineSyncResult(synced, store.pendingCount()),
                        dateAbsence = action.dateAbsence,
                        nombreHeures = action.nombreHeures
                    )

                    PendingOfflineAction.TYPE_DELETE_ABSENCE -> repository.deleteAbsence(
                        action.absenceId ?: return@withLock OfflineSyncResult(synced, store.pendingCount())
                    )

                    else -> ApiResult.Error("Action offline inconnue.")
                }

                when (result) {
                    is ApiResult.Success -> {
                        store.removeAction(action.id)
                        synced++
                    }
                    is ApiResult.Error -> return@withLock OfflineSyncResult(synced, store.pendingCount())
                }
            }

            OfflineSyncResult(synced = synced, remaining = store.pendingCount())
        }
    }

    fun pendingCount(context: Context): Int = OfflineStore(context.applicationContext).pendingCount()
}

data class OfflineSyncResult(
    val synced: Int,
    val remaining: Int
)
