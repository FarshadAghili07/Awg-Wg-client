package com.network.awg

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

data class AppInfoItem(
    val name: String,
    val packageName: String,
    var isSelected: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AwgVpnMainScreen()
        }
    }
}

@Composable
fun AwgVpnMainScreen() {
    val context = LocalContext.current
    val isRunning by TunnelService.isRunning.collectAsState()
    val downloadSpeed by TunnelService.downloadSpeed.collectAsState()
    val uploadSpeed by TunnelService.uploadSpeed.collectAsState()

    var configText by remember { mutableStateOf("") }
    var isDarkTheme by remember { mutableStateOf(true) }
    var showSplitTunnelDialog by remember { mutableStateOf(false) }

    // برنامه‌های مستثنی شده در Split Tunneling
    val disallowedApps = remember { mutableStateListOf<String>() }

    // لانچر دسترسی VPN
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
            Toast.makeText(context, "مجوز اتصال VPN تایید نشد", Toast.LENGTH_SHORT).show()
        }
    }

    // لانچر اسکنر QR کد
    val qrScannerLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        if (result.contents != null) {
            configText = result.contents
            Toast.makeText(context, "کانفیگ با موفقیت از QR دریافت شد", Toast.LENGTH_SHORT).show()
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

    // انیمیشن تنفس دکمه اتصال هنگام اتصال
    val infiniteTransition = rememberInfiniteTransition(label = "btn_pulse")
    val pulseBorder by infiniteTransition.animateFloat(
        initialValue = 2f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_anim"
    )

    fun formatSpeedRate(bytesPerSec: Long): String {
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

            // نوار بالای صفحه
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
                    // دکمه Split Tunneling
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                            .clickable { showSplitTunnelDialog = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Apps", color = purpleAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

            // مانیتورینگ زنده ترافیک و سرعت دانلود/آپلود
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
                        Text("⤓ DOWNLOAD", color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isRunning) formatSpeedRate(downloadSpeed) else "0 KB/s",
                            color = cyanAccent,
                            fontSize = 18.sp,
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
                        Text("⤒ UPLOAD", color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isRunning) formatSpeedRate(uploadSpeed) else "0 KB/s",
                            color = purpleAccent,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // دکمه بزرگ دایره‌ای اتصال VPN
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
                                Toast.makeText(context, "لطفاً کانفیگ را وارد کنید یا اسکن نمایید", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
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
                        text = if (isRunning) "DISCONNECT" else "CONNECT",
                        color = if (isRunning) activeGreen else textMain,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
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

            Spacer(modifier = Modifier.height(30.dp))

            // بخش افزودن کانفیگ (دکمه QR و فیلد متنی)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AmneziaWG Config (.conf)", color = textMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                // دکمه اسکن بارکد دوربین
                Button(
                    onClick = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("کیوآرکد کانفیگ را اسکن کنید")
                            setBeepEnabled(true)
                            setOrientationLocked(true)
                        }
                        qrScannerLauncher.launch(options)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("📷 اسکن QR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = configText,
                onValueChange = { configText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                placeholder = {
                    Text(
                        "[Interface]\nPrivateKey = ...\nAddress = 10.0.0.2/32\nJc = 4\nJmin = 50\nJmax = 1000\nS1 = 15\nS2 = 25\nH1 = 1234\n...\n[Peer]\nPublicKey = ...\nEndpoint = ...",
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

            Spacer(modifier = Modifier.height(14.dp))

            if (disallowedApps.isNotEmpty()) {
                Text(
                    text = "برنامه‌های مستثنی شده (Split): ${disallowedApps.size} برنامه",
                    color = purpleAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // دیالوگ Split Tunneling
        if (showSplitTunnelDialog) {
            SplitTunnelAppListDialog(
                context = context,
                selectedPackages = disallowedApps,
                onDismiss = { showSplitTunnelDialog = false }
            )
        }
    }
}

@Composable
fun SplitTunnelAppListDialog(
    context: Context,
    selectedPackages: MutableList<String>,
    onDismiss: () -> Unit
) {
    val pm = context.packageManager
    var appList by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        appList = installed
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 } // برنامه‌های نصبی کاربر
            .map { app ->
                AppInfoItem(
                    name = app.loadLabel(pm).toString(),
                    packageName = app.packageName,
                    isSelected = selectedPackages.contains(app.packageName)
                )
            }
            .sortedBy { it.name }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Split Tunneling (دور زدن VPN)",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "برنامه‌های انتخاب‌شده مستقیم به اینترنت وصل می‌شوند (از تونل رد نمی‌شوند):",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(appList) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    item.isSelected = !item.isSelected
                                    if (item.isSelected) {
                                        if (!selectedPackages.contains(item.packageName)) {
                                            selectedPackages.add(item.packageName)
                                        }
                                    } else {
                                        selectedPackages.remove(item.packageName)
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.isSelected,
                                onCheckedChange = { checked ->
                                    item.isSelected = checked
                                    if (checked) {
                                        if (!selectedPackages.contains(item.packageName)) {
                                            selectedPackages.add(item.packageName)
                                        }
                                    } else {
                                        selectedPackages.remove(item.packageName)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(item.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(item.packageName, color = Color(0xFF64748B), fontSize = 11.sp)
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
                    Text("تأیید و بازگشت", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

