package com.nexusguard.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object NexusIcons {

    val Home: ImageVector by lazy {
        ImageVector.Builder(
            name = "Home",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 5.69f)
                lineToRelative(5f, 4.5f)
                verticalLineTo(18f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-6f)
                horizontalLineTo(9f)
                verticalLineToRelative(6f)
                horizontalLineTo(7f)
                verticalLineToRelative(-7.81f)
                lineToRelative(5f, -4.5f)
                moveTo(12f, 3f)
                lineTo(2f, 12f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(8f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(-6f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(-8f)
                horizontalLineToRelative(3f)
                lineTo(12f, 3f)
                close()
            }
        }.build()
    }

    val Delete: ImageVector by lazy {
        ImageVector.Builder(
            name = "Delete",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(16f, 9f)
                verticalLineToRelative(10f)
                horizontalLineTo(8f)
                verticalLineTo(9f)
                horizontalLineToRelative(8f)
                moveToRelative(-1.5f, -6f)
                horizontalLineToRelative(-5f)
                lineToRelative(-1f, 1f)
                horizontalLineTo(5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(14f)
                verticalLineTo(4f)
                horizontalLineToRelative(-3.5f)
                lineToRelative(-1f, -1f)
                close()
                moveTo(18f, 7f)
                horizontalLineTo(6f)
                verticalLineToRelative(12f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(8f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                close()
            }
        }.build()
    }

    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "Settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(19.14f, 12.94f)
                curveToRelative(0.04f, -0.3f, 0.06f, -0.61f, 0.06f, -0.94f)
                curveToRelative(0f, -0.32f, -0.02f, -0.64f, -0.07f, -0.94f)
                lineToRelative(2.03f, -1.58f)
                curveToRelative(0.18f, -0.14f, 0.23f, -0.41f, 0.12f, -0.61f)
                lineToRelative(-1.92f, -3.32f)
                curveToRelative(-0.12f, -0.22f, -0.37f, -0.29f, -0.59f, -0.22f)
                lineToRelative(-2.39f, 0.96f)
                curveToRelative(-0.5f, -0.38f, -1.03f, -0.7f, -1.62f, -0.94f)
                lineTo(14.4f, 2.81f)
                curveToRelative(-0.04f, -0.24f, -0.24f, -0.41f, -0.48f, -0.41f)
                horizontalLineToRelative(-3.84f)
                curveToRelative(-0.24f, 0f, -0.43f, 0.17f, -0.47f, 0.41f)
                lineTo(9.25f, 5.35f)
                curveTo(8.66f, 5.59f, 8.12f, 5.92f, 7.63f, 6.29f)
                lineTo(5.24f, 5.33f)
                curveToRelative(-0.22f, -0.08f, -0.47f, 0f, -0.59f, 0.22f)
                lineTo(2.74f, 8.87f)
                curveTo(2.62f, 9.08f, 2.66f, 9.34f, 2.86f, 9.48f)
                lineToRelative(2.03f, 1.58f)
                curveTo(4.84f, 11.36f, 4.8f, 11.69f, 4.8f, 12f)
                curveToRelative(0f, 0.31f, 0.02f, 0.65f, 0.07f, 0.94f)
                lineToRelative(-2.03f, 1.58f)
                curveToRelative(-0.18f, 0.14f, -0.23f, 0.41f, -0.12f, 0.61f)
                lineToRelative(1.92f, 3.32f)
                curveToRelative(0.12f, 0.22f, 0.37f, 0.29f, 0.59f, 0.22f)
                lineToRelative(2.39f, -0.96f)
                curveToRelative(0.5f, 0.38f, 1.03f, 0.7f, 1.62f, 0.94f)
                lineToRelative(0.36f, 2.54f)
                curveToRelative(0.05f, 0.24f, 0.24f, 0.41f, 0.48f, 0.41f)
                horizontalLineToRelative(3.84f)
                curveToRelative(0.24f, 0f, 0.44f, -0.17f, 0.47f, -0.41f)
                lineToRelative(0.36f, -2.54f)
                curveToRelative(0.59f, -0.24f, 1.13f, -0.56f, 1.62f, -0.94f)
                lineToRelative(2.39f, 0.96f)
                curveToRelative(0.22f, 0.08f, 0.47f, 0f, 0.59f, -0.22f)
                lineToRelative(1.92f, -3.32f)
                curveToRelative(0.12f, -0.22f, 0.07f, -0.47f, -0.12f, -0.61f)
                lineTo(19.14f, 12.94f)
                close()
                moveTo(12f, 15.6f)
                curveToRelative(-1.98f, 0f, -3.6f, -1.62f, -3.6f, -3.6f)
                reflectiveCurveToRelative(1.62f, -3.6f, 3.6f, -3.6f)
                reflectiveCurveToRelative(3.6f, 1.62f, 3.6f, 3.6f)
                reflectiveCurveTo(13.98f, 15.6f, 12f, 15.6f)
                close()
            }
        }.build()
    }

    val Refresh: ImageVector by lazy {
        ImageVector.Builder(
            name = "Refresh",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(17.65f, 6.35f)
                curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
                curveToRelative(-4.42f, 0f, -7.99f, 3.58f, -7.99f, 8f)
                reflectiveCurveToRelative(3.57f, 8f, 7.99f, 8f)
                curveToRelative(3.73f, 0f, 6.84f, -2.55f, 7.73f, -6f)
                horizontalLineToRelative(-2.08f)
                curveToRelative(-0.82f, 2.33f, -3.04f, 4f, -5.65f, 4f)
                curveToRelative(-3.31f, 0f, -6f, -2.69f, -6f, -6f)
                reflectiveCurveToRelative(2.69f, -6f, 6f, -6f)
                curveToRelative(1.66f, 0f, 3.14f, 0.69f, 4.22f, 1.78f)
                lineTo(13f, 11f)
                horizontalLineToRelative(7f)
                verticalLineTo(4f)
                lineTo(17.65f, 6.35f)
                close()
            }
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "Check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(9f, 16.17f)
                lineTo(4.83f, 12f)
                lineToRelative(-1.42f, 1.41f)
                lineTo(9f, 19f)
                lineTo(21f, 7f)
                lineToRelative(-1.41f, -1.41f)
                close()
            }
        }.build()
    }

    val CheckCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "CheckCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
                reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
                reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
                reflectiveCurveToRelative(-3.59f, 8f, -8f, 8f)
                close()
                moveTo(16.59f, 7.58f)
                lineTo(10f, 14.17f)
                lineToRelative(-2.59f, -2.58f)
                lineTo(6f, 13f)
                lineToRelative(4f, 4f)
                lineToRelative(8f, -8f)
                close()
            }
        }.build()
    }

    val Lock: ImageVector by lazy {
        ImageVector.Builder(
            name = "Lock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(18f, 8f)
                horizontalLineToRelative(-1f)
                verticalLineTo(6f)
                curveToRelative(0f, -2.76f, -2.24f, -5f, -5f, -5f)
                reflectiveCurveTo(7f, 3.24f, 7f, 6f)
                verticalLineToRelative(2f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(10f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(12f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(10f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(9f, 6f)
                curveToRelative(0f, -1.66f, 1.34f, -3f, 3f, -3f)
                reflectiveCurveToRelative(3f, 1.34f, 3f, 3f)
                verticalLineToRelative(2f)
                horizontalLineTo(9f)
                verticalLineTo(6f)
                close()
                moveTo(18f, 20f)
                horizontalLineTo(6f)
                verticalLineTo(10f)
                horizontalLineToRelative(12f)
                verticalLineTo(20f)
                close()
                moveTo(12f, 17f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                close()
            }
        }.build()
    }

    val Info: ImageVector by lazy {
        ImageVector.Builder(
            name = "Info",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(11f, 7f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-2f)
                close()
                moveTo(11f, 11f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(-2f)
                close()
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
                reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
                reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
                reflectiveCurveToRelative(-3.59f, 8f, -8f, 8f)
                close()
            }
        }.build()
    }

    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowBack",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(20f, 11f)
                horizontalLineTo(7.83f)
                lineToRelative(5.59f, -5.59f)
                lineTo(12f, 4f)
                lineToRelative(-8f, 8f)
                lineToRelative(8f, 8f)
                lineToRelative(1.41f, -1.41f)
                lineTo(7.83f, 13f)
                horizontalLineTo(20f)
                verticalLineToRelative(-2f)
                close()
            }
        }.build()
    }

    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(10f, 6f)
                lineTo(8.59f, 7.41f)
                lineTo(13.17f, 12f)
                lineToRelative(-4.58f, 4.59f)
                lineTo(10f, 18f)
                lineToRelative(6f, -6f)
                close()
            }
        }.build()
    }

    val SportsEsports: ImageVector by lazy {
        ImageVector.Builder(
            name = "SportsEsports",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(21.58f, 16.09f)
                lineToRelative(-1.09f, -7.66f)
                curveTo(20.21f, 6.46f, 18.52f, 5f, 16.53f, 5f)
                horizontalLineTo(7.47f)
                curveTo(5.48f, 5f, 3.79f, 6.46f, 3.51f, 8.43f)
                lineToRelative(-1.09f, 7.66f)
                curveTo(2.2f, 17.63f, 3.39f, 19f, 4.94f, 19f)
                horizontalLineToRelative(0f)
                curveToRelative(0.68f, 0f, 1.32f, -0.27f, 1.8f, -0.75f)
                lineTo(9f, 16f)
                horizontalLineToRelative(6f)
                lineToRelative(2.25f, 2.25f)
                curveToRelative(0.48f, 0.48f, 1.13f, 0.75f, 1.8f, 0.75f)
                horizontalLineToRelative(0f)
                curveTo(20.61f, 19f, 21.8f, 17.63f, 21.58f, 16.09f)
                close()
                moveTo(11f, 11f)
                horizontalLineTo(9f)
                verticalLineToRelative(2f)
                horizontalLineTo(8f)
                verticalLineToRelative(-2f)
                horizontalLineTo(6f)
                verticalLineToRelative(-1f)
                horizontalLineToRelative(2f)
                verticalLineTo(8f)
                horizontalLineToRelative(1f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineTo(11f)
                close()
                moveTo(15f, 10f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
                curveTo(16f, 9.55f, 15.55f, 10f, 15f, 10f)
                close()
                moveTo(17f, 13f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
                curveTo(18f, 12.55f, 17.55f, 13f, 17f, 13f)
                close()
            }
        }.build()
    }

    val BatteryChargingFull: ImageVector by lazy {
        ImageVector.Builder(
            name = "BatteryChargingFull",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(15.67f, 4f)
                horizontalLineTo(14f)
                verticalLineTo(2f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(2f)
                horizontalLineTo(8.33f)
                curveTo(7.6f, 4f, 7f, 4.6f, 7f, 5.33f)
                verticalLineToRelative(15.33f)
                curveTo(7f, 21.4f, 7.6f, 22f, 8.33f, 22f)
                horizontalLineToRelative(7.33f)
                curveToRelative(0.74f, 0f, 1.34f, -0.6f, 1.34f, -1.33f)
                verticalLineTo(5.33f)
                curveTo(17f, 4.6f, 16.4f, 4f, 15.67f, 4f)
                close()
                moveTo(11f, 20f)
                verticalLineToRelative(-5.5f)
                horizontalLineTo(9f)
                lineTo(13f, 7f)
                verticalLineToRelative(5.5f)
                horizontalLineToRelative(2f)
                lineTo(11f, 20f)
                close()
            }
        }.build()
    }

    val PictureAsPdf: ImageVector by lazy {
        ImageVector.Builder(
            name = "PictureAsPdf",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(20f, 2f)
                horizontalLineTo(8f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(12f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(12f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(4f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(20f, 16f)
                horizontalLineTo(8f)
                verticalLineTo(4f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(12f)
                close()
                moveTo(4f, 6f)
                horizontalLineTo(2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                horizontalLineTo(4f)
                verticalLineTo(6f)
                close()
            }
        }.build()
    }

    val RestoreFromTrash: ImageVector by lazy {
        ImageVector.Builder(
            name = "RestoreFromTrash",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(19f, 4f)
                horizontalLineToRelative(-3.5f)
                lineToRelative(-1f, -1f)
                horizontalLineToRelative(-5f)
                lineToRelative(-1f, 1f)
                horizontalLineTo(5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(14f)
                verticalLineTo(4f)
                close()
                moveTo(6f, 7f)
                verticalLineToRelative(12f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(8f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                horizontalLineTo(6f)
                close()
                moveTo(14f, 14f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(-4f)
                horizontalLineTo(8f)
                lineToRelative(4f, -4f)
                lineToRelative(4f, 4f)
                horizontalLineToRelative(-2f)
                close()
            }
        }.build()
    }

    val Visibility: ImageVector by lazy {
        ImageVector.Builder(
            name = "Visibility",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2f, 12f)
                curveTo(5f, 7f, 8.5f, 5f, 12f, 5f)
                curveToRelative(3.5f, 0f, 7f, 2f, 10f, 7f)
                curveToRelative(-3f, 5f, -6.5f, 7f, -10f, 7f)
                curveToRelative(-3.5f, 0f, -7f, -2f, -10f, -7f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 9f)
                curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
                reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
                reflectiveCurveToRelative(3f, -1.34f, 3f, -3f)
                reflectiveCurveToRelative(-1.34f, -3f, -3f, -3f)
                close()
            }
        }.build()
    }

    val Speed: ImageVector by lazy {
        ImageVector.Builder(
            name = "Speed",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4f, 17f)
                arcTo(8f, 8f, 0f, true, true, 20f, 17f)
                moveTo(12f, 14f)
                lineTo(16f, 9f)
            }
        }.build()
    }

    val History: ImageVector by lazy {
        ImageVector.Builder(
            name = "History",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 12f)
                arcTo(8f, 8f, 0f, true, true, 6.3f, 17.6f)
                moveTo(12f, 8f)
                verticalLineTo(12f)
                lineTo(15f, 14f)
            }
        }.build()
    }

    // Fase 4: Ícones das categorias com traço de 1,5 dp e cantos arredondados
    val CategoryCache: ImageVector by lazy {
        strokeIcon("CategoryCache") {
            moveTo(4f, 7f)
            lineTo(12f, 3f)
            lineTo(20f, 7f)
            lineTo(12f, 11f)
            close()
            moveTo(4f, 12f)
            lineTo(12f, 16f)
            lineTo(20f, 12f)
            moveTo(4f, 17f)
            lineTo(12f, 21f)
            lineTo(20f, 17f)
        }
    }

    val CategoryApk: ImageVector by lazy {
        strokeIcon("CategoryApk") {
            moveTo(7f, 10f)
            arcTo(5f, 5f, 0f, false, true, 17f, 10f)
            verticalLineTo(18f)
            curveTo(17f, 19.1f, 16.1f, 20f, 15f, 20f)
            horizontalLineTo(9f)
            curveTo(7.9f, 20f, 7f, 19.1f, 7f, 18f)
            close()
            moveTo(9f, 5.5f)
            lineTo(7.5f, 3.5f)
            moveTo(15f, 5.5f)
            lineTo(16.5f, 3.5f)
            moveTo(10f, 9f)
            horizontalLineTo(10.1f)
            moveTo(14f, 9f)
            horizontalLineTo(14.1f)
        }
    }

    val CategoryJunk: ImageVector by lazy {
        strokeIcon("CategoryJunk") {
            moveTo(4f, 7f)
            horizontalLineTo(20f)
            moveTo(9f, 7f)
            verticalLineTo(4f)
            horizontalLineTo(15f)
            verticalLineTo(7f)
            moveTo(6f, 7f)
            lineTo(7f, 20f)
            horizontalLineTo(17f)
            lineTo(18f, 7f)
            moveTo(10f, 11f)
            verticalLineTo(16f)
            moveTo(14f, 11f)
            verticalLineTo(16f)
        }
    }

    val CategoryDuplicates: ImageVector by lazy {
        strokeIcon("CategoryDuplicates") {
            moveTo(8f, 8f)
            horizontalLineTo(19f)
            verticalLineTo(19f)
            horizontalLineTo(8f)
            close()
            moveTo(16f, 8f)
            verticalLineTo(5f)
            horizontalLineTo(5f)
            verticalLineTo(16f)
            horizontalLineTo(8f)
        }
    }

    val CategoryEmptyFolder: ImageVector by lazy {
        strokeIcon("CategoryEmptyFolder") {
            moveTo(3f, 6f)
            horizontalLineTo(9f)
            lineTo(11f, 8f)
            horizontalLineTo(21f)
            verticalLineTo(19f)
            horizontalLineTo(3f)
            close()
            moveTo(9f, 13.5f)
            horizontalLineTo(15f)
        }
    }

    val CategoryLeftovers: ImageVector by lazy {
        strokeIcon("CategoryLeftovers") {
            moveTo(12f, 3f)
            lineTo(20f, 7.5f)
            verticalLineTo(16.5f)
            lineTo(12f, 21f)
            lineTo(4f, 16.5f)
            verticalLineTo(7.5f)
            close()
            moveTo(12f, 12f)
            lineTo(20f, 7.5f)
            moveTo(12f, 12f)
            lineTo(4f, 7.5f)
            moveTo(12f, 12f)
            verticalLineTo(21f)
        }
    }

    val CategorySimilarPhotos: ImageVector by lazy {
        strokeIcon("CategorySimilarPhotos") {
            moveTo(4f, 6f)
            horizontalLineTo(16f)
            verticalLineTo(16f)
            horizontalLineTo(4f)
            close()
            moveTo(8f, 16f)
            verticalLineTo(19f)
            horizontalLineTo(20f)
            verticalLineTo(9f)
            horizontalLineTo(16f)
            moveTo(5.5f, 13.5f)
            lineTo(8.5f, 10.5f)
            lineTo(11f, 13f)
            lineTo(13.5f, 11f)
            lineTo(15.5f, 13.5f)
        }
    }

    val CategoryBlurryPhotos: ImageVector by lazy {
        strokeIcon("CategoryBlurryPhotos") {
            moveTo(12f, 4f)
            curveTo(8f, 9f, 6f, 12f, 6f, 15f)
            arcTo(6f, 6f, 0f, false, false, 18f, 15f)
            curveTo(18f, 12f, 16f, 9f, 12f, 4f)
            close()
            moveTo(9.5f, 15.5f)
            arcTo(2.5f, 2.5f, 0f, false, false, 12f, 18f)
        }
    }

    val CategoryScreenshots: ImageVector by lazy {
        strokeIcon("CategoryScreenshots") {
            moveTo(7f, 3f)
            horizontalLineTo(17f)
            curveTo(18.1f, 3f, 19f, 3.9f, 19f, 5f)
            verticalLineTo(19f)
            curveTo(19f, 20.1f, 18.1f, 21f, 17f, 21f)
            horizontalLineTo(7f)
            curveTo(5.9f, 21f, 5f, 20.1f, 5f, 19f)
            verticalLineTo(5f)
            curveTo(5f, 3.9f, 5.9f, 3f, 7f, 3f)
            close()
            moveTo(9f, 8f)
            horizontalLineTo(11f)
            moveTo(9f, 8f)
            verticalLineTo(10f)
            moveTo(15f, 16f)
            horizontalLineTo(13f)
            moveTo(15f, 16f)
            verticalLineTo(14f)
        }
    }

    val CategoryDownloads: ImageVector by lazy {
        strokeIcon("CategoryDownloads") {
            moveTo(12f, 4f)
            verticalLineTo(15f)
            moveTo(8f, 11f)
            lineTo(12f, 15f)
            lineTo(16f, 11f)
            moveTo(5f, 19f)
            horizontalLineTo(19f)
        }
    }

    val CategoryLargeFiles: ImageVector by lazy {
        strokeIcon("CategoryLargeFiles") {
            moveTo(6f, 3f)
            horizontalLineTo(14f)
            lineTo(19f, 8f)
            verticalLineTo(20f)
            horizontalLineTo(6f)
            close()
            moveTo(14f, 3f)
            verticalLineTo(8f)
            horizontalLineTo(19f)
            moveTo(9f, 14f)
            horizontalLineTo(16f)
            moveTo(9f, 17f)
            horizontalLineTo(14f)
        }
    }

    val CategoryMessaging: ImageVector by lazy {
        strokeIcon("CategoryMessaging") {
            moveTo(4f, 5f)
            horizontalLineTo(20f)
            verticalLineTo(16f)
            horizontalLineTo(8f)
            lineTo(4f, 20f)
            close()
            moveTo(8f, 9.5f)
            horizontalLineTo(16f)
            moveTo(8f, 12.5f)
            horizontalLineTo(13f)
        }
    }

    val AppsGrid: ImageVector by lazy {
        strokeIcon("AppsGrid") {
            moveTo(4f, 4f)
            horizontalLineTo(10f)
            verticalLineTo(10f)
            horizontalLineTo(4f)
            close()
            moveTo(14f, 4f)
            horizontalLineTo(20f)
            verticalLineTo(10f)
            horizontalLineTo(14f)
            close()
            moveTo(4f, 14f)
            horizontalLineTo(10f)
            verticalLineTo(20f)
            horizontalLineTo(4f)
            close()
            moveTo(14f, 14f)
            horizontalLineTo(20f)
            verticalLineTo(20f)
            horizontalLineTo(14f)
            close()
        }
    }

    val EmptyStateShield: ImageVector by lazy {
        strokeIcon("EmptyStateShield") {
            moveTo(12f, 3f)
            lineTo(19f, 6f)
            verticalLineTo(11.5f)
            curveTo(19f, 16.2f, 15.9f, 19.6f, 12f, 21f)
            curveTo(8.1f, 19.6f, 5f, 16.2f, 5f, 11.5f)
            verticalLineTo(6f)
            close()
            moveTo(9f, 12f)
            lineTo(11f, 14f)
            lineTo(15.5f, 9.5f)
        }
    }

    private fun strokeIcon(
        name: String,
        builder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit
    ): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = builder
            )
        }.build()
    }
}
