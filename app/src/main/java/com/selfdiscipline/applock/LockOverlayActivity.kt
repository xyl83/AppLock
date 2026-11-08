package com.selfdiscipline.applock

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 全屏密码拦截页
 */
class LockOverlayActivity : ComponentActivity() {

    private lateinit var prefs: PrefsManager
    private var deadlineCheckJob: Job? = null

    private val targetPackage: String? by lazy {
        intent.getStringExtra(EXTRA_TARGET_PACKAGE)
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "target_pkg"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PrefsManager(this)

        // 全屏沉浸
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        window.statusBarColor = Color.TRANSPARENT

        // 拦截返回键：必须输对密码才放行（Android 13+ 预测性返回已在 Manifest 关闭）
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 吞掉返回事件，停留在密码页
            }
        })

        startDeadlineChecker()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ComposeColor(0xFF121212)
                ) {
                    LockScreenContent(
                        prefs = prefs,
                        onUnlockSuccess = { grantUnlockAndFinish() }
                    )
                }
            }
        }
    }

    private fun startDeadlineChecker() {
        deadlineCheckJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                if (prefs.isDeadlineReached()) {
                    // 到达截止时间 → 奖励弹窗 + 关闭
                    showRewardDialog()
                    return@launch
                }
                delay(1000L)
            }
        }
    }

    private fun stopDeadlineChecker() {
        deadlineCheckJob?.cancel()
        deadlineCheckJob = null
    }

    private fun showRewardDialog() {
        AlertDialog.Builder(this)
            .setTitle("🎉 锁定时间已满！")
            .setMessage("恭喜你完成了自律目标！\n现在可以自由使用应用了。")
            .setCancelable(false)
            .setPositiveButton("好的") { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() {
        stopDeadlineChecker()
        super.onDestroy()
    }

    /**
     * 密码验证通过：先登记本次放行的包名，再关闭拦截页。
     * 否则关闭后无障碍服务会立刻重新收到被锁应用的窗口事件并再次拉起本页。
     */
    private fun grantUnlockAndFinish() {
        targetPackage?.let { prefs.grantUnlockFor(it) }
        finish()
    }
}

// =============== Compose UI ===============

@Composable
private fun LockScreenContent(
    prefs: PrefsManager,
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current as Activity
    var password by remember { mutableStateOf("") }
    var attemptCount by remember { mutableStateOf(prefs.getAttemptCount()) }
    var showPassword by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { /* nothing */ }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor(0xFF121212))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🔒 自律锁",
            color = ComposeColor.White,
            fontSize = 28.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "请输入密码解锁",
            color = ComposeColor(0xFFAAAAAA),
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            keyboardActions = KeyboardActions(
                onDone = { submitPassword(prefs, password) { advice, newCount, success ->
                    attemptCount = newCount
                    if (success) {
                        onUnlockSuccess()
                    } else {
                        context.runOnUiThread {
                            AlertDialog.Builder(context)
                                .setTitle("💡 忠告")
                                .setMessage(advice)
                                .setCancelable(true)
                                .setPositiveButton("记住了") { _, _ -> }
                                .show()
                        }
                    }
                    password = ""
                } }
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                submitPassword(prefs, password) { advice, newCount, success ->
                    attemptCount = newCount
                    if (success) {
                        onUnlockSuccess()
                    } else {
                        context.runOnUiThread {
                            AlertDialog.Builder(context)
                                .setTitle("💡 忠告")
                                .setMessage(advice)
                                .setCancelable(true)
                                .setPositiveButton("记住了") { _, _ -> }
                                .show()
                        }
                    }
                    password = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("确认")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 红色计数文字：已经提交了 X / 最大10次
        Text(
            text = if (attemptCount == 0) "已提交 0 / ${PrefsManager.MAX_ATTEMPTS}"
                   else "已提交 $attemptCount / ${PrefsManager.MAX_ATTEMPTS}",
            color = ComposeColor(0xFFFF4444),
            fontSize = 14.sp
        )

        // 提示10次后自动重置
        if (attemptCount == PrefsManager.MAX_ATTEMPTS) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "（计数将自动归零）",
                color = ComposeColor(0xFFFF8888),
                fontSize = 12.sp
            )
        }

        if (message.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = ComposeColor(0xFFFF8844),
                fontSize = 14.sp
            )
        }
    }
}

/**
 * 提交密码：验证 + 计数 + 获取忠告
 * 无论对错都增加计数、弹忠告
 * 密码正确才放行
 */
private fun submitPassword(
    prefs: PrefsManager,
    input: String,
    callback: (advice: String, newCount: Int, success: Boolean) -> Unit
) {
    val success = prefs.checkPassword(input)
    val newCount = prefs.incrementAttemptCount()
    val advice = prefs.nextAdvice()
    callback(advice, newCount, success)
}
