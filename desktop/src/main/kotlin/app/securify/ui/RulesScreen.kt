package app.securify.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.securify.core.Store

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(onBack: () -> Unit) {
    val start = Store.state.value.prefs
    var proxy by rememberSaveable { mutableStateOf(start.proxyRules) }
    var direct by rememberSaveable { mutableStateOf(start.directRules) }
    var block by rememberSaveable { mutableStateOf(start.blockRules) }
    val close = {
        Store.prefs { it.copy(proxyRules = proxy.trim(), directRules = direct.trim(), blockRules = block.trim()) }
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rules)) },
                navigationIcon = { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).centeredContent().imePadding().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.rules_hint),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            listOf(
                Triple(R.string.rules_proxy, proxy) { v: String -> proxy = v },
                Triple(R.string.rules_direct, direct) { v: String -> direct = v },
                Triple(R.string.rules_block, block) { v: String -> block = v },
            ).forEach { (label, value, set) ->
                OutlinedTextField(
                    value, set, Modifier.fillMaxWidth().padding(top = 16.dp),
                    label = { Text(stringResource(label)) },
                    minLines = 3,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}

fun ruleCount(text: String) = text.lines().count { it.isNotBlank() && !it.trim().startsWith("#") }
