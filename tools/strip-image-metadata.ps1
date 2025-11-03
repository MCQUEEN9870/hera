param(
  [string]$Base = "d:\hera\hera pheri goods\attached_assets",
  [string[]]$Targets = @("images","vehicle category","animation")
)

function SizeKB($p){ [math]::Round((Get-Item $p).Length/1KB,2) }

# Build root paths to process
$roots = @()
foreach($t in $Targets){
  $dir = Join-Path $Base $t
  if(Test-Path $dir){ $roots += $dir } else { Write-Host "Skip (not found): $dir" -ForegroundColor Yellow }
}

if(-not $roots){ Write-Error "No target folders found. Check paths."; exit 1 }

$report = @()

## Resolve tool paths robustly (handles WinGet installs without PATH refresh)
# Try environment variables first, then common install locations, then PATH

function Resolve-ToolPath {
  param(
    [string]$Name,
    [string[]]$Candidates
  )
  foreach($c in $Candidates){
    if([string]::IsNullOrWhiteSpace($c)){ continue }
    if(Test-Path $c){ return (Resolve-Path $c).Path }
  }
  $cmd = Get-Command $Name -ErrorAction SilentlyContinue
  if($cmd){ return $cmd.Path }
  return $null
}

$exiftoolPathCandidates = @(
  $env:EXIFTOOL,
  "$env:LOCALAPPDATA\Programs\ExifTool\ExifTool.exe",
  "C:\\Program Files\\ExifTool\\exiftool.exe",
  "C:\\Program Files\\ExifTool\\exiftool(-k).exe",
  "C:\\Program Files (x86)\\ExifTool\\exiftool.exe",
  "C:\\Program Files (x86)\\ExifTool\\exiftool(-k).exe"
)
$exiftool = Resolve-ToolPath -Name 'exiftool' -Candidates $exiftoolPathCandidates

# Attempt to discover webpmux under WinGet packages if not on PATH
$webpmuxPathCandidates = @(
  $env:WEBPMUX,
  (Get-ChildItem -Path "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter webpmux.exe -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName),
  "C:\\Program Files\\libwebp\\bin\\webpmux.exe",
  "C:\\Program Files (x86)\\libwebp\\bin\\webpmux.exe"
)
$webpmux = Resolve-ToolPath -Name 'webpmux' -Candidates $webpmuxPathCandidates

# JPG/PNG — strip EXIF/XMP/ICC (lossless)
if($exiftool){
  Get-ChildItem -Path $roots -Recurse -Include *.jpg,*.jpeg,*.png | ForEach-Object {
    try {
      $before = SizeKB $_.FullName
      & $exiftool -q -all= -overwrite_original --icc_profile --xmp:all --iptc:all "$($_.FullName)" | Out-Null
      $after  = SizeKB $_.FullName
      $report += [pscustomobject]@{ File=$_.FullName; BeforeKB=$before; AfterKB=$after; SavedKB=($before-$after) }
    } catch {
      Write-Host "EXIF strip failed: $($_.FullName) $_" -ForegroundColor Yellow
    }
  }
} else {
  Write-Host "exiftool not found; skipping JPG/PNG stripping." -ForegroundColor Yellow
}

# WebP — strip EXIF/XMP/ICC (lossless)
if($webpmux){
  Get-ChildItem -Path $roots -Recurse -Filter *.webp | ForEach-Object {
    try {
      $before = SizeKB $_.FullName
      # Chain three strip operations (webpmux allows only one -strip per run)
      $src = $_.FullName
      $tmp1 = Join-Path $env:TEMP (([System.IO.Path]::GetRandomFileName()) + '.webp')
      & $webpmux -strip exif "$src" -o "$tmp1" 2>$null | Out-Null
  if(Test-Path $tmp1){ $in1 = $tmp1 } else { $in1 = $src }

      $tmp2 = Join-Path $env:TEMP (([System.IO.Path]::GetRandomFileName()) + '.webp')
      & $webpmux -strip xmp "$in1" -o "$tmp2" 2>$null | Out-Null
  if(Test-Path $tmp2){ $in2 = $tmp2 } else { $in2 = $in1 }

      $tmp3 = Join-Path $env:TEMP (([System.IO.Path]::GetRandomFileName()) + '.webp')
      & $webpmux -strip icc "$in2" -o "$tmp3" 2>$null | Out-Null
  if(Test-Path $tmp3){ $in3 = $tmp3 } else { $in3 = $in2 }

      if($in3 -ne $src -and (Test-Path $in3)){
        Move-Item -Force $in3 $src
      }
      foreach($t in @($tmp1,$tmp2,$tmp3)){
        if($t -and (Test-Path $t)) { Remove-Item -Force $t }
      }
      $after  = SizeKB $_.FullName
      $report += [pscustomobject]@{ File=$_.FullName; BeforeKB=$before; AfterKB=$after; SavedKB=($before-$after) }
    } catch {
      Write-Host "WebP strip failed: $($_.FullName) $_" -ForegroundColor Yellow
    }
  }
} else {
  Write-Host "webpmux not found; skipping WebP stripping." -ForegroundColor Yellow
}

# GIF — remove comments/extensions, keep animation
$gifsicle = Get-Command gifsicle -ErrorAction SilentlyContinue
if($gifsicle){
  Get-ChildItem -Path $roots -Recurse -Filter *.gif | ForEach-Object {
    try {
      $before = SizeKB $_.FullName
      $tmp = "$($_.FullName).tmp"
      & gifsicle --no-comments --no-extensions --careful "$($_.FullName)" -o "$tmp"
      if(Test-Path $tmp){ Move-Item -Force $tmp $_.FullName }
      $after  = SizeKB $_.FullName
      $report += [pscustomobject]@{ File=$_.FullName; BeforeKB=$before; AfterKB=$after; SavedKB=($before-$after) }
    } catch {
      Write-Host "GIF strip failed: $($_.FullName) $_" -ForegroundColor Yellow
    }
  }
} else {
  Write-Host "gifsicle not found; skipping GIF stripping." -ForegroundColor Yellow
}

# SVG — optimize and strip metadata
$svgo = Get-Command svgo -ErrorAction SilentlyContinue
if($svgo){
  Get-ChildItem -Path $roots -Recurse -Filter *.svg | ForEach-Object {
    try {
      $before = SizeKB $_.FullName
      & svgo --multipass "$($_.FullName)" -o "$($_.FullName)" | Out-Null
      $after  = SizeKB $_.FullName
      $report += [pscustomobject]@{ File=$_.FullName; BeforeKB=$before; AfterKB=$after; SavedKB=($before-$after) }
    } catch {
      Write-Host "SVG optimize failed: $($_.FullName) $_" -ForegroundColor Yellow
    }
  }
} else {
  Write-Host "svgo not found; skipping SVG optimization." -ForegroundColor Yellow
}

# Summary
if($report.Count -gt 0){
  $report | Sort-Object -Property SavedKB -Descending | Format-Table -AutoSize
  $totBefore = ($report | Measure-Object -Property BeforeKB -Sum).Sum
  $totAfter  = ($report | Measure-Object -Property AfterKB -Sum).Sum
  $totSaved  = [math]::Round(($totBefore - $totAfter),2)
  Write-Host "Total saved: $totSaved KB" -ForegroundColor Green
} else {
  Write-Host "No eligible files processed (or required tools missing)." -ForegroundColor Yellow
}
