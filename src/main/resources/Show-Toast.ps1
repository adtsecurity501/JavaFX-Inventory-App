param ([Parameter(Mandatory = $true)]
    [string]$Title,

    [Parameter(Mandatory = $true)]
    [string]$Message)

try
{
    Add-Type -AssemblyName System.Windows.Forms
    $notifyIcon = New-Object System.Windows.Forms.NotifyIcon

    # --- THIS BLOCK IS NEW ---
    # Define the path to the custom icon.
    # The script's working directory will be the folder where the Java app is running.
    $iconPath = ".\notification_icon.ico"

    # Check if the custom icon exists.
    if (Test-Path $iconPath)
    {
        # If it exists, use it.
        $notifyIcon.Icon = New-Object System.Drawing.Icon $iconPath
    }
    else
    {
        # Otherwise, use a generic system icon as a fallback.
        $notifyIcon.Icon = [System.Drawing.Icon]::ExtractAssociatedIcon("$env:windir\System32\imageres.dll")
    }
    # --- END OF NEW BLOCK ---

    $notifyIcon.BalloonTipTitle = $Title
    $notifyIcon.BalloonTipText = $Message
    $notifyIcon.Visible = $true
    $notifyIcon.ShowBalloonTip(10000)

    Start-Sleep -Seconds 10
    $notifyIcon.Dispose()
}
catch
{
    Write-Error "Failed to create and show balloon tip notification. Error: $( $_.Exception.Message )"
}