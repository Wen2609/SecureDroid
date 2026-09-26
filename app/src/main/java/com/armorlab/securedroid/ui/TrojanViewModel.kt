package com.armorlab.securedroid.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.armorlab.securedroid.R
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.root.LockerDetector
import com.armorlab.securedroid.root.ModuleScanner
import com.armorlab.securedroid.trojan.ClamAvSignatures
import com.armorlab.securedroid.trojan.RootkitDetector
import com.armorlab.securedroid.trojan.TrojanScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class TrojanUiState {
    data object Idle : TrojanUiState()
    data class Scanning(val progress: Int, val total: Int, val label: String) : TrojanUiState()
    data class Done(val items: List<TrojanAdapter.UiItem>, val summary: String) : TrojanUiState()
}

class TrojanViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableLiveData<TrojanUiState>(TrojanUiState.Idle)
    val state: LiveData<TrojanUiState> = _state

    fun startScan() {
        if (_state.value is TrojanUiState.Scanning) return
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            ClamAvSignatures.ensureLoaded(context)
            val pkgs = context.packageManager.getInstalledPackages(0)
            val items = mutableListOf<TrojanAdapter.UiItem>()
            var infected = 0
            pkgs.forEachIndexed { index, info ->
                val report = TrojanScanner.scanPackage(context, info.packageName)
                if (report.isInfected) {
                    infected++
                    items.add(report.toUiItem())
                }
                _state.postValue(TrojanUiState.Scanning(index + 1, pkgs.size,
                    context.getString(R.string.trojan_scanning)))
            }
            _state.postValue(TrojanUiState.Done(
                items,
                context.getString(R.string.trojan_done, pkgs.size, infected)
            ))
        }
    }

    fun startRootkit() {
        if (_state.value is TrojanUiState.Scanning) return
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            _state.postValue(TrojanUiState.Scanning(0, 0,
                context.getString(R.string.rootkit_scanning)))
            val findings = RootkitDetector.detect(context)
            val items = findings.map {
                TrojanAdapter.UiItem(
                    title = it.name,
                    sub = it.detail,
                    detail = "",
                    level = it.level,
                    suggestion = it.suggestion,
                    uninstallPkg = null
                )
            }
            val summary = if (findings.isEmpty())
                context.getString(R.string.rootkit_clean)
            else
                context.getString(R.string.rootkit_done, findings.size)
            _state.postValue(TrojanUiState.Done(items, summary))
        }
    }

    fun startLockerScan() {
        if (_state.value is TrojanUiState.Scanning) return
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            _state.postValue(TrojanUiState.Scanning(0, 0,
                context.getString(R.string.locker_scanning)))
            val findings = LockerDetector.scan(context)
            val items = findings.map {
                TrojanAdapter.UiItem(
                    title = it.title,
                    sub = it.sub,
                    detail = it.detail,
                    level = it.level,
                    suggestion = it.suggestion,
                    uninstallPkg = null,
                    fixCommand = it.fixCommand,
                    fixLabel = it.fixLabel
                )
            }
            val summary = if (findings.isEmpty())
                context.getString(R.string.locker_clean)
            else
                context.getString(R.string.locker_done, findings.size)
            _state.postValue(TrojanUiState.Done(items, summary))
        }
    }

    fun startModuleScan() {
        if (_state.value is TrojanUiState.Scanning) return
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            _state.postValue(TrojanUiState.Scanning(0, 0,
                context.getString(R.string.modules_scanning)))
            val result = ModuleScanner.scan(context)
            val items = result.findings.map {
                TrojanAdapter.UiItem(
                    title = it.title,
                    sub = it.sub,
                    detail = it.detail,
                    level = it.level,
                    suggestion = it.suggestion,
                    uninstallPkg = null,
                    fixCommand = it.fixCommand,
                    fixLabel = it.fixLabel
                )
            }
            val summary = when {
                items.isNotEmpty() -> context.getString(R.string.modules_done, items.size)
                result.scannedRoots == 0 -> context.getString(R.string.modules_no_access)
                else -> context.getString(R.string.modules_clean)
            }
            _state.postValue(TrojanUiState.Done(items, summary))
        }
    }

    private fun TrojanScanner.Report.toUiItem(): TrojanAdapter.UiItem {
        val worst = detections.maxByOrNull { it.level.ordinal }?.level
        val detail = detections.joinToString("\n") {
            "[" + it.engine + "] " + it.name + " — " + it.detail
        }
        return TrojanAdapter.UiItem(
            title = appName,
            sub = packageName,
            detail = detail,
            level = worst,
            suggestion = null,
            uninstallPkg = packageName
        )
    }
}
