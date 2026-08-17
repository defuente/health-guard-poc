package cl.defuente.healthguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text("Health Guard POC", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "Esta POC solicita acceso de lectura a la frecuencia cardíaca en Health Connect para mostrar mediciones y evaluar reglas configurables de alerta. Los datos se procesan localmente en esta versión y no se envían a un servidor. No es un dispositivo médico ni reemplaza evaluación profesional.",
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}
