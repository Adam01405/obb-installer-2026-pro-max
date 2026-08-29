package com.aciderix.obbinstaller.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aciderix.obbinstaller.R
import com.shinegirls.apkadremovereditor.R as AdrR

/** 整合自 ApkAdRemoverEditor 关于页的功能特性文案（对应其 strings.xml 的 h_* 条目）。 */
private val TOOLBOX_FEATURES = listOf(
    AdrR.string.h_97349354,
    AdrR.string.h_7ff042ba,
    AdrR.string.h_de139607,
    AdrR.string.h_2c72ee88,
    AdrR.string.h_15c99f22,
    AdrR.string.h_b7aba98e,
    AdrR.string.h_4e63334a,
    AdrR.string.h_aa1a5948,
    AdrR.string.h_3e32cd6d,
    AdrR.string.h_2065c465,
    AdrR.string.h_c82adf9a,
    AdrR.string.h_d7c66a32,
    AdrR.string.h_437a2008,
    AdrR.string.h_cf9a62ca,
    AdrR.string.h_687f7144,
    AdrR.string.h_4e20a9f5,
    AdrR.string.h_7be1d8e4,
    AdrR.string.h_eb83ed6d,
    AdrR.string.h_ddafe46f,
    AdrR.string.h_0cad3ba2,
    AdrR.string.h_26f0c819,
    AdrR.string.h_c0a05071,
    AdrR.string.h_de184846
)

@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    val versionName = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(HubColors.Surface)
                .border(BorderStroke(1.dp, HubColors.Border), RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(HubColors.SurfaceMuted)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painter = painterResource(R.drawable.launcher_image))
                }
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.about_version, versionName),
                        style = MaterialTheme.typography.bodySmall,
                        color = HubColors.TextMuted
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(HubColors.Surface)
                .border(BorderStroke(1.dp, HubColors.Border), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.about_intro), style = MaterialTheme.typography.bodyLarge)
        }

        SectionCard(title = stringResource(R.string.about_features_title), icon = Icons.Outlined.AutoAwesome) {
            BulletItem(stringResource(R.string.about_feature_1))
            BulletItem(stringResource(R.string.about_feature_2))
            BulletItem(stringResource(R.string.about_feature_3))
            BulletItem(stringResource(R.string.about_feature_4))
            BulletItem(stringResource(R.string.about_feature_5))
        }

        // 工具箱 · APK 去广告（整合自 ApkAdRemoverEditor 关于页功能清单，折叠展示）
        CollapsibleSection(
            title = stringResource(R.string.about_toolbox_title),
            icon = Icons.Outlined.AutoAwesome,
            initiallyExpanded = false
        ) {
            TOOLBOX_FEATURES.forEach { resId ->
                BulletItem(stringResource(resId))
            }
        }

        SectionCard(title = stringResource(R.string.about_compat_title), icon = Icons.Outlined.Devices) {
            Text(stringResource(R.string.about_compat_body), style = MaterialTheme.typography.bodyMedium)
        }

        SectionCard(title = stringResource(R.string.about_changelog_title), icon = Icons.Outlined.Update) {
            BulletItem(stringResource(R.string.about_changelog_1))
            BulletItem(stringResource(R.string.about_changelog_2))
            BulletItem(stringResource(R.string.about_changelog_3))
            BulletItem(stringResource(R.string.about_changelog_4))
        }

        // 开源致谢 / 参考文档 / 隐私 / 免责 / 版权（整合自 ApkAdRemoverEditor 关于页，折叠展示）
        CollapsibleSection(
            title = stringResource(R.string.about_open_source_title),
            icon = Icons.Outlined.Code,
            initiallyExpanded = false
        ) {
            Text(stringResource(AdrR.string.about_open_source), style = MaterialTheme.typography.bodyMedium)
        }
        CollapsibleSection(
            title = stringResource(R.string.about_reference_title),
            icon = Icons.Outlined.Info,
            initiallyExpanded = false
        ) {
            Text(stringResource(AdrR.string.about_reference), style = MaterialTheme.typography.bodyMedium)
        }
        CollapsibleSection(
            title = stringResource(R.string.about_privacy_title),
            icon = Icons.Outlined.VerifiedUser,
            initiallyExpanded = false
        ) {
            Text(stringResource(AdrR.string.about_privacy), style = MaterialTheme.typography.bodyMedium)
        }
        CollapsibleSection(
            title = stringResource(R.string.about_disclaimer_title),
            icon = Icons.Outlined.WarningAmber,
            initiallyExpanded = false
        ) {
            Text(stringResource(AdrR.string.about_disclaimer), style = MaterialTheme.typography.bodyMedium)
        }

        CollapsibleSection(
            title = stringResource(R.string.about_copyright_title),
            icon = Icons.Outlined.Code,
            initiallyExpanded = false
        ) {
            BulletItem(stringResource(AdrR.string.about_copyright))
            BulletItem(stringResource(R.string.about_dev_author))
            val uriHandler = LocalUriHandler.current
            val repoUrl = stringResource(R.string.about_dev_repo_url)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HubColors.SurfaceMuted)
                    .clickable { uriHandler.openUri(repoUrl) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.about_dev_repo_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = HubColors.TextMuted
                    )
                    Text(
                        repoUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = HubColors.Primary
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun HelpScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            stringResource(R.string.help_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        FaqItem(stringResource(R.string.help_q1), stringResource(R.string.help_a1))
        FaqItem(stringResource(R.string.help_q2), stringResource(R.string.help_a2))
        FaqItem(stringResource(R.string.help_q3), stringResource(R.string.help_a3))
        FaqItem(stringResource(R.string.help_q4), stringResource(R.string.help_a4))
        FaqItem(stringResource(R.string.help_q5), stringResource(R.string.help_a5))

        // 工具箱 · APK 去广告使用说明（折叠展示）
        CollapsibleSection(
            title = stringResource(R.string.help_toolbox_title),
            icon = Icons.Outlined.Info,
            initiallyExpanded = false
        ) {
            FaqItem(stringResource(R.string.help_q_adr1), stringResource(R.string.help_a_adr1))
            FaqItem(stringResource(R.string.help_q_adr2), stringResource(R.string.help_a_adr2))
            FaqItem(stringResource(R.string.help_q_adr3), stringResource(R.string.help_a_adr3))
            FaqItem(stringResource(R.string.help_q_adr4), stringResource(R.string.help_a_adr4))
            FaqItem(stringResource(R.string.help_q_adr5), stringResource(R.string.help_a_adr5))
        }

        // 隐私政策 / 免责声明（整合自 ApkAdRemoverEditor 关于页，折叠展示）
        CollapsibleSection(
            title = stringResource(R.string.about_privacy_title),
            icon = Icons.Outlined.VerifiedUser,
            initiallyExpanded = false
        ) {
            Text(stringResource(AdrR.string.about_privacy), style = MaterialTheme.typography.bodyMedium)
        }
        CollapsibleSection(
            title = stringResource(R.string.about_disclaimer_title),
            icon = Icons.Outlined.WarningAmber,
            initiallyExpanded = false
        ) {
            Text(stringResource(AdrR.string.about_disclaimer), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    body: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HubColors.Surface)
            .border(BorderStroke(1.dp, HubColors.Border), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                        .background(HubColors.Primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = HubColors.Primary, modifier = Modifier.size(16.dp))
                }
                Text(title, style = MaterialTheme.typography.titleSmall, color = HubColors.Primary)
            }
            body()
        }
    }
}

/**
 * 可折叠内容卡片，展开 / 收起带垂直展开动画。
 */
@Composable
private fun CollapsibleSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    initiallyExpanded: Boolean = false,
    body: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HubColors.Surface)
            .border(BorderStroke(1.dp, HubColors.Border), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                        .background(HubColors.Primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = HubColors.Primary, modifier = Modifier.size(16.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = HubColors.Primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = HubColors.TextSecondary
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { body() }
            }
        }
    }
}

@Composable
private fun BulletItem(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier.size(6.dp).clip(CircleShape).background(HubColors.Primary)
                .offset(y = 8.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = HubColors.TextPrimary)
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HubColors.Surface)
            .border(BorderStroke(1.dp, HubColors.Border), RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(question, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = HubColors.TextSecondary
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Text(answer, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Image(painter: androidx.compose.ui.graphics.painter.Painter) {
    androidx.compose.foundation.Image(
        painter = painter,
        contentDescription = null,
        modifier = Modifier.fillMaxSize()
    )
}
