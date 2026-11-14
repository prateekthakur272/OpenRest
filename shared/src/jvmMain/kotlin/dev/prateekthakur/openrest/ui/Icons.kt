package dev.prateekthakur.openrest.ui

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
}

@Composable
fun Ico(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = LocalPal.current.muted, size: Dp = 16.dp) =
    Icon(icon, null, modifier.size(size), tint)
