package com.culinary.probe.feature

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.culinary.probe.domain.Greeter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FeatureViewModel @Inject constructor(
    private val greeter: Greeter,
) : ViewModel() {
    val message: String = greeter.greet()
}

@Composable
fun FeatureRoute() {
    val viewModel: FeatureViewModel = hiltViewModel()
    Text(text = viewModel.message)
}
