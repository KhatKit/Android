package heizige.kk.khatkit.app.feature.assistant.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AssistantMemoryPage(id: String) {
    val vm: AssistantDetailViewModel =
        hiltViewModel<AssistantDetailViewModel, AssistantDetailViewModel.Factory>(
            creationCallback = { it.create(id) },
        )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    RoleMemoryPanel(vm = vm, assistant = assistant)
}
