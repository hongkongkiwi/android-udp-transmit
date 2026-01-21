package com.udptrigger.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.udptrigger.data.SettingsDataStore

class TriggerViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TriggerViewModel::class.java)) {
            val dataStore = SettingsDataStore(context)
            @Suppress("UNCHECKED_CAST")
            return TriggerViewModel(context, dataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
