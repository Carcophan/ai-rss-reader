package de.carcophan.ai_rss.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.carcophan.ai_rss.data.repository.GeminiRepository
import kotlinx.coroutines.launch

@Composable
fun GeminiSettingsDialog(
    geminiRepository: GeminiRepository,
    onDismiss: () -> Unit,
    onSaveSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(geminiRepository.getApiKey()) }
    var selectedModel by remember { mutableStateOf(geminiRepository.getModel()) }
    var customModelInput by remember {
        mutableStateOf(
            if (selectedModel !in GeminiRepository.POPULAR_MODELS) selectedModel else ""
        )
    }
    var isCustomSelected by remember {
        mutableStateOf(selectedModel !in GeminiRepository.POPULAR_MODELS)
    }

    var isPasswordVisible by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    fun runTest() {
        val modelToTest = if (isCustomSelected) customModelInput.trim() else selectedModel
        isTestingConnection = true
        testResult = null
        scope.launch {
            val result = geminiRepository.testConnection(
                testApiKey = apiKey.trim(),
                testModel = modelToTest
            )
            isTestingConnection = false
            result.onSuccess {
                testResult = Pair(true, "Verbindung erfolgreich! Modell antwortet.")
            }.onFailure { error ->
                testResult = Pair(false, error.localizedMessage ?: "Verbindungstest fehlgeschlagen.")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gemini KI-Einstellungen",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Gib deinen Google Gemini API-Key ein, um Artikel mit KI zusammenfassen zu lassen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // API Key Field
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        testResult = null
                    },
                    label = { Text("Gemini API-Schlüssel") },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPasswordVisible) "Verbergen" else "Anzeigen"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Link to get API Key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://aistudio.google.com/app/apikey")
                            )
                            context.startActivity(intent)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Kostenlosen Key bei Google AI Studio erstellen",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Model Selection
                Text(
                    text = "Gemini Modell auswählen:",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                GeminiRepository.POPULAR_MODELS.forEach { modelName ->
                    val isChecked = !isCustomSelected && selectedModel == modelName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isCustomSelected = false
                                selectedModel = modelName
                                testResult = null
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isChecked,
                            onClick = {
                                isCustomSelected = false
                                selectedModel = modelName
                                testResult = null
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = modelName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            val hint = when (modelName) {
                                "gemini-3.8-flash" -> "Gewünschtes Modell für schnelle, präzise Analysen"
                                "gemini-2.0-flash" -> "Neuestes Standardmodell (schnell & kostengünstig)"
                                "gemini-1.5-flash" -> "Bewährtes stabiles Flash-Modell"
                                "gemini-2.5-flash" -> "Erweiterte Reasoning-Fähigkeiten"
                                else -> ""
                            }
                            if (hint.isNotBlank()) {
                                Text(
                                    text = hint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Custom Model Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isCustomSelected = true
                            testResult = null
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isCustomSelected,
                        onClick = {
                            isCustomSelected = true
                            testResult = null
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Benutzerdefiniertes Modell",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (isCustomSelected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customModelInput,
                        onValueChange = {
                            customModelInput = it
                            testResult = null
                        },
                        label = { Text("Modell-Name (z.B. gemini-3.8-flash)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Test Connection Button
                OutlinedButton(
                    onClick = { runTest() },
                    enabled = apiKey.isNotBlank() && !isTestingConnection,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Teste Verbindung...")
                    } else {
                        Text("Verbindung testen")
                    }
                }

                // Test Result Feedback
                testResult?.let { (success, message) ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (success) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (success) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (success) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalModel = if (isCustomSelected) {
                        customModelInput.trim().ifBlank { GeminiRepository.DEFAULT_MODEL }
                    } else {
                        selectedModel
                    }
                    geminiRepository.setApiKey(apiKey)
                    geminiRepository.setModel(finalModel)
                    onSaveSuccess()
                    onDismiss()
                }
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
