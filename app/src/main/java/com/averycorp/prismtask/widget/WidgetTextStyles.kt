package com.averycorp.prismtask.widget

import androidx.compose.ui.unit.sp
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Standardized typography scale for PrismTask Glance widgets.
 *
 * Sizes mirror the JSX widget mockup tokens (T.header / T.body / T.caption /
 * T.badge / T.timerLarge / T.scoreLarge) so on-device rendering matches the
 * design source of truth.
 */
object WidgetTextStyles {
    fun header(color: ColorProvider) = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = color
    )

    fun body(color: ColorProvider) = TextStyle(
        fontSize = 13.sp,
        color = color
    )

    fun bodyBold(color: ColorProvider) = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )

    fun caption(color: ColorProvider) = TextStyle(
        fontSize = 11.sp,
        color = color
    )

    fun captionMedium(color: ColorProvider) = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = color
    )

    fun badge(color: ColorProvider) = TextStyle(
        fontSize = 10.sp,
        color = color
    )

    fun badgeBold(color: ColorProvider) = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )

    fun scoreLarge(color: ColorProvider) = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )

    fun timerLarge(color: ColorProvider) = TextStyle(
        fontSize = 38.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )

    fun timerSmall(color: ColorProvider) = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )
}
