package com.sunfeld.smsgateway

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSendTab(
    viewModel: BluetoothHidViewModel,
    enabled: Boolean,
    onPickImage: () -> Unit,
    onSavePayload: () -> Unit,
    onLoadPayload: () -> Unit
) {
    val selectedType by viewModel.selectedPayloadType.collectAsStateWithLifecycle()
    val payloadName by viewModel.payloadNameFlow.collectAsStateWithLifecycle()
    val formFields by viewModel.payloadFormFields.collectAsStateWithLifecycle()
    val imageLabel by viewModel.selectedImageLabel.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.tab_data_send),
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Payload Name
            OutlinedTextField(
                value = payloadName,
                onValueChange = { viewModel.payloadNameFlow.value = it },
                label = { Text(stringResource(R.string.payload_name_hint)) },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Payload Type Dropdown
            PayloadTypeDropdown(
                selectedType = selectedType,
                enabled = enabled,
                onTypeSelected = { viewModel.selectedPayloadType.value = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic form fields based on selected type
            when (selectedType) {
                BluetoothPayload.PayloadType.VCARD -> VCardForm(formFields, enabled, viewModel)
                BluetoothPayload.PayloadType.VCARD_PHOTO -> VCardPhotoForm(formFields, enabled, viewModel, onPickImage)
                BluetoothPayload.PayloadType.VCALENDAR -> CalendarForm(formFields, enabled, viewModel)
                BluetoothPayload.PayloadType.VNOTE -> NoteForm(formFields, enabled, viewModel)
                BluetoothPayload.PayloadType.IMAGE -> ImageForm(imageLabel, enabled, onPickImage)
                BluetoothPayload.PayloadType.TEXT -> TextForm(formFields, enabled, viewModel)
                BluetoothPayload.PayloadType.PAIRING_NAME -> {} // Shouldn't appear, but safe
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Save / Load Payload buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onSavePayload,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.save_payload))
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onLoadPayload,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.load_payload))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayloadTypeDropdown(
    selectedType: BluetoothPayload.PayloadType,
    enabled: Boolean,
    onTypeSelected: (BluetoothPayload.PayloadType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val types = listOf(
        BluetoothPayload.PayloadType.VCARD,
        BluetoothPayload.PayloadType.VCARD_PHOTO,
        BluetoothPayload.PayloadType.VCALENDAR,
        BluetoothPayload.PayloadType.VNOTE,
        BluetoothPayload.PayloadType.IMAGE,
        BluetoothPayload.PayloadType.TEXT
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = payloadTypeLabel(selectedType),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.payload_type_hint)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            types.forEach { type ->
                DropdownMenuItem(
                    text = { Text(payloadTypeLabel(type)) },
                    onClick = {
                        onTypeSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun payloadTypeLabel(type: BluetoothPayload.PayloadType): String {
    return when (type) {
        BluetoothPayload.PayloadType.VCARD -> stringResource(R.string.payload_type_contact)
        BluetoothPayload.PayloadType.VCARD_PHOTO -> stringResource(R.string.payload_type_contact_photo)
        BluetoothPayload.PayloadType.VCALENDAR -> stringResource(R.string.payload_type_calendar)
        BluetoothPayload.PayloadType.VNOTE -> stringResource(R.string.payload_type_note)
        BluetoothPayload.PayloadType.IMAGE -> stringResource(R.string.payload_type_image)
        BluetoothPayload.PayloadType.TEXT -> stringResource(R.string.payload_type_text)
        BluetoothPayload.PayloadType.PAIRING_NAME -> "Pairing Name"
    }
}

// ---- Type-specific forms ----

@Composable
private fun VCardForm(
    fields: Map<String, String>,
    enabled: Boolean,
    viewModel: BluetoothHidViewModel
) {
    Column {
        FormField(R.string.field_full_name, "fullName", fields, enabled, viewModel)
        FormField(R.string.field_phone, "phone", fields, enabled, viewModel)
        FormField(R.string.field_email, "email", fields, enabled, viewModel)
        FormField(R.string.field_organization, "organization", fields, enabled, viewModel)
        FormField(R.string.field_title, "title", fields, enabled, viewModel)
        FormField(R.string.field_url, "url", fields, enabled, viewModel)
        FormField(R.string.field_note, "note", fields, enabled, viewModel)
    }
}

@Composable
private fun VCardPhotoForm(
    fields: Map<String, String>,
    enabled: Boolean,
    viewModel: BluetoothHidViewModel,
    onPickImage: () -> Unit
) {
    Column {
        FormField(R.string.field_full_name, "fullName", fields, enabled, viewModel)
        FormField(R.string.field_phone, "phone", fields, enabled, viewModel)
        FormField(R.string.field_email, "email", fields, enabled, viewModel)
        FormField(R.string.field_organization, "organization", fields, enabled, viewModel)

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onPickImage,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.btn_pick_photo))
        }

        val photoStatus = if (fields.containsKey("photoBase64") && fields["photoBase64"]?.isNotEmpty() == true) {
            stringResource(R.string.image_selected, "photo")
        } else {
            stringResource(R.string.no_image_selected)
        }
        Text(
            text = photoStatus,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun CalendarForm(
    fields: Map<String, String>,
    enabled: Boolean,
    viewModel: BluetoothHidViewModel
) {
    Column {
        FormField(R.string.field_summary, "summary", fields, enabled, viewModel)
        FormField(R.string.field_description, "description", fields, enabled, viewModel)
        FormField(R.string.field_location, "location", fields, enabled, viewModel)
    }
}

@Composable
private fun NoteForm(
    fields: Map<String, String>,
    enabled: Boolean,
    viewModel: BluetoothHidViewModel
) {
    FormField(R.string.field_body, "body", fields, enabled, viewModel, minLines = 3)
}

@Composable
private fun ImageForm(
    imageLabel: String,
    enabled: Boolean,
    onPickImage: () -> Unit
) {
    Column {
        OutlinedButton(
            onClick = onPickImage,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.btn_pick_image))
        }
        Text(
            text = imageLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun TextForm(
    fields: Map<String, String>,
    enabled: Boolean,
    viewModel: BluetoothHidViewModel
) {
    FormField(R.string.field_text_content, "text", fields, enabled, viewModel, minLines = 3)
}

@Composable
private fun FormField(
    labelRes: Int,
    key: String,
    fields: Map<String, String>,
    enabled: Boolean,
    viewModel: BluetoothHidViewModel,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = fields[key] ?: "",
        onValueChange = { viewModel.updateFormField(key, it) },
        label = { Text(stringResource(labelRes)) },
        enabled = enabled,
        singleLine = minLines == 1,
        minLines = minLines,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}
