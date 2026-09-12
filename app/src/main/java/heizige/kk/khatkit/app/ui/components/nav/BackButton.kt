package heizige.kk.khatkit.app.ui.components.nav

import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.ui.context.LocalNavController
import heizige.kk.khatkit.app.ui.theme.CustomColors
import heizige.kk.khatkit.app.ui.icons.arrowBack

@Composable
fun BackButton(modifier: Modifier = Modifier) {
    val navController = LocalNavController.current
    FilledTonalIconButton(
        onClick = {
            navController.popBackStack()
        },
        modifier = modifier,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = CustomColors.listItemColors.containerColor),
    ) {
        Icon(
            imageVector = arrowBack,
            contentDescription = stringResource(R.string.back)
        )
    }
}
