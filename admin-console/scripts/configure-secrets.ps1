param(
 [Parameter(Mandatory=$true)][string]$AccessTeamDomain,
 [Parameter(Mandatory=$true)][string]$GithubAppId,
 [Parameter(Mandatory=$true)][string]$GithubInstallationId,
 [Parameter(Mandatory=$true)][string]$GithubPrivateKeyPath,
 [switch]$PromptAccessToken,
 [string]$AccessTokenFile
)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath 'C:\rusmorph-v006\admin-console'
if($AccessTeamDomain -notmatch '^[a-z0-9-]+\.cloudflareaccess\.com$'){throw 'Use the full Cloudflare Access team domain'}
if($GithubAppId -notmatch '^\d+$' -or $GithubInstallationId -notmatch '^\d+$'){throw 'GitHub App identifiers must be numeric'}
$taskKeyFile=Get-Item -LiteralPath $GithubPrivateKeyPath
$taskRepoRoot=[IO.Path]::GetFullPath('C:\rusmorph-v006')+[IO.Path]::DirectorySeparatorChar
if($taskKeyFile.FullName.StartsWith($taskRepoRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Store the private key outside the source repository'}
$taskPriorToken=$env:CLOUDFLARE_API_TOKEN
$taskPriorDomain=$env:WERUS_ACCESS_TEAM_DOMAIN
try {
 # Check Worker access with the existing Wrangler credential, before switching to an Access-only token.
 npx wrangler secret list *> $null
 if($LASTEXITCODE -ne 0){throw 'Deploy the protected bootstrap Worker before configuring server secrets'}
 node scripts/verify-github.mjs $GithubAppId $GithubInstallationId $taskKeyFile.FullName
 $taskGithubVerified=$LASTEXITCODE -eq 0
 if(-not $taskGithubVerified){Write-Warning 'GitHub installation permissions are incomplete. Access setup will continue; GitHub secrets will not be uploaded.'}
 if($PromptAccessToken -or $AccessTokenFile){
  if($PromptAccessToken){
   $taskSecureToken=Read-Host 'Cloudflare Access API token (hidden; never paste into chat)' -AsSecureString
  }else{
   $taskSecureToken=(Get-Content -LiteralPath $AccessTokenFile -Raw).Trim() | ConvertTo-SecureString
  }
  if($taskSecureToken.Length -eq 0){throw 'An Access management token is required'}
  $taskTokenPtr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecureToken)
  try{$env:CLOUDFLARE_API_TOKEN=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskTokenPtr)}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskTokenPtr)}
 }
 $env:WERUS_ACCESS_TEAM_DOMAIN=$AccessTeamDomain
 node scripts/provision.mjs --access-only
 if($LASTEXITCODE -ne 0){throw 'Access provisioning failed; no GitHub secrets uploaded'}
 # Restore Wrangler's existing Worker credential; the Access-only token need not manage Workers.
 $env:CLOUDFLARE_API_TOKEN=$taskPriorToken
 if(-not $taskGithubVerified){
  Write-Warning 'Access configured. Approve GitHub App permissions and rerun this script to finish GitHub setup. Redeployment and real login verification remain required.'
  return
 }
 # Normalize GitHub PKCS#1 locally to PKCS#8; PEM bytes never appear in arguments.
 node --input-type=module -e "import {readFileSync} from 'node:fs'; import {normalizeKey} from './scripts/verify-github.mjs'; process.stdout.write(normalizeKey(readFileSync(process.argv[1],'utf8')));" $taskKeyFile.FullName | npx wrangler secret put GITHUB_APP_PRIVATE_KEY
 if($LASTEXITCODE -ne 0){throw 'Private key upload failed'}
 $GithubAppId | npx wrangler secret put GITHUB_APP_ID
 if($LASTEXITCODE -ne 0){throw 'App ID upload failed'}
 $GithubInstallationId | npx wrangler secret put GITHUB_INSTALLATION_ID
 if($LASTEXITCODE -ne 0){throw 'Installation ID upload failed'}
 Write-Output 'Access and restricted GitHub App configured. Deployment and authenticated end-to-end validation are still required.'
} finally {
 $env:CLOUDFLARE_API_TOKEN=$taskPriorToken
 $env:WERUS_ACCESS_TEAM_DOMAIN=$taskPriorDomain
 if($null -ne $taskSecureToken){$taskSecureToken.Dispose()}
 $taskSecureToken=$null
}
