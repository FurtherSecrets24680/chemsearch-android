package com.furthersecrets.chemsearch.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.*
import com.adamglin.phosphoricons.fill.*
import com.adamglin.phosphoricons.regular.*

@Stable
sealed interface ChemIconSpec {
    data class Vector(val imageVector: ImageVector) : ChemIconSpec
}

fun ImageVector.asChemIcon(): ChemIconSpec = ChemIconSpec.Vector(this)

@Composable
fun ChemIcon(
    icon: ChemIconSpec,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    when (icon) {
        is ChemIconSpec.Vector -> Icon(
            imageVector = icon.imageVector,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
    }
}

object ChemAppIcons {
    val Atom = PhosphorIcons.Bold.Atom.asChemIcon()
    val Axis3d = PhosphorIcons.Bold.ThreeD.asChemIcon()
    val ArrowLeftRight = PhosphorIcons.Bold.ArrowsLeftRight.asChemIcon()
    val Calculator = PhosphorIcons.Bold.Calculator.asChemIcon()
    val Database = PhosphorIcons.Bold.Database.asChemIcon()
    val Dna = PhosphorIcons.Bold.Dna.asChemIcon()
    val Download = PhosphorIcons.Bold.DownloadSimple.asChemIcon()
    val Droplet = PhosphorIcons.Bold.Drop.asChemIcon()
    val Droplets = PhosphorIcons.Bold.DropHalf.asChemIcon()
    val FlaskConical = PhosphorIcons.Bold.Flask.asChemIcon()
    val GitCompareArrows = PhosphorIcons.Bold.GitDiff.asChemIcon()
    val History = PhosphorIcons.Bold.ClockCounterClockwise.asChemIcon()
    val Library = PhosphorIcons.Bold.Books.asChemIcon()
    val ListFilter = PhosphorIcons.Bold.Funnel.asChemIcon()
    val Network = PhosphorIcons.Bold.Network.asChemIcon()
    val Percent = PhosphorIcons.Bold.Percent.asChemIcon()
    val Scale = PhosphorIcons.Bold.Scales.asChemIcon()
    val Search = PhosphorIcons.Bold.MagnifyingGlass.asChemIcon()
    val SearchFilled = PhosphorIcons.Fill.MagnifyingGlass.asChemIcon()
    val Settings = PhosphorIcons.Bold.GearSix.asChemIcon()
    val SettingsFilled = PhosphorIcons.Fill.GearSix.asChemIcon()
    val SlidersHorizontal = PhosphorIcons.Bold.SlidersHorizontal.asChemIcon()
    val Star = PhosphorIcons.Fill.Star.asChemIcon()
    val TestTubes = PhosphorIcons.Bold.TestTube.asChemIcon()
    val Wind = PhosphorIcons.Bold.Wind.asChemIcon()
    val Wrench = PhosphorIcons.Bold.Wrench.asChemIcon()
    val WrenchFilled = PhosphorIcons.Fill.Wrench.asChemIcon()
    val LibraryFilled = PhosphorIcons.Fill.Books.asChemIcon()
    val HistoryFilled = PhosphorIcons.Fill.ClockCounterClockwise.asChemIcon()
}

object Icons {
    object Default {
        val Add = PhosphorIcons.Bold.Plus
        val Air = PhosphorIcons.Bold.Wind
        val ArrowDropDown = PhosphorIcons.Bold.CaretDown
        val AutoFixHigh = PhosphorIcons.Bold.MagicWand
        val Biotech = PhosphorIcons.Bold.Flask
        val Bolt = PhosphorIcons.Bold.Lightning
        val Brightness2 = PhosphorIcons.Bold.Moon
        val BugReport = PhosphorIcons.Bold.Bug
        val Cached = PhosphorIcons.Bold.ArrowsClockwise
        val Calculate = PhosphorIcons.Bold.Calculator
        val Cancel = PhosphorIcons.Bold.XCircle
        val Check = PhosphorIcons.Bold.Check
        val CheckCircle = PhosphorIcons.Bold.CheckCircle
        val ChevronRight = PhosphorIcons.Bold.CaretRight
        val Clear = PhosphorIcons.Bold.X
        val Close = PhosphorIcons.Bold.X
        val Code = PhosphorIcons.Bold.Code
        val ContentCopy = PhosphorIcons.Bold.Copy
        val DarkMode = PhosphorIcons.Bold.Moon
        val Delete = PhosphorIcons.Bold.Trash
        val DeleteOutline = PhosphorIcons.Bold.Trash
        val DeleteSweep = PhosphorIcons.Bold.Trash
        val Description = PhosphorIcons.Bold.FileText
        val Download = PhosphorIcons.Bold.DownloadSimple
        val DownloadDone = PhosphorIcons.Bold.CheckCircle
        val Error = PhosphorIcons.Bold.WarningCircle
        val FolderOpen = PhosphorIcons.Bold.FolderOpen
        val GridView = PhosphorIcons.Bold.GridFour
        val HealthAndSafety = PhosphorIcons.Bold.ShieldCheck
        val History = PhosphorIcons.Bold.ClockCounterClockwise
        val Hub = PhosphorIcons.Bold.ShareNetwork
        val Info = PhosphorIcons.Bold.Info
        val Key = PhosphorIcons.Bold.Key
        val KeyboardArrowDown = PhosphorIcons.Bold.CaretDown
        val KeyboardArrowUp = PhosphorIcons.Bold.CaretUp
        val LightMode = PhosphorIcons.Bold.Sun
        val Memory = PhosphorIcons.Bold.Cpu
        val Notifications = PhosphorIcons.Bold.Bell
        val NotificationsActive = PhosphorIcons.Bold.BellRinging
        val Palette = PhosphorIcons.Bold.Palette
        val Public = PhosphorIcons.Bold.Globe
        val RadioButtonChecked = PhosphorIcons.Fill.RadioButton
        val Refresh = PhosphorIcons.Bold.ArrowClockwise
        val RestartAlt = PhosphorIcons.Bold.ArrowCounterClockwise
        val Scale = PhosphorIcons.Bold.Scales
        val Science = PhosphorIcons.Bold.Flask
        val Search = PhosphorIcons.Bold.MagnifyingGlass
        val SearchOff = PhosphorIcons.Bold.MagnifyingGlassMinus
        val Settings = PhosphorIcons.Bold.GearSix
        val Share = PhosphorIcons.Bold.ShareNetwork
        val SmartToy = PhosphorIcons.Bold.Robot
        val Star = PhosphorIcons.Fill.Star
        val StarBorder = PhosphorIcons.Regular.Star
        val Storage = PhosphorIcons.Bold.Database
        val SwapHoriz = PhosphorIcons.Bold.ArrowsLeftRight
        val SystemUpdate = PhosphorIcons.Bold.DownloadSimple
        val Terminal = PhosphorIcons.Bold.Terminal
        val Tune = PhosphorIcons.Bold.SlidersHorizontal
        val ViewInAr = PhosphorIcons.Bold.CubeFocus
        val Visibility = PhosphorIcons.Bold.Eye
        val VisibilityOff = PhosphorIcons.Bold.EyeSlash
        val Warning = PhosphorIcons.Bold.Warning
        val WaterDrop = PhosphorIcons.Bold.Drop
        val WavingHand = PhosphorIcons.Bold.HandWaving
    }

    object AutoMirrored {
        object Filled {
            val ArrowBack = PhosphorIcons.Bold.ArrowLeft
            val ArrowForward = PhosphorIcons.Bold.ArrowRight
            val Backspace = PhosphorIcons.Bold.Backspace
            val CompareArrows = PhosphorIcons.Bold.ArrowsLeftRight
            val Feed = PhosphorIcons.Bold.Rss
            val HelpOutline = PhosphorIcons.Bold.Question
            val InsertDriveFile = PhosphorIcons.Bold.File
            val MenuBook = PhosphorIcons.Bold.BookOpen
            val OpenInNew = PhosphorIcons.Bold.ArrowSquareOut
            val ShowChart = PhosphorIcons.Bold.ChartLineUp
            val ViewList = PhosphorIcons.Bold.ListBullets
        }
    }
}
