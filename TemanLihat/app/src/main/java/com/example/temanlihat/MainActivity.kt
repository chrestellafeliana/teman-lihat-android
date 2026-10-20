package com.example.temanlihat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.temanlihat.ui.theme.TemanLihatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemanLihatTheme { TemanLihatHome() }
        }
    }
}

private enum class Feature(val title: Int, val subtitle: Int, val description: Int) {
    Document(R.string.document_title, R.string.document_subtitle, R.string.document_description),
    Expiry(R.string.expiry_title, R.string.expiry_subtitle, R.string.expiry_description),
    ColorDetection(R.string.color_title, R.string.color_subtitle, R.string.color_description)
}

@Composable
fun TemanLihatHome() {
    var selectedFeature by rememberSaveable { mutableStateOf<String?>(null) }

    if (selectedFeature == Feature.Document.name) {
        LiveDetectionScreen(DetectionMode.Document, onBack = { selectedFeature = null })
        return
    }

    if (selectedFeature == Feature.Expiry.name) {
        LiveDetectionScreen(DetectionMode.Expiry, onBack = { selectedFeature = null })
        return
    }

    if (selectedFeature == Feature.ColorDetection.name) {
        LiveDetectionScreen(DetectionMode.Color, onBack = { selectedFeature = null })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.fillMaxWidth().semantics { heading() },
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 34.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.choose_feature),
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 26.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 18.sp,
            lineHeight = 26.sp,
            textAlign = TextAlign.Center
        )
        Column(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Feature.entries.forEach { feature ->
                FeatureCard(feature) { selectedFeature = feature.name }
            }
        }
    }

    Feature.entries.firstOrNull { it.name == selectedFeature }?.let { feature ->
        AlertDialog(
            onDismissRequest = { selectedFeature = null },
            title = { Text(stringResource(feature.title)) },
            text = {
                Text(stringResource(feature.description) + "\n\n" + stringResource(R.string.feature_status))
            },
            confirmButton = {
                TextButton(onClick = { selectedFeature = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun FeatureCard(feature: Feature, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 84.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(PaddingValues(horizontal = 16.dp, vertical = 17.dp)),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = stringResource(feature.title),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 18.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(feature.subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            lineHeight = 23.sp
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HomePreview() {
    TemanLihatTheme { TemanLihatHome() }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, fontScale = 1.5f)
@Composable
private fun LargeTextPreview() {
    TemanLihatTheme { TemanLihatHome() }
}
