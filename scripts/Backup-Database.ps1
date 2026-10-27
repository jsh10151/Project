param([switch]$VerifyRestore)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$backupDir=Join-Path $projectRoot 'backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$backupName='spacepick-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)+'.sql'
$target=Join-Path $backupDir $backupName
$container=(docker compose --env-file .env -p space-pick -f compose.yml ps -q db).Trim()
if(!$container){throw 'SPACE PICK development database is not running.'}
$tempPath='/tmp/'+$backupName
try {
 docker exec $container sh -c 'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysqldump -u "$MYSQL_USER" --single-transaction --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"' | Out-Null
 if($LASTEXITCODE -ne 0){throw 'Database dump preflight failed.'}
 docker exec $container sh -c ('export MYSQL_PWD="$MYSQL_PASSWORD"; mysqldump -u "$MYSQL_USER" --single-transaction --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE" > '+$tempPath)
 if($LASTEXITCODE -ne 0){throw 'Database dump failed.'}
 docker cp "${container}:$tempPath" $target
 if($LASTEXITCODE -ne 0){throw 'Backup copy failed.'}
} finally { docker exec $container rm -f $tempPath | Out-Null }
if((Get-Item -LiteralPath $target).Length -lt 100){throw 'Backup file is empty.'}
Write-Output "Backup created: $target"
if(!$VerifyRestore){return}
$restoreName='spacepick-restore-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
try {
 docker run --detach --rm --name $restoreName -e MYSQL_ALLOW_EMPTY_PASSWORD=yes mysql:8.4 | Out-Null
 if($LASTEXITCODE -ne 0){throw 'Cannot start isolated restore database.'}
 $ready=$false
 for($i=0;$i -lt 45;$i++){
  docker exec $restoreName mysqladmin ping -uroot --silent 2>$null | Out-Null
  if($LASTEXITCODE -eq 0){$ready=$true;break}
  Start-Sleep -Seconds 1
 }
 if(!$ready){throw 'Restore database did not become ready.'}
 docker cp $target "${restoreName}:/tmp/backup.sql"
 if($LASTEXITCODE -ne 0){throw 'Restore input copy failed.'}
 docker exec $restoreName mysql -uroot -e 'CREATE DATABASE spacepick CHARACTER SET utf8mb4;'
 if($LASTEXITCODE -ne 0){throw 'Restore schema creation failed.'}
 docker exec $restoreName sh -c 'mysql -uroot spacepick < /tmp/backup.sql'
 if($LASTEXITCODE -ne 0){throw 'SQL restore failed.'}
 docker exec $restoreName mysql -uroot -N -e 'SELECT COUNT(*) AS tables_restored FROM information_schema.tables WHERE table_schema="spacepick"; SELECT COUNT(*) AS spaces_restored FROM spacepick.spaces; SELECT COUNT(*) AS bookings_restored FROM spacepick.bookings;'
 if($LASTEXITCODE -ne 0){throw 'Restored data verification failed.'}
 Write-Output 'Isolated restore completed. Original database was not modified. Container has no published ports.'
} finally { docker stop $restoreName | Out-Null }