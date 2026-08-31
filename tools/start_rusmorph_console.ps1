param(
    [ValidateSet("Menu", "Start", "Stop", "Status")]
    [string]$Action = "Menu"
)

$ErrorActionPreference = "Stop"

$speechTask = "RusMorph Speech Backend"
$agentTask = "RusMorph Agent Proxy"
$watchdogTask = "RusMorph Speech Backend Watchdog"
$serviceTasks = @($speechTask, $agentTask)

function Test-ServiceHealth {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Uri
    )

    try {
        $response = Invoke-RestMethod -Uri $Uri -TimeoutSec 3
        return $response.status -eq "ok"
    } catch {
        return $false
    }
}

function Stop-RusMorphListener {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    $listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    foreach ($listener in $listeners) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
        if ($null -eq $process) {
            continue
        }

        $isExpected = if ($Port -eq 18000) {
            $process.CommandLine -match "uvicorn\s+app\.main:app.*--port\s+18000"
        } else {
            $process.CommandLine.Contains("\agent-proxy\") -and
                $process.CommandLine.Contains("8787")
        }
        if (-not $isExpected) {
            Write-Host "端口 $Port 被其他程序占用，未强制结束该进程。" -ForegroundColor Red
            continue
        }

        $processIds = @($process.ProcessId)
        $parent = Get-CimInstance Win32_Process -Filter "ProcessId=$($process.ParentProcessId)"
        if ($null -ne $parent) {
            $expectedParent = if ($Port -eq 18000) {
                $parent.CommandLine.Contains("\russian_speech_backend\") -and
                    $parent.CommandLine.Contains("uvicorn")
            } else {
                $parent.CommandLine.Contains("\agent-proxy\") -and
                    $parent.CommandLine.Contains("wrangler")
            }
            if ($expectedParent) {
                $processIds = @($parent.ProcessId) + $processIds
            }
        }

        foreach ($processId in $processIds) {
            Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
        }
    }
}

function Show-RusMorphStatus {
    $speechReady = Test-ServiceHealth "http://127.0.0.1:18000/health"
    $agentReady = Test-ServiceHealth "http://127.0.0.1:8787/health"

    Write-Host ""
    Write-Host "RusMorph 当前状态" -ForegroundColor Cyan
    Write-Host ("  语音后端  : " + $(if ($speechReady) { "运行中" } else { "已停止" })) `
        -ForegroundColor $(if ($speechReady) { "Green" } else { "DarkGray" })
    Write-Host ("  AI 代理   : " + $(if ($agentReady) { "运行中" } else { "已停止" })) `
        -ForegroundColor $(if ($agentReady) { "Green" } else { "DarkGray" })
    Write-Host ""
}

function Start-RusMorph {
    Write-Host ""
    Write-Host "正在启动 RusMorph，请稍候……" -ForegroundColor Yellow

    Disable-ScheduledTask -TaskName $watchdogTask -ErrorAction SilentlyContinue | Out-Null
    foreach ($taskName in $serviceTasks) {
        Start-ScheduledTask -TaskName $taskName
    }

    $deadline = (Get-Date).AddSeconds(60)
    do {
        $speechReady = Test-ServiceHealth "http://127.0.0.1:18000/health"
        $agentReady = Test-ServiceHealth "http://127.0.0.1:8787/health"
        if (-not ($speechReady -and $agentReady)) {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline -and -not ($speechReady -and $agentReady))

    if ($speechReady -and $agentReady) {
        Write-Host "RusMorph 已启动，可在安卓端使用。" -ForegroundColor Green
    } else {
        Write-Host "部分服务未能及时启动，请选择 [3] 查看状态和日志。" -ForegroundColor Red
    }
}

function Stop-RusMorph {
    Write-Host ""
    Write-Host "正在停止 RusMorph……" -ForegroundColor Yellow

    Disable-ScheduledTask -TaskName $watchdogTask -ErrorAction SilentlyContinue | Out-Null
    foreach ($taskName in $serviceTasks) {
        Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    }

    Start-Sleep -Seconds 1
    Stop-RusMorphListener -Port 18000
    Stop-RusMorphListener -Port 8787
    Start-Sleep -Seconds 1
    Write-Host "RusMorph 后台服务已停止，自动启动触发器保持关闭。" -ForegroundColor Green
}

$host.UI.RawUI.WindowTitle = "RusMorph 控制台"
try {
    [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
} catch {
    # Keep the current console encoding when the host does not expose it.
}

switch ($Action) {
    "Start" {
        Start-RusMorph
        Show-RusMorphStatus
        exit 0
    }
    "Stop" {
        Stop-RusMorph
        Show-RusMorphStatus
        exit 0
    }
    "Status" {
        Show-RusMorphStatus
        exit 0
    }
}

while ($true) {
    Clear-Host
    Write-Host "========================================" -ForegroundColor DarkCyan
    Write-Host "           RusMorph 服务控制台" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor DarkCyan
    Show-RusMorphStatus
    Write-Host "  [1] 启动 RusMorph"
    Write-Host "  [2] 停止 RusMorph"
    Write-Host "  [3] 刷新状态"
    Write-Host "  [Q] 关闭此窗口（不改变服务状态）"
    Write-Host ""

    $choice = [Console]::ReadKey($true).KeyChar.ToString().ToUpperInvariant()
    switch ($choice) {
        "1" {
            Start-RusMorph
            Write-Host ""
            Write-Host "按任意键返回菜单……" -ForegroundColor DarkGray
            [Console]::ReadKey($true) | Out-Null
        }
        "2" {
            Stop-RusMorph
            Write-Host ""
            Write-Host "按任意键返回菜单……" -ForegroundColor DarkGray
            [Console]::ReadKey($true) | Out-Null
        }
        "3" {
            continue
        }
        "Q" {
            break
        }
    }
}
