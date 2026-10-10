param([Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f-]{36}$')][string]$VersionId)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath 'C:\rusmorph-v006\admin-console'
# Rolls back the management Worker only; neither textbook pointers nor user databases are reverted.
npx wrangler rollback $VersionId --message 'WeRus management-console rollback'
if($LASTEXITCODE -ne 0){throw 'Worker rollback failed'}
