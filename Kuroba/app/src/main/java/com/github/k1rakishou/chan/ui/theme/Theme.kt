package com.github.k1rakishou.chan.ui.theme

import android.content.Context

class Theme(
  override val context: Context,
  override val name: String,
  override val isLightTheme: Boolean,
  override val lightStatusBar: Boolean,
  override val lightNavBar: Boolean,
  override val accentColor: Int,
  override val primaryColor: Int,
  override val backColor: Int,
  override val errorColor: Int,
  override val textColorPrimary: Int,
  override val textColorSecondary: Int,
  override val textColorHint: Int,
  override val postHighlightedColor: Int,
  override val postSavedReplyColor: Int,
  override val postSubjectColor: Int,
  override val postDetailsColor: Int,
  override val postNameColor: Int,
  override val postInlineQuoteColor: Int,
  override val postQuoteColor: Int,
  override val postHighlightQuoteColor: Int,
  override val postLinkColor: Int,
  override val postSpoilerColor: Int,
  override val postSpoilerRevealTextColor: Int,
  override val postUnseenLabelColor: Int,
  override val dividerColor: Int,
  override val bookmarkCounterNotWatchingColor: Int,
  override val bookmarkCounterHasRepliesColor: Int,
  override val bookmarkCounterNormalColor: Int,
) : ChanTheme()