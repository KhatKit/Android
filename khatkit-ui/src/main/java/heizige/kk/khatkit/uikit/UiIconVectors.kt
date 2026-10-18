package heizige.kk.khatkit.uikit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import heizige.kk.khatkit.ui.UiIcons

/** `Icon.name` / `Button.icon` 的名字到 Material 图标的映射，名单由 [UiIcons] 校验。 */
object UiIconVectors {
    private val vectors: Map<String, ImageVector> = mapOf(
        "account_circle" to Icons.Filled.AccountCircle,
        "add" to Icons.Filled.Add,
        "analytics" to Icons.Filled.Analytics,
        "arrow_back" to Icons.Filled.ArrowBack,
        "arrow_downward" to Icons.Filled.ArrowDownward,
        "arrow_forward" to Icons.Filled.ArrowForward,
        "arrow_upward" to Icons.Filled.ArrowUpward,
        "block" to Icons.Filled.Block,
        "bolt" to Icons.Filled.Bolt,
        "calendar_month" to Icons.Filled.CalendarMonth,
        "cancel" to Icons.Filled.Cancel,
        "celebration" to Icons.Filled.Celebration,
        "check" to Icons.Filled.Check,
        "check_circle" to Icons.Filled.CheckCircle,
        "close" to Icons.Filled.Close,
        "delete" to Icons.Filled.Delete,
        "edit" to Icons.Filled.Edit,
        "email" to Icons.Filled.Email,
        "error" to Icons.Filled.Error,
        "event" to Icons.Filled.Event,
        "expand_more" to Icons.Filled.ExpandMore,
        "favorite" to Icons.Filled.Favorite,
        "favorite_border" to Icons.Filled.FavoriteBorder,
        "history" to Icons.Filled.History,
        "hourglass_top" to Icons.Filled.HourglassTop,
        "info" to Icons.Filled.Info,
        "insights" to Icons.Filled.Insights,
        "link" to Icons.Filled.Link,
        "lock" to Icons.Filled.Lock,
        "more_vert" to Icons.Filled.MoreVert,
        "notifications" to Icons.Filled.Notifications,
        "open_in_new" to Icons.Filled.OpenInNew,
        "paid" to Icons.Filled.Paid,
        "people" to Icons.Filled.People,
        "person" to Icons.Filled.Person,
        "psychology" to Icons.Filled.Psychology,
        "refresh" to Icons.Filled.Refresh,
        "remove" to Icons.Filled.Remove,
        "rocket_launch" to Icons.Filled.RocketLaunch,
        "schedule" to Icons.Filled.Schedule,
        "search" to Icons.Filled.Search,
        "settings" to Icons.Filled.Settings,
        "share" to Icons.Filled.Share,
        "star" to Icons.Filled.Star,
        "star_border" to Icons.Filled.StarBorder,
        "sync" to Icons.Filled.Sync,
        "thumb_down" to Icons.Filled.ThumbDown,
        "thumb_up" to Icons.Filled.ThumbUp,
        "timer" to Icons.Filled.Timer,
        "trending_down" to Icons.Filled.TrendingDown,
        "trending_up" to Icons.Filled.TrendingUp,
        "verified" to Icons.Filled.Verified,
        "visibility" to Icons.Filled.Visibility,
        "warning" to Icons.Filled.Warning,
    )

    fun vector(name: String): ImageVector? = vectors[name]

    val names: Set<String> get() = vectors.keys
}
