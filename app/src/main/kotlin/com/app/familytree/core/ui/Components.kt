package com.app.familytree.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.app.familytree.R
import com.app.familytree.core.model.*
import com.app.familytree.core.theme.*

@Composable fun Avatar(person: Person, modifier: Modifier = Modifier) {
    val portraits = listOf(R.drawable.portrait_0, R.drawable.portrait_1, R.drawable.portrait_2, R.drawable.portrait_3)
    Box(modifier.clip(CircleShape).background(Color(0xFFE6D9CA)), contentAlignment = Alignment.Center) {
        Text(person.name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }, color = Espresso)
        if (person.avatarUrl?.startsWith("asset:") == true) {
            androidx.compose.foundation.Image(painterResource(portraits[person.id.hashCode().and(3)]), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (!person.avatarUrl.isNullOrBlank()) {
            AsyncImage(model = person.avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}
@Composable fun PageHeading(title: String, eyebrow: String? = null, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (eyebrow != null) Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = SoftBrown)
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = WarmGrey)
    }
}
@Composable fun ErrorMessage(error: String?, retry: (() -> Unit)? = null) {
    if (error != null) Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(error, color = MaterialTheme.colorScheme.error)
        if (retry != null) TextButton(onClick = retry) { Text("Retry load") }
    }
}
@Composable fun RelationDialog(form: RelationForm, onEdit: (String, String, String) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(24.dp),
        title = { Text("Add ${form.type.name.lowercase()}", style = MaterialTheme.typography.headlineMedium) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(form.name, { onEdit(it, form.avatarUrl, form.year) }, label = { Text("Name") }, singleLine = true, enabled = !form.saving)
            OutlinedTextField(form.avatarUrl, { onEdit(form.name, it, form.year) }, label = { Text("Avatar URL (optional)") }, singleLine = true, enabled = !form.saving)
            if (form.type == RelationshipType.PARTNER) OutlinedTextField(form.year, { onEdit(form.name, form.avatarUrl, it) }, label = { Text("Marriage year (optional)") }, singleLine = true, enabled = !form.saving)
            form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(onClick = onSave, enabled = !form.saving) { Text(if (form.saving) "Saving…" else "Add relative") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !form.saving) { Text("Cancel") } })
}
