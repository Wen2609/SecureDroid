package com.armorlab.securedroid.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.data.ScanRecordEntity
import com.armorlab.securedroid.scan.ScannerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
            val packages = context.packageManager.getInstalledPackages(0)
            val results = mutableListOf<ScannerEngine.ScanResult>()
            packages.forEachIndexed { index, info ->
                results.add(ScannerEngine.scanPackage(context, info.packageName))
                _state.postValue(ScanUiState.Scanning(index + 1, packages.size))
            }
            val dao = AppDatabase.get(context).scanRecordDao()
            val now = System.currentTimeMillis()
            results.forEach { r ->
                dao.insert(
                    ScanRecordEntity(
                        packageName = r.packageName,
                        appName = r.appName,
                        sha256 = r.sha256,
                        threatName = r.threat?.name,
                        riskScore = r.permissionRiskScore,
                        scannedAt = now
                    )
                )
            }
            _state.postValue(ScanUiState.Done(results))
        }
    }
}
