package com.averycorp.prismtask.widget

import androidx.compose.ui.unit.sp
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Standardized typography scale for PrismTask Glance widgets.
 *
 * Sizes follow a consistent scale:
 * - Header/title: 16sp
 * - Body/task name: 13sp
 * - Caption/metadata: 11sp
 * - Badge/count: 10sp
 */
object WidgetTextStyles {
    fun header(color: ColorProvider) = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
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
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )

    fun timerLarge(color: ColorProvider) = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )

    fun timerSmall(color: ColorProvider) = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )
}
