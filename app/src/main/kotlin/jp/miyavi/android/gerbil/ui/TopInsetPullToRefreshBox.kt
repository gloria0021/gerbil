package jp.miyavi.android.gerbil.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * 一覧が TopAppBar の裏まで広がる画面用の PullToRefreshBox。
 * 更新インジケーターを [topPadding]（TopAppBar の下端）から出し、バーの裏に隠れないようにする。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopInsetPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    topPadding: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topPadding),
            )
        },
        content = content,
    )
}
