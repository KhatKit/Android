package heizige.kk.khatkit.app.feature.automation

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import java.io.File

@Composable
fun TraceAuditPage() {
    val context = LocalContext.current
    val tracer = remember(context) { tracerFor(context) }
    var traces by remember { mutableStateOf(tracer.list()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val record = selectedId?.let { tracer.load(it) }
    val views = record?.toStepViews().orEmpty()
    var cursor by remember(selectedId) { mutableStateOf(TraceBrowseState(0, views.size)) }
    val current = views.getOrNull(cursor.index)

    KedgeSettingsPageScaffold(
        title = "轨迹审计",
        navigationIcon = { BackButton() },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LayoutDirection.Ltr) + 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                end = padding.calculateEndPadding(LayoutDirection.Ltr) + 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (traces.isEmpty()) {
                item { Text("还没有轨迹。设备动作执行后会出现在这里。") }
            }
            items(traces, key = { it.id }) { meta ->
                KedgeCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { selectedId = meta.id },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(meta.label.ifBlank { meta.id }, style = MaterialTheme.typography.titleMedium)
                        Text("${meta.stepCount} 步", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            current?.let { step ->
                item {
                    KedgeCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(step.title, style = MaterialTheme.typography.titleMedium)
                            if (step.detail.isNotBlank()) Text(step.detail)
                            Text(if (step.success) "成功" else "失败")
                            if (step.result.isNotBlank()) Text(step.result)
                            step.hit?.let { Text("命中 $it") }
                            step.screenshotPath?.let { Text(it) }
                            Text("${cursor.index + 1} / ${cursor.count}")
                        }
                    }
                }
                item {
                    KedgeButton(onClick = { cursor = cursor.previous() }, enabled = cursor.hasPrevious) {
                        Text("上一步")
                    }
                }
                item {
                    KedgeButton(onClick = { cursor = cursor.next() }, enabled = cursor.hasNext) {
                        Text("下一步")
                    }
                }
            }
        }
    }
}

internal fun tracerFor(context: Context): AutomationTracer =
    AutomationTracer(File(context.filesDir, "khatkit/traces"))
