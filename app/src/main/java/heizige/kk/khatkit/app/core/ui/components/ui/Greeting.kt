package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import java.util.Calendar

/** 按当前时段取问候语（「下午好👋」这类），空会话时聊天页顶栏的标题用它。 */
@Composable
fun greetingMessage(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> stringResource(id = R.string.menu_page_morning_greeting)
        in 12..17 -> stringResource(id = R.string.menu_page_afternoon_greeting)
        in 18..22 -> stringResource(id = R.string.menu_page_evening_greeting)
        else -> stringResource(id = R.string.menu_page_night_greeting)
    }
}
