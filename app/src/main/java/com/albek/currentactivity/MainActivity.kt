package com.albek.currentactivity

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.collectLatest


import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                CurrentActivityScreen()
            }
        }

        // إرسال Intent لإعادة إنشاء Overlay عند فتح التطبيق
        val intent = Intent(this, MyAccessibilityService::class.java)
        intent.action = "START_OVERLAY"
        startService(intent)
    }



}

@Composable
fun CurrentActivityScreen() {
    val context = LocalContext.current
    var packageName by remember { mutableStateOf("Waiting...") }
    var className by remember { mutableStateOf("Waiting...") }

    LaunchedEffect(Unit) {
        MyAccessibilityService.currentActivityFlow.collectLatest { info ->
            packageName = info.first ?: "Unknown"
            className = info.second ?: "Unknown"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(R.string.package_text, packageName), fontSize = 20.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = stringResource(R.string.activity_text, className), fontSize = 20.sp)
        Spacer(modifier = Modifier.height(30.dp))

        // زر لتفعيل Accessibility Service
        Button(onClick = {


            // فتح إعدادات Accessibility Service
            val accessibilityIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            accessibilityIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(accessibilityIntent)

            // طلب إذن Overlay أولاً
            if (!Settings.canDrawOverlays(context)) {
                val overlayIntent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri()
                )
                overlayIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(overlayIntent)
                return@Button // نوقف التنفيذ هنا حتى يعطي المستخدم الإذن
            }

        }) {
            Text(text = "Enable Accessibility Service")
        }


        Spacer(modifier = Modifier.height(20.dp))

        // زر لإيقاف التطبيق وإزالة Overlay
        Button(onClick = {
            // إرسال Intent للخدمة لإزالة Overlay

            val stopIntent = Intent(context, MyAccessibilityService::class.java)
            stopIntent.action = "STOP_OVERLAY"
            context.startService(stopIntent)

            // إغلاق التطبيق
            (context as? ComponentActivity)?.finishAffinity()
            // أو exitProcess(0) إذا أردت الإغلاق القوي
            // exitProcess(0)
        }) {
            Text(text = "Stop App")
        }
    }
}
