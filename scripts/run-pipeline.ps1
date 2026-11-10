# Chạy luồng /deliver không người trông: mỗi vòng một hạng mục backlog, một phiên Claude mới (ngữ cảnh sạch).
# Dừng khi skill trả PIPELINE_STATUS khác CONTINUE. Dùng: powershell -File scripts/run-pipeline.ps1 [-MaxItems 20]
param([int]$MaxItems = 20)

Set-Location (Split-Path $PSScriptRoot -Parent)
[Console]::OutputEncoding = [Text.Encoding]::UTF8
New-Item -ItemType Directory -Force .pipeline-logs | Out-Null

for ($i = 1; $i -le $MaxItems; $i++) {
    $log = ".pipeline-logs/$(Get-Date -Format yyyyMMdd-HHmmss).log"
    Write-Host "=== Vòng $i/$MaxItems -> $log"
    # acceptEdits + allowlist/deny trong .claude/settings.json + hook guard.mjs; lệnh ngoài allowlist bị từ chối, không treo.
    claude -p "/deliver" --permission-mode acceptEdits --disallowedTools PowerShell | Tee-Object -FilePath $log

    $m = Select-String -Path $log -Pattern 'PIPELINE_STATUS:\s*(CONTINUE|BLOCKED|COMPLETE)' | Select-Object -Last 1
    $status = if ($m) { $m.Matches[0].Groups[1].Value } else { 'UNKNOWN' }
    if ($status -eq 'CONTINUE') { continue }

    Write-Host "=== Dừng: $status. Xem $log, docs/backlog.md, docs/worklog.md"
    if ($status -eq 'COMPLETE') { exit 0 } else { exit 2 }
}
Write-Host "=== Đạt MaxItems=$MaxItems"
