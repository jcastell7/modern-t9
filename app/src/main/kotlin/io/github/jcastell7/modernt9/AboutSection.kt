package io.github.jcastell7.modernt9

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The "About & licences" section of the settings screen.
 *
 * This is not decoration: the dictionaries are CC BY-SA 4.0, which requires attribution
 * that is reasonably visible to the people receiving the work — the app's users, not
 * just readers of the source. This is where that obligation is met.
 *
 * Links open in the browser via an intent. The app itself has no network permission and
 * needs none for this.
 */
@Composable
fun AboutSection() {
    val context = LocalContext.current
    val version = remember { appVersion(context) }

    Text(stringResource(R.string.about_title), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Row(modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)) {
        Text(stringResource(R.string.about_version, version), fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Link(stringResource(R.string.about_source), stringResource(R.string.about_source_url))
    }

    LicenceEntry(
        title = stringResource(R.string.licence_app_title),
        body = stringResource(R.string.licence_app_body),
        links = listOf(
            stringResource(R.string.licence_app_link) to stringResource(R.string.licence_app_url),
        ),
    )

    LicenceEntry(
        title = stringResource(R.string.licence_dict_title),
        body = stringResource(R.string.licence_dict_body),
        links = listOf(
            stringResource(R.string.licence_dict_source_link) to stringResource(R.string.licence_dict_source_url),
            stringResource(R.string.licence_dict_licence_link) to stringResource(R.string.licence_dict_licence_url),
        ),
    )

    LicenceEntry(
        title = stringResource(R.string.licence_libs_title),
        body = stringResource(R.string.licence_libs_body),
        links = listOf(
            stringResource(R.string.licence_libs_link) to stringResource(R.string.licence_libs_url),
        ),
    )
}

@Composable
private fun LicenceEntry(title: String, body: String, links: List<Pair<String, String>>) {
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(body, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.padding(top = 4.dp)) {
            links.forEachIndexed { index, (label, url) ->
                if (index > 0) Spacer(Modifier.width(16.dp))
                Link(label, url)
            }
        }
    }
}

@Composable
private fun Link(label: String, url: String) {
    val context = LocalContext.current
    Text(
        text = label,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable { openUrl(context, url) },
    )
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // A device with no browser is rare but real; failing quietly beats crashing settings.
    runCatching { context.startActivity(intent) }
}

private fun appVersion(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: ""
