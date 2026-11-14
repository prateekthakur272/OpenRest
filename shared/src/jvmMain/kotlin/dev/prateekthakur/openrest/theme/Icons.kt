package dev.prateekthakur.openrest.theme

import dev.prateekthakur.openrest.core.http.*
import dev.prateekthakur.openrest.core.run.*

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One place that names every icon the app uses (Material Symbols, rounded). */
object Ic {
    val Logo = Icons.Rounded.Api
    val Close = Icons.Rounded.Close
    val Add = Icons.Rounded.Add
    val More = Icons.Rounded.MoreVert
    val Refresh = Icons.Rounded.Refresh
    val CollapseAll = Icons.Rounded.UnfoldLess
    val Up = Icons.Rounded.KeyboardArrowUp
    val Down = Icons.Rounded.KeyboardArrowDown
    val DropDown = Icons.Rounded.KeyboardArrowDown
    val Check = Icons.Rounded.Check
    val CheckCircle = Icons.Rounded.CheckCircle
    val Cancel = Icons.Rounded.Cancel
    val Skip = Icons.Rounded.SkipNext
    val Folder = Icons.Rounded.Folder
    val FolderOpen = Icons.Rounded.FolderOpen
    val Attach = Icons.Rounded.AttachFile
    val Text = Icons.Rounded.TextFields
    val Delete = Icons.Rounded.DeleteOutline
    val Copy = Icons.Rounded.ContentCopy
    val Download = Icons.Rounded.Download
    val Upload = Icons.Rounded.Upload
    val Save = Icons.Rounded.Save
    val Code = Icons.Rounded.Code
    val Send = Icons.Rounded.Send
    val Play = Icons.Rounded.PlayArrow
    val Workflow = Icons.Rounded.AccountTree
    val Stop = Icons.Rounded.Stop
    val Settings = Icons.Rounded.Settings
    val Light = Icons.Rounded.LightMode
    val Dark = Icons.Rounded.DarkMode
    val Cookie = Icons.Rounded.Cookie
    val Variables = Icons.Rounded.DataObject
    val Environments = Icons.Rounded.Layers
    val Sidebar = Icons.Rounded.ViewSidebar
    val SplitV = Icons.Rounded.HorizontalSplit
    val SplitH = Icons.Rounded.VerticalSplit
    val Compare = Icons.Rounded.Compare
    val Bulb = Icons.Rounded.Lightbulb
    val History = Icons.Rounded.History
    val Search = Icons.Rounded.Search
    val Edit = Icons.Rounded.Edit
    val Rename = Icons.Rounded.DriveFileRenameOutline
    val Clone = Icons.Rounded.FileCopy
    val Open = Icons.Rounded.OpenInNew
    val NewFile = Icons.Rounded.NoteAdd
    val NewFolder = Icons.Rounded.CreateNewFolder
    val Export = Icons.Rounded.IosShare
    val Import = Icons.Rounded.FileDownload
    val Warning = Icons.Rounded.WarningAmber
    val Error = Icons.Rounded.ErrorOutline
    val Command = Icons.Rounded.Bolt
    val MoveUp = Icons.Rounded.ArrowUpward
    val MoveDown = Icons.Rounded.ArrowDownward
    val Reveal = Icons.Rounded.FolderOpen
    val Tab = Icons.Rounded.Tab
    val Link = Icons.Rounded.Link
    val Drag = Icons.Rounded.DragIndicator
    val Person = Icons.Rounded.AccountCircle
    val Shield = Icons.Rounded.Security
    val Tune = Icons.Rounded.Tune
    val Eye = Icons.Rounded.Visibility
    val EyeOff = Icons.Rounded.VisibilityOff
    val Fingerprint = Icons.Rounded.Fingerprint
    val Info = Icons.Rounded.Info
    val Codec = Icons.Rounded.Transform
    val NoBody = Icons.Rounded.Block
    val FormBody = Icons.Rounded.ListAlt
    val Graph = Icons.Rounded.Hub
    val FmtJson = Icons.Rounded.DataObject
    val FmtHtml = Icons.Rounded.Html
    val FmtXml = Icons.Rounded.Code
    val FmtJs = Icons.Rounded.Javascript
    val FmtRaw = Icons.Rounded.Description
    val FmtHex = Icons.Rounded.Tag
    val FmtB64 = Icons.Rounded.Transform
    val ExpandAll = Icons.Rounded.UnfoldMore
    val FileGeneric = Icons.Rounded.InsertDriveFile
    val Pdf = Icons.Rounded.PictureAsPdf
    val Audio = Icons.Rounded.AudioFile
    val Video = Icons.Rounded.VideoFile
    val Image = Icons.Rounded.Image
    val Key = Icons.Rounded.VpnKey
    val Dash = Icons.Rounded.Remove

    /** The GitHub mark (not in the Material set), drawn from its 24x24 outline. */
    val GitHub: ImageVector by lazy {
        ImageVector.Builder("GitHub", 24.dp, 24.dp, 24f, 24f).addPath(
            androidx.compose.ui.graphics.vector.PathParser().parsePathString(
                "M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12",
            ).toNodes(),
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        ).build()
    }
}

@Composable
fun Ico(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = LocalPal.current.muted, size: Dp = 16.dp) =
    Icon(icon, null, modifier.size(size), tint)
