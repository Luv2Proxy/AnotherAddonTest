param(
    [string]$Root = (Get-Location).Path
)

function Create-DirectoryIndex {
    param(
        [string]$Directory
    )

    # First recursively process child directories
    Get-ChildItem -LiteralPath $Directory -Directory -Force |
        Where-Object { $_.Name -ne ".git" } |
        ForEach-Object {
            Create-DirectoryIndex -Directory $_.FullName
        }

    $IndexPath = Join-Path $Directory "index.txt"

    $Lines = @()

    # Header
    $RelativePath = $Directory.Substring($Root.Length).TrimStart('\')
    
    if ([string]::IsNullOrWhiteSpace($RelativePath)) {
        $RelativePath = "."
    }

    $Lines += "Directory: $RelativePath"
    $Lines += "=" * 80
    $Lines += ""

    # Direct files only
    $Files = Get-ChildItem -LiteralPath $Directory -File -Force |
        Where-Object { $_.Name -ne "index.txt" }

    if ($Files.Count -gt 0) {
        $Lines += "FILES"
        $Lines += "-----"

        foreach ($File in $Files) {
            $Lines += $File.Name
        }

        $Lines += ""
    }

    # Direct child directories only
    $Directories = Get-ChildItem -LiteralPath $Directory -Directory -Force |
        Where-Object { $_.Name -ne ".git" }

    if ($Directories.Count -gt 0) {
        $Lines += "DIRECTORIES"
        $Lines += "-----------"

        foreach ($ChildDirectory in $Directories) {
            $Lines += $ChildDirectory.Name
        }

        $Lines += ""
    }

    # Write index
    $Lines | Set-Content -LiteralPath $IndexPath -Encoding UTF8
}

Create-DirectoryIndex -Directory $Root

Write-Host "Directory indexes created successfully."
Write-Host "Root: $Root"