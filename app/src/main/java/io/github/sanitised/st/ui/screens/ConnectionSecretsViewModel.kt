package io.github.sanitised.st.ui.screens

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.sanitised.st.data.repository.ConnectionRepository
import javax.inject.Inject

@HiltViewModel
class ConnectionSecretsViewModel @Inject constructor(
    val repository: ConnectionRepository,
) : ViewModel()
