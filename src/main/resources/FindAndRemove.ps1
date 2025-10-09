#Requires -Modules ActiveDirectory

param ([Parameter(Mandatory = $true, ParameterSetName = 'Search')]
    [switch]$search,

    [Parameter(Mandatory = $true, ParameterSetName = 'Remove')]
    [switch]$remove,

    [Parameter(Mandatory = $true)]
    [string]$targets,

    [Parameter(ParameterSetName = 'Search')]
    [ValidateSet('AD', 'SCCM', 'Both')]
    [string]$source = 'Both')

# --- Configuration ---
$SCCMServer = "PKVWMEMCM01"
$SCCMSiteCode = "KNX"
$ADServer = "ADT.com"

# --- Functions ---
function Write-Log
{
    param($Message, $Type = "INFO")
    Write-Host "LOG:$Type`:$Message"
}

function Write-Result
{
    param($Source, $SearchTerm, $ComputerName, $Status)
    Write-Host "RESULT:$Source,$SearchTerm,$ComputerName,$Status"
}

function Invoke-Search
{
    $searchTerms = $targets -split ',' | Where-Object { $_ -ne "" }
    Write-Log "Search starting for $( $searchTerms.Count ) term(s) in source: $source"

    if (($source -eq 'AD' -or $source -eq 'Both') -and -not (Get-Module -ListAvailable -Name ActiveDirectory))
    {
        Write-Log "ERROR" "Active Directory module not found. Please install RSAT: Active Directory Domain Services tools."
    }

    foreach ($term in $searchTerms)
    {
        $found = $false
        if ($source -eq 'AD' -or $source -eq 'Both')
        {
            try
            {
                $adComputer = Get-ADComputer -Filter "SerialNumber -eq '$term' -or Name -like '*$term*'" -Server $ADServer -ErrorAction Stop
                if ($adComputer)
                {
                    Write-Result "AD" $term $adComputer.Name "OK"
                    $found = $true
                }
            }
            catch
            {
                Write-Log "ERROR" "AD search for '$term' failed: $( $_.Exception.Message )"
            }
        }

        if ($source -eq 'SCCM' -or $source -eq 'Both')
        {
            try
            {
                $namespace = "ROOT\SMS\site_$SCCMSiteCode"
                $sccmQuery = "SELECT sys.Name FROM SMS_R_System AS sys JOIN SMS_G_System_SYSTEM_ENCLOSURE AS se ON sys.ResourceID = se.ResourceID WHERE se.SerialNumber = '$term' OR sys.Name = '$term'"
                $sccmDevice = Get-WmiObject -Query $sccmQuery -ComputerName $SCCMServer -Namespace $namespace -ErrorAction Stop
                if ($sccmDevice)
                {
                    Write-Result "SCCM" $term $sccmDevice.Name "OK"
                    $found = $true
                }
            }
            catch
            {
                Write-Log "ERROR" "SCCM search for '$term' failed: $( $_.Exception.Message )"
            }
        }

        # --- THIS SECTION IS NOW REMOVED TO HIDE "NOT FOUND" RESULTS ---
        # No output is generated if a term is not found, cleaning up the UI.
    }
}

function Invoke-Removal
{
    $computerNames = $targets -split ',' | Where-Object { $_ -ne "" }
    Write-Log "INFO" "Removal starting for $( $computerNames.Count ) computer(s)"

    try
    {
        $un = "svcSMSPushAcct"
        $pass = '{QM90mDOIbn=*zdcEg:T:9dBQL#:J0' | ConvertTo-SecureString -AsPlainText -Force
        $adCreds = New-Object System.Management.Automation.PSCredential($un, $pass)
    }
    catch
    {
        Write-Log "CRITICAL" "Failed to create credential object. Check PowerShell version and permissions."
        return
    }

    foreach ($name in $computerNames)
    {
        # SCCM Removal
        try
        {
            $sccmDevices = Get-WmiObject -Query "SELECT * FROM SMS_R_SYSTEM WHERE Name='$name'" -ComputerName $SCCMServer -Namespace "ROOT\SMS\site_$SCCMSiteCode" -ErrorAction Stop
            if ($sccmDevices)
            {
                foreach ($device in $sccmDevices)
                {
                    $device.psbase.Delete()
                }
                Write-Log "INFO" "Successfully removed '$name' from SCCM."
            }
            else
            {
                Write-Log "WARN" "Computer '$name' not found in SCCM."
            }
        }
        catch
        {
            Write-Log "ERROR" "Failed to remove '$name' from SCCM: $( $_.Exception.Message )"
        }

        # Active Directory Removal
        try
        {
            $adComputer = Get-ADComputer $name -Server $ADServer -Credential $adCreds -ErrorAction Stop
            if ($adComputer)
            {
                # --- THIS IS THE FIX: Added the -Recursive switch ---
                Remove-ADObject -Identity $adComputer -Recursive -Confirm:$false -Credential $adCreds -Server $ADServer -ErrorAction Stop
                Write-Log "INFO" "Successfully removed '$name' from Active Directory."
            }
            else
            {
                Write-Log "WARN" "Computer '$name' not found in AD."
            }
        }
        catch
        {
            if ($_.Exception.Message -like "*Cannot find an object*")
            {
                Write-Log "WARN" "Computer '$name' not found in AD."
            }
            else
            {
                Write-Log "ERROR" "Failed to remove '$name' from AD: $( $_.Exception.Message )"
            }
        }
    }
}

# --- Main script execution ---
if ($search)
{
    Invoke-Search
}
elseif ($remove)
{
    Invoke-Removal
}