package com.network.awg

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

data class AppItem(
    val name: String,
    val packageName: String,
    var isBypassed: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AwgClientApp()
        }
    }
}

@Composable
fun AwgClientApp() {
    val context = LocalContext.current
    val prefs: SharedPreferences = remember {
        context.getSharedPreferences("awg_prefs", Context.MODE_PRIVATE)
    }

    val isRunning by TunnelService.isRunning.collectAsState()
    val downloadSpeed by TunnelService.downloadSpeed.collectAsState()
    val uploadSpeed by TunnelService.uploadSpeed.collectAsState()

    // بارگذاری کانفیگ ذخیره شده قبلی
    var configText by remember { mutableStateOf(prefs.getString("saved_config", "") ?: "") }
    var isDarkTheme by remember { mutableStateOf(true) }
    var isEnglish by remember { mutableStateOf(false) }
    var showSplitTunnelDialog by remember { mutableStateOf(false) }

    val disallowedApps = remember {
        mutableStateListOf<String>().apply {
            addAll(prefs.getStringSet("disallowed_apps", emptySet()) ?: emptySet())
        }
    }

    fun saveConfigToStorage(text: String) {
        prefs.edit().putString("saved_config", text).apply()
        Toast.makeText(context, if (isEnglish) "Config Saved!" else "کانفیگ ذخیره شد", Toast.LENGTH_SHORT).show()
    }

    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(context, TunnelService::class.java).apply {
                action = TunnelService.ACTION_CONNECT
                putExtra(TunnelService.EXTRA_CONFIG, configText)
                putStringArrayListExtra(TunnelService.EXTRA_DISALLOWED_APPS, ArrayList(disallowedApps))
            }
            context.startService(intent)
        } else {
            Toast.makeText(context, if (isEnglish) "VPN Permission Denied" else "مجوز اتصال تایید نشد", Toast.LENGTH_SHORT).show()
        }
    }

    val qrScannerLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        if (result.contents != null) {
            configText = result.contents
            saveConfigToStorage(result.contents)
        }
    }

    val baseBg = if (isDarkTheme) Color(0xFF0A0F1D) else Color(0xFFF4F6F9)
    val cardBg = if (isDarkTheme) Color(0xFF131B2E) else Color(0xFFFFFFFF)
    val textMain = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDarkTheme) Color(0xFF8C9AA9) else Color(0xFF6B7280)
    val borderCol = if (isDarkTheme) Color(0xFF202B42) else Color(0xFFE2E8F0)
    val cyanAccent = Color(0xFF00E5FF)
    val purpleAccent = Color(0xFFA855F7)
    val activeGreen = Color(0xFF10B981)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseBorder by infiniteTransition.animateFloat(
        initialValue = 2.5f,
        targetValue = 5.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_border"
    )

    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024.0)
            else -> "$bytesPerSec B/s"
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = baseBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // هدر بالا
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AMNEZIA WG",
                    color = textMain,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // سوییچ زبان
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                            .clickable { isEnglish = !isEnglish }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(if (isEnglish) "FA" else "EN", color = cyanAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // دکمه Split Tunneling
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                            .clickable { showSplitTunnelDialog = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(if (isEnglish) "Apps" else "برنامه‌ها", color = purpleAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // تغییر تم
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                            .clickable { isDarkTheme = !isDarkTheme }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(if (isDarkTheme) "☀️" else "🌙", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // باکس‌های سرعت دانلود و آپلود
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, borderCol, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(if (isEnglish) "⤓ DOWNLOAD" else "⤓ دانلود", color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isRunning) formatSpeed(downloadSpeed) else "0 KB/s",
                            color = cyanAccent,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, borderCol, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(if (isEnglish) "⤒ UPLOAD" else "⤒ آپلود", color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isRunning) formatSpeed(uploadSpeed) else "0 KB/s",
                            color = purpleAccent,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // دکمه بزرگ اتصال VPN
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .clip(CircleShape)
                    .background(cardBg)
                    .border(
                        if (isRunning) pulseBorder.dp else 2.5.dp,
                        if (isRunning) activeGreen else cyanAccent,
                        CircleShape
                    )
                    .clickable {
                        if (isRunning) {
                            val intent = Intent(context, TunnelService::class.java).apply {
                                action = TunnelService.ACTION_DISCONNECT
                            }
                            context.startService(intent)
                        } else {
                            if (configText.isBlank()) {
                                Toast.makeText(context, if (isEnglish) "Please enter or scan a config" else "لطفاً کانفیگ را وارد کنید", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            saveConfigToStorage(configText)

                            val prepareIntent = VpnService.prepare(context)
                            if (prepareIntent != null) {
                                vpnPrepareLauncher.launch(prepareIntent)
                            } else {
                                val intent = Intent(context, TunnelService::class.java).apply {
                                    action = TunnelService.ACTION_CONNECT
                                    putExtra(TunnelService.EXTRA_CONFIG, configText)
                                    putStringArrayListExtra(TunnelService.EXTRA_DISALLOWED_APPS, ArrayList(disallowedApps))
                                }
                                context.startService(intent)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isRunning) (if (isEnglish) "DISCONNECT" else "قطع اتصال") else (if (isEnglish) "CONNECT" else "اتصال"),
                        color = if (isRunning) activeGreen else textMain,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isRunning) "● CONNECTED" else "○ READY",
                        color = if (isRunning) activeGreen else textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // نوار بالای کادر متنی با دکمه ذخیره و اسکن QR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEnglish) "Configuration (URI / .conf)" else "کانفیگ (لینک یا فایل .conf)",
                    color = textMain,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // دکمه ثبت کانفیگ
                    Button(
                        onClick = { saveConfigToStorage(configText) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = activeGreen),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(if (isEnglish) "Save" else "ثبت کانفیگ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // دکمه اسکنر QR
                    Button(
                        onClick = {
                            val options = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt(if (isEnglish) "Scan QR Code" else "بارکد کانفیگ را اسکن کنید")
                                setBeepEnabled(true)
                                setOrientationLocked(true)
                            }
                            qrScannerLauncher.launch(options)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(if (isEnglish) "📷 QR" else "📷 اسکن", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = configText,
                onValueChange = { configText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                placeholder = {
                    Text(
                        "wg://... یا awg://... یا [Interface]...",
                        color = textMuted.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = cyanAccent,
                    unfocusedBorderColor = borderCol,
                    focusedTextColor = textMain,
                    unfocusedTextColor = textMain
                ),
                shape = RoundedCornerShape(14.dp)
            )

            if (disallowedApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isEnglish) "${disallowedApps.size} apps bypassed via Split Tunnel" else "${disallowedApps.size} برنامه از تونل مستثنی شدند",
                    color = purpleAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(26.dp))
        }

        // دیالوگ کامل Split Tunneling با تمام برنامه‌های گوشی
        if (showSplitTunnelDialog) {
            SplitTunnelDialog(
                context = context,
                isEnglish = isEnglish,
                selectedPackages = disallowedApps,
                onDismiss = {
                    prefs.edit().putStringSet("disallowed_apps", disallowedApps.toSet()).apply()
                    showSplitTunnelDialog = false
                }
            )
        }
    }
}

@Composable
fun SplitTunnelDialog(
    context: Context,
    isEnglish: Boolean,
    selectedPackages: MutableList<String>,
    onDismiss: () -> Unit
) {
    val pm = context.packageManager
    var appList by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        // تفکیک تمام برنامه‌هایی که آیکون یا امکان باز شدن دارند
        appList = installed
            .filter { app ->
                // برنامه‌های دارای Intent باز شدن یا برنامه‌های غیرسیستمی
                pm.getLaunchIntentForPackage(app.packageName) != null || ((app.flags and ApplicationInfo.FLAG_SYSTEM) == 0)
            }
            .map { app ->
                AppItem(
                    name = app.loadLabel(pm).toString(),
                    packageName = app.packageName,
                    isBypassed = selectedPackages.contains(app.packageName)
                )
            }
            .sortedBy { it.name.lowercase() }
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isEnglish) "Split Tunneling (Bypass Apps)" else "اسپلیت تونلینگ (دور زدن VPN)",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isEnglish) "Check apps that should NOT use the VPN (Direct Internet):" else "برنامه‌هایی که نباید از فیلترشکن رد شوند (اتصال مستقیم) را تیک بزنید:",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF00E5FF))
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(appList) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        app.isBypassed = !app.isBypassed
                                        if (app.isBypassed) {
                                            if (!selectedPackages.contains(app.packageName)) selectedPackages.add(app.packageName)
                                        } else {
                                            selectedPackages.remove(app.packageName)
                                        }
                                    }
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = app.isBypassed,
                                    onCheckedChange = { checked ->
                                        app.isBypassed = checked
                                        if (checked) {
                                            if (!selectedPackages.contains(app.packageName)) selectedPackages.add(app.packageName)
                                        } else {
                                            selectedPackages.remove(app.packageName)
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(app.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(app.packageName, color = Color(0xFF64748B), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text(if (isEnglish) "Done" else "تأیید و بازگشت", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
