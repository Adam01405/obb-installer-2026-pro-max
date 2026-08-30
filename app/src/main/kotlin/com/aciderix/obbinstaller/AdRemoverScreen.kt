package com.aciderix.obbinstaller

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aciderix.obbinstaller.ui.FileSourceCard
import com.aciderix.obbinstaller.ui.HubColors
import com.aciderix.obbinstaller.ui.HubGradientButton
import com.shinegirls.apkadremovereditor.core.AdPatternConfig
import com.shinegirls.apkadremovereditor.core.SubscriptionManager
import com.shinegirls.apkadremovereditor.utils.Format

@Composable
fun AdRemoverScreen(
    onPickApk: () -> Unit,
    vm: AdRemoverViewModel = viewModel()
) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    var showSettings by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }
    var showAddSub by remember { mutableStateOf(false) }
    var showEditSub by remember { mutableStateOf<SubscriptionManager.Subscription?>(null) }
    var previewSub by remember { mutableStateOf<SubscriptionManager.Subscription?>(null) }
    var showDone by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    // 日志自动滚动到底部
    LaunchedEffect(state.log.length) {
        if (state.log.isNotEmpty()) scrollState.animateScrollTo(scrollState.maxValue)
    }
    // 处理完成 / 出错时弹窗
    LaunchedEffect(state.result, state.error) {
        if (state.result != null) showDone = true
        if (state.error != null) showError = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FileSourceCard(
            title = stringResource(R.string.adr_title),
            statusLabel = state.apk?.let {
                stringResource(R.string.selected_label, it.displayName)
            } ?: stringResource(R.string.adr_pick_apk),
            isSelected = state.apk != null,
            icon = Icons.Outlined.Android,
            pickHint = stringResource(R.string.adr_pick_apk),
            formatHint = stringResource(R.string.format_apk),
            onPick = onPickApk,
            enabled = !state.isProcessing,
            canChange = true
        )

        SettingsCard(
            state = state,
            onToggleSettings = { showSettings = !showSettings },
            showSettings = showSettings,
            onSetSignMode = vm::setSignMode,
            onSetFlutter = vm::setFlutterEnabled,
            onSetDexOptimize = vm::setDexOptimizeEnabled,
            onSetSkipSigning = vm::setSkipSigning,
            onSetCategory = vm::setCategoryEnabled,
            onSetOutputDir = vm::setOutputDir,
            onResetOutputDir = vm::resetOutputDir,
            onSetConfigPath = vm::setConfigPath,
            onResetConfigPath = vm::resetConfigPath,
            onResetConfigToDefault = vm::resetConfigToDefault,
            onShowSubscriptions = { showSubs = true }
        )

        // 开始处理（与首页"安装 APK"同一样式：渐变发光按钮 + 卡片 + 进度条）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(HubColors.Surface)
                .border(BorderStroke(1.dp, HubColors.Border), RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HubGradientButton(
                    text = stringResource(
                        if (state.isProcessing) R.string.adr_processing else R.string.adr_start
                    ),
                    onClick = vm::start,
                    enabled = state.apk != null && !state.isProcessing,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Download
                )
                AnimatedVisibility(
                    visible = state.isProcessing,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            color = HubColors.Primary,
                            trackColor = HubColors.Border
                        )
                        Text(stringResource(R.string.adr_processing), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // 实时日志
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(HubColors.SurfaceMuted)
                .border(BorderStroke(1.dp, HubColors.Border), RoundedCornerShape(18.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.adr_log_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.log.isNotEmpty()) {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(state.log))
                            Toast.makeText(ctx, ctx.getString(R.string.log_copied), Toast.LENGTH_SHORT).show()
                        }) { Text(stringResource(R.string.copy_log), style = MaterialTheme.typography.labelSmall) }
                    }
                }
                HorizontalDivider(color = HubColors.Border.copy(alpha = 0.5f))
                Box(modifier = Modifier.weight(1f)) {
                    if (state.log.isEmpty()) {
                        Text(
                            stringResource(R.string.adr_log_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = HubColors.TextMuted,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    } else {
                        SelectionContainer {
                            Text(
                                text = state.log,
                                style = MaterialTheme.typography.bodySmall,
                                color = HubColors.TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSubs) {
        SubscriptionsDialog(
            subs = state.subscriptions,
            onDismiss = { showSubs = false },
            onAdd = { showAddSub = true },
            onToggle = vm::setSubscriptionEnabled,
            onDelete = vm::deleteSubscription,
            onEdit = { sub ->
                showEditSub = sub
            },
            onApply = vm::applyEnabledSubscriptions,
            onPreview = { sub ->
                previewSub = sub
            },
            onRestore = { sub ->
                vm.restoreSubscriptionConfig(sub) { ok ->
                    if (ok) {
                        Toast.makeText(ctx, ctx.getString(R.string.adr_restored), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, ctx.getString(R.string.adr_add_fail), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onShare = { sub ->
                val token = vm.shareSubscriptionToken(sub)
                val clip = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                clip.setPrimaryClip(ClipData.newPlainText("subscription", token))
                Toast.makeText(ctx, ctx.getString(R.string.adr_share_token), Toast.LENGTH_SHORT).show()
            }
        )
    }
    previewSub?.let { sub ->
        SubscriptionPreviewDialog(
            sub = sub,
            vm = vm,
            onDismiss = { previewSub = null }
        )
    }
    if (showAddSub) {
        AddSubscriptionDialog(
            onDismiss = { showAddSub = false },
            onConfirm = { token ->
                val ok = vm.addSubscription(token)
                if (ok) {
                    Toast.makeText(ctx, ctx.getString(R.string.adr_ok), Toast.LENGTH_SHORT).show()
                    showAddSub = false
                } else {
                    Toast.makeText(ctx, ctx.getString(R.string.adr_add_fail), Toast.LENGTH_SHORT).show()
                }
                ok
            }
        )
    }
    showEditSub?.let { sub ->
        EditSubscriptionDialog(
            sub = sub,
            onDismiss = { showEditSub = null },
            onSave = { updated ->
                vm.updateSubscription(updated)
                showEditSub = null
            }
        )
    }

    state.result?.let { r ->
        if (showDone) {
            DoneDialog(
                result = r,
                onDismiss = { showDone = false }
            )
        }
    }
    state.error?.let { e ->
        if (showError) {
            AlertDialog(
                onDismissRequest = { showError = false },
                title = { Text(stringResource(R.string.phase_error, "")) },
                text = { Text(e, style = MaterialTheme.typography.bodySmall) },
                confirmButton = {
                    TextButton(onClick = { showError = false }) { Text(stringResource(R.string.adr_ok)) }
                }
            )
        }
    }
}

@Composable
private fun SettingsCard(
    state: AdRemoverUiState,
    showSettings: Boolean,
    onToggleSettings: () -> Unit,
    onSetSignMode: (Int) -> Unit,
    onSetFlutter: (Boolean) -> Unit,
    onSetDexOptimize: (Boolean) -> Unit,
    onSetSkipSigning: (Boolean) -> Unit,
    onSetCategory: (AdPatternConfig.Category, Boolean) -> Unit,
    onSetOutputDir: (String) -> Unit,
    onResetOutputDir: () -> Unit,
    onSetConfigPath: (String) -> Unit,
    onResetConfigPath: () -> Unit,
    onResetConfigToDefault: () -> Unit,
    onShowSubscriptions: () -> Unit
) {
    val ctx = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(HubColors.Surface)
            .border(BorderStroke(1.dp, HubColors.Border), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleSettings),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(HubColors.Primary.copy(alpha = 0.15f))
                        .border(BorderStroke(1.dp, HubColors.Primary.copy(alpha = 0.4f)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Build,
                        contentDescription = null,
                        tint = HubColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.adr_settings), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                androidx.compose.material3.Icon(
                    imageVector = if (showSettings) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = HubColors.TextMuted
                )
            }

            AnimatedVisibility(visible = showSettings, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 签名效验去除模式
                    SectionTitle(stringResource(R.string.adr_sign_mode))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeChip(
                            label = stringResource(R.string.adr_sign_off),
                            selected = state.signMode == 0,
                            onClick = { onSetSignMode(0) }
                        )
                        ModeChip(
                            label = stringResource(R.string.adr_sign_normal),
                            selected = state.signMode == 1,
                            onClick = { onSetSignMode(1) }
                        )
                        ModeChip(
                            label = stringResource(R.string.adr_sign_original),
                            selected = state.signMode == 2,
                            onClick = { onSetSignMode(2) }
                        )
                    }

                    // Flutter / DEX / 跳过签名
                    SwitchRow(
                        label = stringResource(R.string.adr_flutter),
                        hint = stringResource(R.string.adr_flutter_hint),
                        checked = state.flutterEnabled,
                        onCheckedChange = onSetFlutter
                    )
                    SwitchRow(
                        label = stringResource(R.string.adr_dex_optimize),
                        hint = stringResource(R.string.adr_dex_optimize_hint),
                        checked = state.dexOptimizeEnabled,
                        onCheckedChange = onSetDexOptimize
                    )
                    SwitchRow(
                        label = stringResource(R.string.adr_skip_signing),
                        hint = stringResource(R.string.adr_skip_signing_hint),
                        checked = state.skipSigning,
                        onCheckedChange = onSetSkipSigning
                    )

                    // 路径设置
                    PathField(
                        label = stringResource(R.string.adr_output_dir),
                        value = state.outputDir,
                        resetLabel = stringResource(R.string.adr_output_dir_reset),
                        onValueChange = onSetOutputDir,
                        onReset = onResetOutputDir
                    )
                    PathField(
                        label = stringResource(R.string.adr_config_path),
                        value = state.configPath,
                        resetLabel = stringResource(R.string.adr_config_path_reset),
                        onValueChange = onSetConfigPath,
                        onReset = onResetConfigPath
                    )
                    OutlinedButton(
                        onClick = {
                            onResetConfigToDefault()
                            Toast.makeText(ctx, ctx.getString(R.string.adr_config_reset_default), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.adr_config_reset_default)) }

                    // 分类开关
                    SectionTitle(stringResource(R.string.adr_categories))
                    state.categories.forEach { (category, enabled) ->
                        SwitchRow(
                            label = ctx.getString(category.titleRes),
                            hint = category.key,
                            checked = enabled,
                            onCheckedChange = { onSetCategory(category, it) }
                        )
                    }

                    // 订阅
                    SectionTitle(stringResource(R.string.adr_subscriptions))
                    OutlinedButton(
                        onClick = onShowSubscriptions,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.adr_subscriptions)) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = HubColors.TextSecondary)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) HubColors.Primary.copy(alpha = 0.15f) else HubColors.SurfaceMuted)
            .border(
                BorderStroke(1.dp, if (selected) HubColors.Primary else HubColors.Border),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) HubColors.Primary else HubColors.TextSecondary
        )
    }
}

@Composable
private fun SwitchRow(label: String, hint: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = HubColors.TextMuted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PathField(
    label: String,
    value: String,
    resetLabel: String,
    onValueChange: (String) -> Unit,
    onReset: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onReset) { Text(resetLabel, style = MaterialTheme.typography.labelSmall) }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall,
            singleLine = true
        )
    }
}

@Composable
private fun SubscriptionsDialog(
    subs: List<SubscriptionManager.Subscription>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (SubscriptionManager.Subscription) -> Unit,
    onApply: () -> Unit,
    onPreview: (SubscriptionManager.Subscription) -> Unit,
    onRestore: (SubscriptionManager.Subscription) -> Unit,
    onShare: (SubscriptionManager.Subscription) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adr_subscriptions)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (subs.isEmpty()) {
                    Text(stringResource(R.string.adr_subscriptions_empty), style = MaterialTheme.typography.bodySmall, color = HubColors.TextMuted)
                }
                subs.forEach { sub ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(checked = sub.enabled, onCheckedChange = { onToggle(sub.id, it) })
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(sub.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    if (sub.type == SubscriptionManager.Type.URL) "URL" else "CONTENT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HubColors.TextMuted
                                )
                                sub.createdAt.takeIf { it > 0 }?.let { ts ->
                                    Text(
                                        stringResource(R.string.adr_sub_added_at, formatSubDate(ts)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HubColors.TextMuted
                                    )
                                }
                            }
                        }
                        IconButtonSmall(Icons.Outlined.Visibility) { onPreview(sub) }
                        IconButtonSmall(Icons.Outlined.Restore) { onRestore(sub) }
                        IconButtonSmall(Icons.Outlined.Edit) { onEdit(sub) }
                        IconButtonSmall(Icons.Outlined.Share) { onShare(sub) }
                        IconButtonSmall(Icons.Outlined.Delete) { onDelete(sub.id) }
                    }
                    HorizontalDivider(color = HubColors.Border.copy(alpha = 0.5f))
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onApply) { Text(stringResource(R.string.adr_apply_subs)) }
                TextButton(onClick = onAdd) { Text(stringResource(R.string.adr_add_subscription)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.adr_close)) }
            }
        }
    )
}

/** 将订阅创建时间戳格式化为 yyyy-MM-dd HH:mm。 */
private fun formatSubDate(ts: Long): String =
    try {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
    } catch (_: Exception) {
        ts.toString()
    }

@Composable
private fun SubscriptionPreviewDialog(
    sub: SubscriptionManager.Subscription,
    vm: AdRemoverViewModel,
    onDismiss: () -> Unit
) {
    var content by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(sub.id) {
        loading = true
        vm.previewSubscriptionContent(sub) { result ->
            content = result
            loading = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adr_preview_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    sub.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(HubColors.SurfaceMuted)
                        .padding(10.dp)
                ) {
                    when {
                        loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        content == null -> Text(
                            stringResource(R.string.adr_add_fail),
                            style = MaterialTheme.typography.bodySmall,
                            color = HubColors.TextMuted
                        )
                        else -> SelectionContainer {
                            Text(
                                content!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = HubColors.TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.adr_close)) }
        }
    )
}

@Composable
private fun IconButtonSmall(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(HubColors.SurfaceMuted)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = HubColors.TextSecondary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean
) {
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adr_add_subscription)) },
        text = {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                placeholder = { Text(stringResource(R.string.adr_subscription_token_hint)) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall
            )
        },
        confirmButton = {
            TextButton(onClick = { if (onConfirm(token)) onDismiss() }) { Text(stringResource(R.string.adr_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.adr_cancel)) }
        }
    )
}

@Composable
private fun EditSubscriptionDialog(
    sub: SubscriptionManager.Subscription,
    onDismiss: () -> Unit,
    onSave: (SubscriptionManager.Subscription) -> Unit
) {
    var name by remember { mutableStateOf(sub.name) }
    var url by remember { mutableStateOf(sub.url) }
    var content by remember { mutableStateOf(sub.contentJson) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adr_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                if (sub.type == SubscriptionManager.Type.URL) {
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Content") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(sub.copy(name = name, url = url, contentJson = content))
            }) { Text(stringResource(R.string.adr_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.adr_cancel)) }
        }
    )
}

@Composable
private fun DoneDialog(result: AdRemoverResult, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adr_done_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatRow(stringResource(R.string.adr_size_orig), Format.formatSize(result.originalApkSize))
                StatRow(stringResource(R.string.adr_size_final), Format.formatSize(result.finalSize))
                StatRow(stringResource(R.string.adr_saved), Format.formatSize(result.savedBytes))
                StatRow(stringResource(R.string.adr_time), "${result.totalTimeMs} ms")
                Text(stringResource(R.string.adr_export_path), style = MaterialTheme.typography.labelMedium, color = HubColors.TextMuted)
                Text(result.exportDesc, style = MaterialTheme.typography.bodySmall, color = HubColors.Primary)
                result.reportPath?.let { p ->
                    Text(stringResource(R.string.adr_report_path), style = MaterialTheme.typography.labelMedium, color = HubColors.TextMuted)
                    Text(p, style = MaterialTheme.typography.bodySmall, color = HubColors.Primary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.adr_close)) }
        }
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = HubColors.Primary)
    }
}
