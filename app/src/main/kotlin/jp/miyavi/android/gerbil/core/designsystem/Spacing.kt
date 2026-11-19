package jp.miyavi.android.gerbil.core.designsystem

import androidx.compose.ui.unit.dp

/** 余白は 4dp グリッドのこの定数だけを使う（AGENTS.md デザイン指南 4）。 */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** 余白以外の決まった寸法。 */
object Dimens {
    val hairline = 0.5.dp
    val minTouchTarget = 48.dp
    /** レスのヘッダ行の高さ（コンパクト表示。チップのタップ領域もこれに揃える）。 */
    val postHeaderHeight = 32.dp
    /** シンプル表示のレスとレスの間（各レスの下の余白）。モダン表示は Spacing.m。 */
    val postSpacing = 20.dp
    val unreadBar = 4.dp
    /** 未読の目印（角丸ピル）の高さ。 */
    val unreadBarHeight = 24.dp
    val scrollbarThin = 4.dp
    val scrollbarThick = 8.dp
    val scrollbarThumbLength = 48.dp
    val scrollbarTickLength = 12.dp
    val scrollbarTickThickness = 2.dp
    val maxReadingWidth = 720.dp
    val fontSizeControlWidth = 200.dp
    val colorControlsMaxHeight = 240.dp
    val floatingTabsMaxWidth = 360.dp
    /** 板一覧の掲示板のタブ1つの幅（収まるうちは同じ幅で中央に並べる）。 */
    val siteTabWidth = 120.dp
    /** 空のときの案内の丸いアイコンの大きさ。 */
    val emptyStateIcon = 72.dp
    /** 掲示板のタブの選択のピル（と押した反応）の高さ。 */
    val siteTabPillHeight = 32.dp
    val floatingTabsContentPadding = 96.dp
    val searchBarReservedHeight = 72.dp
    val imagePreviewWidth = 160.dp
    val imagePreviewHeight = 120.dp
    /** 要約の白い面の最低の高さ（生成中の進捗の行とプレースホルダーが収まる高さ）。 */
    val summaryFieldMinHeight = 168.dp
    val imageThumbSize = 64.dp
    val imageBlurRadius = 24.dp
}
