package com.github.k1rakishou.core_themes

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Typeface
import androidx.compose.material.ContentAlpha
import androidx.compose.material.TextFieldColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.manipulateColor

@SuppressLint("ResourceType")
abstract class ChanTheme {
  // Don't forget to update ThemeParser's gson when this class changes !!!
  abstract val name: String
  abstract val isLightTheme: Boolean
  abstract val lightStatusBar: Boolean
  abstract val lightNavBar: Boolean
  abstract val accentColor: Int
  abstract val primaryColor: Int
  abstract val backColor: Int
  abstract val errorColor: Int
  abstract val textColorPrimary: Int
  abstract val textColorSecondary: Int
  abstract val textColorHint: Int
  abstract val postHighlightedColor: Int
  abstract val postSavedReplyColor: Int
  abstract val postSubjectColor: Int
  abstract val postDetailsColor: Int
  abstract val postNameColor: Int
  abstract val postInlineQuoteColor: Int
  abstract val postQuoteColor: Int
  abstract val postHighlightQuoteColor: Int
  abstract val postLinkColor: Int
  abstract val postSpoilerColor: Int
  abstract val postSpoilerRevealTextColor: Int
  abstract val postUnseenLabelColor: Int
  abstract val dividerColor: Int
  abstract val bookmarkCounterNotWatchingColor: Int
  abstract val bookmarkCounterHasRepliesColor: Int
  abstract val bookmarkCounterNormalColor: Int

  val isDarkTheme: Boolean
    get() = !isLightTheme

  val isBackColorDark: Boolean
    get() = ThemeEngine.isDarkColor(backColor)

  val accentColorCompose by lazy { Color(accentColor) }
  val primaryColorCompose by lazy { Color(primaryColor) }
  val backColorCompose by lazy { Color(backColor) }
  val textColorPrimaryCompose by lazy { Color(textColorPrimary) }
  val errorColorCompose by lazy { Color(errorColor) }

  open val mainFont: Typeface = ROBOTO_MEDIUM

  val defaultColors by lazy { loadDefaultColors() }
  val defaultBoldTypeface by lazy { Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

  private fun loadDefaultColors(): DefaultColors {
    val controlNormalColor = if (isLightTheme) {
      CONTROL_LIGHT_COLOR
    } else {
      CONTROL_DARK_COLOR
    }

    val disabledControlAlpha = (255f * .4f).toInt()

    return DefaultColors(disabledControlAlpha, controlNormalColor)
  }

  fun getDisabledTextColor(color: Int): Int {
    return if (isLightTheme) {
      manipulateColor(color, 1.3f)
    } else {
      manipulateColor(color, .7f)
    }
  }

  fun getControlDisabledColor(color: Int): Int {
    return ColorStateList.valueOf(color)
      .withAlpha(defaultColors.disabledControlAlpha)
      .defaultColor
  }

  fun getColorByColorId(chanThemeColorId: ChanThemeColorId): Int {
    return when (chanThemeColorId) {
      ChanThemeColorId.PostSubjectColor -> postSubjectColor
      ChanThemeColorId.PostNameColor -> postNameColor
      ChanThemeColorId.AccentColor -> accentColor
      ChanThemeColorId.PostInlineQuoteColor -> postInlineQuoteColor
      ChanThemeColorId.PostQuoteColor -> postQuoteColor
      ChanThemeColorId.BackColorSecondary -> backColorSecondary()
      ChanThemeColorId.PostLinkColor -> postLinkColor
      ChanThemeColorId.TextColorPrimary -> textColorPrimary
    }
  }

  fun backColorSecondary(): Int {
    return manipulateColor(backColor, .7f)
  }

  @Composable
  fun textFieldColors(): TextFieldColors {
    val disabledAlpha = ContentAlpha.disabled

    val backColorDisabled = remember(key1 = backColorCompose) { backColorCompose.copy(alpha = disabledAlpha) }
    val iconColor = remember(key1 = backColorCompose) { backColorCompose.copy(alpha = TextFieldDefaults.IconOpacity) }

    return TextFieldDefaults.outlinedTextFieldColors(
      textColor = textColorPrimaryCompose,
      disabledTextColor = textColorPrimaryCompose.copy(ContentAlpha.disabled),
      backgroundColor = Color.Transparent,
      cursorColor = accentColorCompose,
      focusedBorderColor = accentColorCompose.copy(alpha = ContentAlpha.high),
      unfocusedBorderColor = accentColorCompose.copy(alpha = ContentAlpha.medium),
      disabledBorderColor = accentColorCompose.copy(alpha = ContentAlpha.disabled),
      focusedLabelColor = accentColorCompose.copy(alpha = ContentAlpha.high),
      unfocusedLabelColor = accentColorCompose.copy(ContentAlpha.medium),
      disabledLabelColor = accentColorCompose.copy(ContentAlpha.disabled),
      leadingIconColor = iconColor,
      disabledLeadingIconColor = iconColor.copy(alpha = ContentAlpha.disabled),
      errorLeadingIconColor = iconColor,
      trailingIconColor = iconColor,
      disabledTrailingIconColor = iconColor.copy(alpha = ContentAlpha.disabled),
      placeholderColor = backColorDisabled.copy(ContentAlpha.medium),
      disabledPlaceholderColor = backColorDisabled.copy(ContentAlpha.disabled),
      errorBorderColor = errorColorCompose,
      errorTrailingIconColor = errorColorCompose,
      errorCursorColor = errorColorCompose,
      errorLabelColor = errorColorCompose,
    )
  }

  data class DefaultColors(
    val disabledControlAlpha: Int,
    val controlNormalColor: Int
  ) {

    val disabledControlAlphaFloat: Float
      get() = disabledControlAlpha.toFloat() / MAX_ALPHA_FLOAT

  }

  companion object {
    private val ROBOTO_MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val ROBOTO_CONDENSED = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

    private const val CONTROL_LIGHT_COLOR = 0xFFAAAAAAL.toInt()
    private const val CONTROL_DARK_COLOR = 0xFFCCCCCCL.toInt()

    private const val MAX_ALPHA_FLOAT = 255f
  }
}