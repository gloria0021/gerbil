package jp.miyavi.android.gerbil.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.gerbil.R
import jp.miyavi.android.gerbil.core.database.SiteEntity
import jp.miyavi.android.gerbil.core.designsystem.Spacing
import jp.miyavi.android.gerbil.data.SiteAddResult
import jp.miyavi.android.gerbil.ui.BoardsTopAppBar

/**
 * 掲示板管理（スレ選択の ⋮ メニューから開く。めったに触らないので設定には置かない）。
 * 一覧には削除のボタンを置かず、掲示板を押して開く編集の中から、確かめてから削除する（うっかり消さないため）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitesScreen(
    sites: List<SiteEntity>,
    onBack: () -> Unit,
    onAddSite: (label: String, url: String, onResult: (SiteAddResult) -> Unit) -> Unit,
    onRenameSite: (id: String, label: String) -> Unit,
    onDeleteSite: (id: String) -> Unit,
) {
    val hazeState = rememberHazeState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            BoardsTopAppBar(
                hazeState = hazeState,
                title = { Text(stringResource(R.string.settings_sites), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_huge_arrow_left), contentDescription = stringResource(R.string.common_back_cd)) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().hazeSource(hazeState), contentPadding = padding) {
            items(sites, key = { it.id }) { site ->
                ListItem(
                    headlineContent = { Text(site.label) },
                    supportingContent = { Text(site.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.settings_site_rename)) { editing = site.id },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_site_add)) },
                    supportingContent = { Text(stringResource(R.string.settings_site_add_desc)) },
                    modifier = Modifier.clickable(role = Role.Button) { adding = true },
                )
            }
        }
    }
    if (adding) SiteAddDialog(onAddSite) { adding = false }
    sites.firstOrNull { it.id == editing }?.let { site ->
        SiteEditDialog(
            current = site.label,
            onDismiss = { editing = null },
            onRename = { onRenameSite(site.id, it); editing = null },
            onDelete = { editing = null; deleting = site.id },
        )
    }
    sites.firstOrNull { it.id == deleting }?.let { site ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.settings_site_delete_title, site.label)) },
            text = { Text(stringResource(R.string.settings_site_delete_body)) },
            confirmButton = { TextButton(onClick = { onDeleteSite(site.id); deleting = null }) { Text(stringResource(R.string.common_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** 掲示板の登録。URL を確かめるあいだはボタンを止め、だめなら理由を出す。 */
@Composable
private fun SiteAddDialog(
    onAddSite: (label: String, url: String, onResult: (SiteAddResult) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<SiteAddResult?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_site_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text(stringResource(R.string.settings_site_url)) },
                    placeholder = { Text(stringResource(R.string.settings_site_url_example)) },
                    supportingText = { Text(stringResource(R.string.settings_site_add_desc)) },
                    singleLine = true,
                    enabled = !checking,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.settings_site_label)) },
                    singleLine = true,
                    enabled = !checking,
                )
                val message = when (error) {
                    SiteAddResult.INVALID_URL -> R.string.settings_site_error_invalid
                    SiteAddResult.DUPLICATE -> R.string.settings_site_error_duplicate
                    SiteAddResult.FETCH_FAILED -> R.string.settings_site_error_fetch
                    SiteAddResult.EMPTY -> R.string.settings_site_error_empty
                    SiteAddResult.UNSUPPORTED -> R.string.settings_site_error_unsupported
                    SiteAddResult.ADDED, null -> null
                }
                if (checking) Text(stringResource(R.string.settings_site_checking), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (message != null) Text(stringResource(message), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !checking && url.isNotBlank(),
                onClick = {
                    checking = true
                    onAddSite(label, url) { result ->
                        checking = false
                        if (result == SiteAddResult.ADDED) onDismiss() else error = result
                    }
                },
            ) { Text(stringResource(R.string.settings_site_register)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun SiteEditDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var label by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_site_rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringResource(R.string.settings_site_label)) }, singleLine = true)
                // 削除は編集の中に置き、押すとさらに確かめる
                TextButton(onClick = onDelete) { Text(stringResource(R.string.settings_site_delete), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = label.isNotBlank(), onClick = { onRename(label) }) { Text(stringResource(R.string.common_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
