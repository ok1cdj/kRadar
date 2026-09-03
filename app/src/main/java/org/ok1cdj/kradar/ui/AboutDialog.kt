package org.ok1cdj.kradar.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.text.TextMMD
import org.ok1cdj.kradar.BuildConfig
import org.ok1cdj.kradar.R

private const val COFFEE_URL = "https://www.buymeacoffee.com/ok1cdj"
private const val GITHUB_URL = "https://github.com/ok1cdj/kRadar"

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Black, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.width(6.dp))
                TextMMD(text = stringRes(R.string.about_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(2.dp))
            TextMMD(text = stringRes(R.string.about_version, BuildConfig.VERSION_NAME), fontSize = 13.sp)
            TextMMD(text = stringRes(R.string.about_author), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            TextMMD(text = stringRes(R.string.about_desc), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            TextMMD(text = stringRes(R.string.about_license), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            TextMMD(text = stringRes(R.string.about_credits), fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))

            ButtonMMD(
                onClick = { openUrl(context, COFFEE_URL) },
                modifier = Modifier.fillMaxWidth().border(1.dp, Color.Black, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
            ) {
                TextMMD(text = stringRes(R.string.about_coffee), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            ButtonMMD(
                onClick = { openUrl(context, GITHUB_URL) },
                modifier = Modifier.fillMaxWidth().border(1.dp, Color.Black, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
            ) {
                TextMMD(text = stringRes(R.string.about_github), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            ButtonMMD(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                TextMMD(text = stringRes(R.string.close), fontSize = 15.sp)
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun stringRes(id: Int, arg: Any): String = androidx.compose.ui.res.stringResource(id, arg)
