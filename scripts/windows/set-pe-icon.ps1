param(
    [Parameter(Mandatory = $true)][string]$Executable,
    [Parameter(Mandatory = $true)][string]$Png
)

$ErrorActionPreference = 'Stop'
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw 'Windows PE icon updates require Windows.' }
$Executable = [IO.Path]::GetFullPath($Executable)
$Png = [IO.Path]::GetFullPath($Png)
if (-not (Test-Path -LiteralPath $Executable -PathType Leaf)) { throw "Executable not found: $Executable" }
if (-not (Test-Path -LiteralPath $Png -PathType Leaf)) { throw "Icon PNG not found: $Png" }

Add-Type -AssemblyName System.Drawing
Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;

public static class PeResourceWriter {
    [DllImport("kernel32.dll", EntryPoint = "BeginUpdateResourceW", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern IntPtr BeginUpdateResource(string fileName, bool deleteExistingResources);

    [DllImport("kernel32.dll", EntryPoint = "UpdateResourceW", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool UpdateResource(IntPtr update, IntPtr type, IntPtr name, ushort language, byte[] data, uint size);

    [DllImport("kernel32.dll", EntryPoint = "EndUpdateResourceW", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool EndUpdateResource(IntPtr update, [MarshalAs(UnmanagedType.Bool)] bool discard);

    public static void Check(bool result, string operation) {
        if (!result) throw new Win32Exception(Marshal.GetLastWin32Error(), operation);
    }
}
'@

$sizes = @(16, 24, 32, 48, 64, 128, 256)
$iconImages = [Collections.Generic.List[byte[]]]::new()
$group = [IO.MemoryStream]::new()
$writer = [IO.BinaryWriter]::new($group)
$writer.Write([UInt16]0)
$writer.Write([UInt16]1)
$writer.Write([UInt16]$sizes.Count)
$source = [System.Drawing.Image]::FromFile($Png)
try {
    foreach ($size in $sizes) {
        $bitmap = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.Clear([System.Drawing.Color]::Transparent)
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $graphics.DrawImage($source, 0, 0, $size, $size)
            } finally { $graphics.Dispose() }
            $imageStream = [IO.MemoryStream]::new()
            try {
                $bitmap.Save($imageStream, [System.Drawing.Imaging.ImageFormat]::Png)
                $imageBytes = $imageStream.ToArray()
            } finally { $imageStream.Dispose() }
            $iconImages.Add($imageBytes)
            $dimension = if ($size -eq 256) { 0 } else { $size }
            $writer.Write([byte]$dimension)
            $writer.Write([byte]$dimension)
            $writer.Write([byte]0)
            $writer.Write([byte]0)
            $writer.Write([UInt16]1)
            $writer.Write([UInt16]32)
            $writer.Write([UInt32]$imageBytes.Length)
            $writer.Write([UInt16](101 + $iconImages.Count - 1))
        } finally { $bitmap.Dispose() }
    }
} finally {
    $source.Dispose()
    $writer.Dispose()
}

$update = [PeResourceWriter]::BeginUpdateResource($Executable, $false)
if ($update -eq [IntPtr]::Zero) { throw [System.ComponentModel.Win32Exception]::new([System.Runtime.InteropServices.Marshal]::GetLastWin32Error()) }
$committed = $false
try {
    for ($i = 0; $i -lt $iconImages.Count; $i++) {
        $bytes = $iconImages[$i]
        [PeResourceWriter]::Check([PeResourceWriter]::UpdateResource(
            $update, [IntPtr]3, [IntPtr](101 + $i), 0, $bytes, [UInt32]$bytes.Length), "Update RT_ICON resource $($i + 1)")
    }
    $groupBytes = $group.ToArray()
    [PeResourceWriter]::Check([PeResourceWriter]::UpdateResource(
        $update, [IntPtr]14, [IntPtr]1, 0, $groupBytes, [UInt32]$groupBytes.Length), 'Update RT_GROUP_ICON resource')
    [PeResourceWriter]::Check([PeResourceWriter]::EndUpdateResource($update, $false), 'Commit PE resources')
    $committed = $true
} finally {
    $group.Dispose()
    if (-not $committed) { [void][PeResourceWriter]::EndUpdateResource($update, $true) }
}

Write-Output "Embedded DSHCraft multi-size icon in $Executable"
