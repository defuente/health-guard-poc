package cl.defuente.healthguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.PermissionController
import cl.defuente.healthguard.data.HealthConnectRepository
import cl.defuente.healthguard.ui.HealthGuardApp

class MainActivity : ComponentActivity() {
    private lateinit var repository: HealthConnectRepository
    private var permissionRefreshVersion by mutableIntStateOf(0)

    private val permissionsLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) {
        permissionRefreshVersion++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = HealthConnectRepository(applicationContext)

        setContent {
            MaterialTheme {
                HealthGuardApp(
                    repository = repository,
                    permissionRefreshVersion = permissionRefreshVersion,
                    onRequestPermissions = {
                        permissionsLauncher.launch(repository.permissionsToRequest())
                    }
                )
            }
        }
    }
}
