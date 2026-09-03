package org.ok1cdj.kradar

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import org.ok1cdj.kradar.ui.KRadarTheme
import org.ok1cdj.kradar.ui.MeteoRadarScreen
import org.ok1cdj.kradar.ui.RadarViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KRadarTheme {
                App()
            }
        }
    }
}

@Composable
private fun App() {
    val vm: RadarViewModel = viewModel()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.onPermissionResult(granted) }

    // On first composition, ask the ViewModel to resolve location. If the
    // permission is missing it flips to the permission-prompt state; the button
    // there launches the request, and onPermissionResult retries locate().
    LaunchedEffect(Unit) {
        vm.locate()
    }

    MeteoRadarScreen(
        vm = vm,
        onRequestLocationPermission = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
    )
}
