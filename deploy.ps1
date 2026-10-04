$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if ($args.Count -gt 0 -and $args[0] -notlike '-*') {
	$modsDir = $args[0]
	$rest = @()
	if ($args.Count -gt 1) {
		$rest = $args[1..($args.Count - 1)]
	}
	& .\gradlew.bat deploy "-Pmods_dir=$modsDir" @rest
} else {
	& .\gradlew.bat deploy @args
}

exit $LASTEXITCODE
