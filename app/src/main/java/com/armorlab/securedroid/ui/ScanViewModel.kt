package com.armorlab.securedroid.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.data.ScanRecordEntity
import com.armorlab.securedroid.trojan.TrojanScanner
import com.armorlab.securedroid.vscan.ParallelScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class ScanUiState {
    data object Idle : ScanUiState()
    data class Scanning(val progress: Int, val total: Int) : ScanUiState()
    data class Done(val results: List<TrojanScanner.Report>) : ScanUiState()
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableLiveData<ScanUiState>(ScanUiState.Idle)
    val state: LiveData<ScanUiState> = _state

    fun startScan() {
        if (_state.value is ScanUiState.Scanning) return
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            // 并行加速:4 线程并发多引擎扫描,实时进度
            val outcome = ParallelScanner.scanReports(context, 4,
                { done, total -> _state.postValue(ScanUiState.Scanning(done, total)) }
            )
            val results = outcome.reports.sortedWith(
                compareByDescending<TrojanScanner.Report> { it.isInfected }
                    .thenByDescending { it.worstLevel?.ordinal ?: -1 }
            )
            val dao = AppDatabase.get(context).scanRecordDao()
            val now = System.currentTimeMillis()
            dao.insertAll(results.map { r ->
                ScanRecordEntity(
                    packageName = r.packageName,
                    appName = r.appName,
                    sha256 = r.sha256,
                    threatName = r.detections.maxByOrNull { it.level.ordinal }?.name,
                    riskScore = r.riskScore,
                    scannedAt = now
                )
            })
            dao.trim()
            _state.postValue(ScanUiState.Done(results))
        }
    }
}
