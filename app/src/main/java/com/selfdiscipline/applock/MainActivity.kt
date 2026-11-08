package com.selfdiscipline.applock

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主界面：密码设置 / 忠告编辑 / 锁定时长 / 应用勾选 / 权限引导
 */
class MainActivity : ComponentActivity() {

    private lateinit var prefs: PrefsManager

    // 每次回到前台递增，驱动权限页重新检测权限状态
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainContent(prefs = prefs, resumeTick = resumeTick)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 用户从系统设置授权页返回时触发刷新，避免页面还显示"未开启"
        resumeTick++
    }
}

// ============ Tab 切换 ============

private enum class TabItem(val title: String) {
    PASSWORD("密码"),
    ADVICE("忠告"),
    LOCK("锁定时长"),
    APPS("应用选择"),
    PERMISSION("权限引导")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(prefs: PrefsManager, resumeTick: Int) {
    var selectedTab by remember { mutableStateOf(TabItem.PASSWORD) }
    val tabs = TabItem.values()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🔒 自律应用锁") })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                tabs.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    TabItem.PASSWORD -> PasswordTab(prefs)
                    TabItem.ADVICE -> AdviceTab(prefs)
                    TabItem.LOCK -> LockDurationTab(prefs)
                    TabItem.APPS -> AppSelectTab(prefs)
                    TabItem.PERMISSION -> PermissionTab(prefs, resumeTick)
                }
            }
        }
    }
}

// ============ Tab 1：密码设置 ============

@Composable
private fun PasswordTab(prefs: PrefsManager) {
    val context = LocalContext.current
    var hasPassword by remember { mutableStateOf(prefs.hasPassword()) }
    var password1 by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var showPwd by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (hasPassword) "已设置密码" else "尚未设置密码",
            style = MaterialTheme.typography.titleMedium,
            color = if (hasPassword) Color(0xFF2E7D32) else Color(0xFFC62828)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password1,
            onValueChange = { password1 = it },
            label = { Text("${if (hasPassword) "新" else ""}密码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation()
        )

        OutlinedTextField(
            value = password2,
            onValueChange = { password2 = it },
            label = { Text("再次输入密码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showPwd, onCheckedChange = { showPwd = it })
            Text("显示密码")
        }

        if (message.isNotEmpty()) {
            Text(
                text = message,
                color = if (message.startsWith("✅")) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }

        Button(
            onClick = {
                when {
                    password1.isBlank() -> message = "❌ 密码不能为空"
                    password1 != password2 -> message = "❌ 两次输入不一致"
                    password1.length < 4 -> message = "❌ 密码至少 4 位"
                    else -> {
                        prefs.savePassword(password1)
                        hasPassword = true
                        password1 = ""
                        password2 = ""
                        message = "✅ 密码保存成功"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (hasPassword) "修改密码" else "设置密码")
        }

        if (hasPassword) {
            Button(
                onClick = {
                    prefs.clearPassword()
                    hasPassword = false
                    password1 = ""
                    password2 = ""
                    message = "✅ 密码已清除"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清除密码")
            }
        }
    }
}

// ============ Tab 2：忠告列表 ============

@Composable
private fun AdviceTab(prefs: PrefsManager) {
    val advices = remember { mutableStateListOf<String>() }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditIndex by remember { mutableStateOf<Int?>(null) }
    var dialogText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        advices.clear()
        advices.addAll(prefs.getAdvices())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "忠告列表（每次提交密码会循环显示）",
                style = MaterialTheme.typography.titleSmall
            )
            Button(onClick = {
                dialogText = ""
                showAddDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("新增")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (advices.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无忠告，点击右上角新增", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(advices) { advice ->
                    val idx = advices.indexOf(advice)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}.",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(32.dp)
                            )
                            Text(
                                text = advice,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                dialogText = advice
                                showEditIndex = idx
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑")
                            }
                            IconButton(onClick = {
                                advices.removeAt(idx)
                                prefs.setAdvices(advices.toList())
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }

    // 新增对话框
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新增忠告") },
            text = {
                OutlinedTextField(
                    value = dialogText,
                    onValueChange = { dialogText = it },
                    label = { Text("忠告内容") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dialogText.isNotBlank()) {
                        advices.add(dialogText.trim())
                        prefs.setAdvices(advices.toList())
                    }
                    showAddDialog = false
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
    }

    // 编辑对话框
    if (showEditIndex != null) {
        AlertDialog(
            onDismissRequest = { showEditIndex = null },
            title = { Text("编辑忠告") },
            text = {
                OutlinedTextField(
                    value = dialogText,
                    onValueChange = { dialogText = it },
                    label = { Text("忠告内容") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dialogText.isNotBlank()) {
                        advices[showEditIndex!!] = dialogText.trim()
                        prefs.setAdvices(advices.toList())
                    }
                    showEditIndex = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEditIndex = null }) { Text("取消") }
            }
        )
    }
}

// ============ Tab 3：锁定时长 ============

@Composable
private fun LockDurationTab(prefs: PrefsManager) {
    var durationInput by remember { mutableStateOf(prefs.lockDurationMinutes.toString()) }
    var deadlineText by remember { mutableStateOf("") }
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    fun updateDeadlineText() {
        val ts = prefs.deadlineTimestamp
        deadlineText = if (ts > 0L) sdf.format(Date(ts)) else "未设置"
    }

    LaunchedEffect(Unit) { updateDeadlineText() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "设置锁定时长（分钟）",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = durationInput,
            onValueChange = { v ->
                durationInput = v.filter { it.isDigit() }
            },
            label = { Text("锁定时长（分钟）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = {
                val mins = durationInput.toIntOrNull()
                if (mins != null && mins > 0) {
                    prefs.lockDurationMinutes = mins
                    prefs.setDeadlineFromNow(mins)
                    updateDeadlineText()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("开始锁定（从此刻起计时）")
        }

        Button(
            onClick = {
                prefs.lockDurationMinutes = 0
                prefs.deadlineTimestamp = 0L
                updateDeadlineText()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("取消锁定")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "当前锁定截止时间：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = deadlineText,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (prefs.isDeadlineReached()) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (prefs.isDeadlineReached()) "✅ 已到期，打开被锁 App 不会弹密码" else "⏳ 未到期，打开被锁 App 会弹密码",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

// ============ Tab 4：应用选择 ============

@Composable
private fun AppSelectTab(prefs: PrefsManager) {
    data class AppInfo(val pkg: String, val label: String, val icon: ImageBitmap?)

    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    val lockedPkgs = remember { mutableStateOf(prefs.getLockedPackages()) }
    var filter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(0)
            val list = installed.map { app ->
                AppInfo(
                    pkg = app.packageName,
                    label = try { pm.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName },
                    icon = try {
                        pm.getApplicationIcon(app).toBitmap(96, 96).asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                )
            }.filter { it.pkg != context.packageName }
                .sortedBy { it.label.lowercase() }
            withContext(Dispatchers.Main) { apps = list }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "勾选要锁定的应用",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("搜索应用名或包名") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "已锁定 ${lockedPkgs.value.size} 个应用",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        val filtered = apps.filter {
            it.label.contains(filter, ignoreCase = true) || it.pkg.contains(filter, ignoreCase = true)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filtered) { app ->
                val locked = lockedPkgs.value.contains(app.pkg)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newSet = lockedPkgs.value.toMutableSet()
                            if (locked) newSet.remove(app.pkg) else newSet.add(app.pkg)
                            lockedPkgs.value = newSet
                            prefs.setLockedPackages(newSet)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = locked,
                        onCheckedChange = { checked ->
                            val newSet = lockedPkgs.value.toMutableSet()
                            if (checked) newSet.add(app.pkg) else newSet.remove(app.pkg)
                            lockedPkgs.value = newSet
                            prefs.setLockedPackages(newSet)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    app.icon?.let { bmp ->
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = app.label, fontSize = 15.sp)
                        Text(text = app.pkg, fontSize = 12.sp, color = Color.Gray)
                    }
                    if (locked) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============ Tab 5：权限引导 ============

@Composable
private fun PermissionTab(prefs: PrefsManager, resumeTick: Int) {
    val context = LocalContext.current

    fun isAccessibilityEnabled(): Boolean {
        val expected = context.packageName + "/" + LockAccessibilityService::class.java.name
        return try {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            enabled?.split(':')?.any { it.equals(expected, ignoreCase = true) } == true
        } catch (_: Exception) { false }
    }

    fun canDrawOverlays(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true

    var a11yOn by remember { mutableStateOf(isAccessibilityEnabled()) }
    var overlayOn by remember { mutableStateOf(canDrawOverlays()) }
    var showPrivacy by remember { mutableStateOf(false) }

    LaunchedEffect(resumeTick) {
        a11yOn = isAccessibilityEnabled()
        overlayOn = canDrawOverlays()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "权限引导",
            style = MaterialTheme.typography.titleLarge
        )

        // 无障碍服务
        PermissionCard(
            title = "① 无障碍服务（核心必需）",
            desc = "用于监测前台应用，命中锁定策略时拉起密码拦截页。未开启则锁完全失效。",
            isOn = a11yOn,
            isRequired = true,
            actionLabel = if (a11yOn) "已开启" else "前往开启",
            intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        )

        // 悬浮窗
        PermissionCard(
            title = "② 悬浮窗 / 显示在其他应用上层",
            desc = "Android 12+ 必须开启此权限，才能在被锁应用之上弹出拦截页面。",
            isOn = overlayOn,
            isRequired = true,
            actionLabel = if (overlayOn) "已开启" else "前往开启",
            intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            else null
        )

        // 国产 ROM 警告
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "⚠️ 国产系统重要提醒",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC62828)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "小米 / OPPO / vivo / 荣耀 等国产 ROM 会在后台杀掉无障碍服务，导致应用锁失效！\n\n" +
                        "请务必额外做以下设置：\n\n" +
                        "1. 进入【设置 → 电池 / 省电 / 电量管理】，将本 App 加入【无限制 / 不限制 / 白名单】\n\n" +
                        "2. 进入【设置 → 应用管理 → 自启动管理 / 后台弹出界面 / 后台运行】，允许本 App 自启动\n\n" +
                        "3. 在最近任务列表中，下拉本 App 卡片选择【锁定】，防止被一键清理\n\n" +
                        "以上属于安卓系统限制，任何第三方应用锁都无法绕过，请知悉。",
                    color = Color(0xFF555555),
                    fontSize = 13.sp
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📌 使用说明",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• 本程序是自律自用工具，不是防盗锁，不能完全拦截返回键\n" +
                        "• 10 次提交计数满后自动归零继续循环\n" +
                        "• 密码正确才能解锁；错误则继续停留并弹忠告\n" +
                        "• 锁定时长到达截止时间后自动放行",
                    color = Color(0xFF555555),
                    fontSize = 13.sp
                )
            }
        }

        // 上架审核需要的隐私政策与权限说明入口
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "隐私政策与权限说明",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "本应用纯本地运行，不联网、无广告、不收集任何个人数据。",
                    color = Color(0xFF555555),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { showPrivacy = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("查看完整隐私政策")
                }
            }
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("隐私政策与权限说明") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = PRIVACY_TEXT, fontSize = 13.sp, lineHeight = 20.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) { Text("我已了解") }
            }
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    desc: String,
    isOn: Boolean,
    isRequired: Boolean,
    actionLabel: String,
    intent: Intent?
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isOn) "✅ 已开启" else "❌ 未开启",
                    color = if (isOn) Color(0xFF2E7D32) else Color(0xFFC62828),
                    fontSize = 12.sp
                )
                if (isRequired) {
                    Text(
                        text = " 必需",
                        color = Color(0xFFE65100),
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(text = desc, color = Color(0xFF555555), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    intent?.let { context.startActivity(it) }
                },
                enabled = !isOn,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(actionLabel)
            }
        }
    }
}

// ============ 隐私政策文本 ============

private const val PRIVACY_TEXT =
    "本应用是一款纯本地的自律工具，不联网、无广告、无任何第三方统计或广告 SDK，不会将任何信息上传至服务器。\n\n" +
        "一、本地保存的数据\n" +
        "1. 解锁密码：仅保存 SHA-256 哈希值，不保存明文\n" +
        "2. 您编辑的忠告文本\n" +
        "3. 您勾选锁定的应用包名\n" +
        "4. 锁定时长、截止时间与密码提交次数\n" +
        "在系统设置中清除应用数据或卸载应用，上述数据即全部删除。\n\n" +
        "二、读取已安装应用列表（QUERY_ALL_PACKAGES）\n" +
        "仅用于在“应用选择”页面展示可勾选的应用，全部在本机处理，不上传、不共享。\n\n" +
        "三、无障碍服务（AccessibilityService）\n" +
        "用途：识别当前前台应用的包名，当您打开已勾选锁定的应用时展示密码验证页面。\n" +
        "范围：仅读取窗口变化事件中的包名，不读取、不记录、不上传任何界面内容或输入内容。\n" +
        "您可随时在系统设置中关闭该服务，关闭后应用锁定功能将失效。\n\n" +
        "四、悬浮窗权限（SYSTEM_ALERT_WINDOW）\n" +
        "用于在被锁定的应用之上显示密码验证页面。\n\n" +
        "五、儿童\n" +
        "本应用不面向 14 周岁以下儿童，也不会主动收集儿童个人信息。\n\n" +
        "六、联系方式\n" +
        "如对本政策有疑问，可通过应用商店页面所列的开发者联系方式与我们联系。"
