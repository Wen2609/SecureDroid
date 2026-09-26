package com.armorlab.securedroid.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.data.ScanRecordEntity
import com.armorlab.securedroid.scan.ScannerEngine
import com.armorlab.securedroid.vscan.ParallelScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

sealed class ScanUiState {
    data object Idle : ScanUiState()
    data class Scanning(val progress: Int, val total: Int) : ScanUiState()
    data class Done(val results: List<ScannerEngine.ScanResult>) : ScanUiState()
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableLiveData<ScanUiState>(ScanUiState.Idle)
    val state: LiveData<ScanUiState> = _state

    fun startScan() {
        if (_state.value is ScanUiState.Scanning) return
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val collected = ConcurrentLinkedQueue<ScannerEngine.ScanResult>()
            // 并行加速:4 线程并发多引擎扫描,实时进度
            ParallelScanner.scanAll(context, 4,
                { done, total -> _state.postValue(ScanUiState.Scanning(done, total)) },
                { r -> collected.add(r) }
            )
            val results = collected.sortedWith(
                compareByDescending<ScannerEngine.ScanResult> { it.isMalicious }
                    .thenByDescending { it.permissionRiskScore }
            )
            val dao = AppDatabase.get(context).scanRecordDao()
            val now = System.currentTimeMillis()
            dao.insertAll(results.map { r ->
                ScanRecordEntity(
                    packageName = r.packageName,
                    appName = r.appName,
                    sha256 = r.sha256,
                    threatName = r.threat?.name,
                    riskScore = r.permissionRiskScore,
                    scannedAt = now
                )
            })
            dao.trim()
            _state.postValue(ScanUiState.Done(results))
        }
    }
}
