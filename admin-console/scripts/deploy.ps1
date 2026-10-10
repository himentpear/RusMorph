param([switch]$Bootstrap,[switch]$SkipTests)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath 'C:\rusmorph-v006\admin-console'
if(-not $SkipTests){npm test;if($LASTEXITCODE -ne 0){throw 'Management tests failed'}}
npm run build
if($LASTEXITCODE -ne 0){throw 'Management build failed'}
$taskConfigPath='bootstrap.local.json'
try {
 if($Bootstrap){
  $taskConfig=Get-Content -LiteralPath 'wrangler.jsonc' -Raw | ConvertFrom-Json
  $taskConfig.services=@()
  $taskConfig | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $taskConfigPath -Encoding utf8
  npx wrangler deploy --config $taskConfigPath --dry-run
  if($LASTEXITCODE -ne 0){throw 'Bootstrap dry run failed'}
  npx wrangler deploy --config $taskConfigPath
 }else{
  npx wrangler deploy --dry-run
  if($LASTEXITCODE -ne 0){throw 'Deployment dry run failed'}
  npx wrangler deploy
 }
 if($LASTEXITCODE -ne 0){throw 'Cloudflare deployment failed'}
} finally {
 if(Test-Path -LiteralPath $taskConfigPath){Remove-Item -LiteralPath $taskConfigPath}
}
