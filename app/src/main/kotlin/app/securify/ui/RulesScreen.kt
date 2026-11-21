package app.securify.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.securify.R
import app.securify.core.Store
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    BackHandler(onBack = close)
    val mono = MiuixTheme.textStyles.body2.copy(fontFamily = FontFamily.Monospace)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.rules),
                navigationIcon = { IconButton(onClick = close) { Icon(MiuixIcons.ChevronBackward, null, Modifier.size(22.dp)) } },
                actions = { IconButton(onClick = close) { Icon(MiuixIcons.Ok, stringResource(R.string.save), Modifier.size(22.dp)) } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).centeredContent().imePadding().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.rules_hint),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 8.dp),
            )
            listOf(
                Triple(R.string.rules_proxy, proxy) { v: String -> proxy = v },
                Triple(R.string.rules_direct, direct) { v: String -> direct = v },
                Triple(R.string.rules_block, block) { v: String -> block = v },
            ).forEach { (label, value, set) ->
                Text(
                    stringResource(label),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp),
                )
                TextField(
                    value = value,
                    onValueChange = set,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    textStyle = mono,
                    insideMargin = DpSize(14.dp, 12.dp),
                )
            }
        }
    }
}

fun ruleCount(text: String) = text.lines().count { it.isNotBlank() && !it.trim().startsWith("#") }