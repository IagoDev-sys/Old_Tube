package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun OpenVideoUrlDialog(
    onDismiss: () -> Unit,
    onOpenVideo: (String) -> Boolean
) {
    var input by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abrir link ou ID do YouTube") },
        text = {
            Column {
                Text(
                    text = "Cole uma URL (youtube.com/watch?v=..., youtu.be/...) ou o ID de 11 caracteres para rodar direto no modo otimizado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        errorText = null
                    },
                    label = { Text("URL ou ID do vídeo") },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { msg -> { Text(msg) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_url_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ok = onOpenVideo(input)
                    if (ok) {
                        onDismiss()
                    } else {
                        errorText = "Link ou ID de vídeo inválido."
                    }
                },
                modifier = Modifier.testTag("confirm_open_url_button")
            ) {
                Text("Reproduzir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun AddChannelDialog(
    onDismiss: () -> Unit,
    onAddChannel: (String, (Boolean, String) -> Unit) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Adicionar canal ao feed RSS") },
        text = {
            Column {
                Text(
                    text = "Digite o @handle (ex: @ManualdoMundo), o link do canal ou o ID UC... O app lê o feed Atom XML oficial sem consumir memória de WebView.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        feedback = null
                    },
                    enabled = !isLoading,
                    label = { Text("@handle, URL ou ID UC...") },
                    singleLine = true,
                    supportingText = feedback?.let { msg -> { Text(msg) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_channel_input")
                )
                if (isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isLoading && input.isNotBlank(),
                onClick = {
                    isLoading = true
                    onAddChannel(input) { success, message ->
                        isLoading = false
                        if (success) {
                            onDismiss()
                        } else {
                            feedback = message
                        }
                    }
                },
                modifier = Modifier.testTag("confirm_add_channel_button")
            ) {
                Text("Seguir canal")
            }
        },
        dismissButton = {
            TextButton(enabled = !isLoading, onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
