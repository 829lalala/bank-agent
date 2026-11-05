param(
    [string]$ReportDirectory = 'target/evaluation-reports'
)

# 仅读取已落盘报告；所有轮次与异常题均保留在分母中。
$evaluationFiles = Get-ChildItem -LiteralPath $ReportDirectory -Filter '*-acceptance-*.json'
if (-not $evaluationFiles) { throw '未找到验收报告，请先执行在线评估。' }
foreach ($evaluationFile in $evaluationFiles) {
    $evaluationReport = Get-Content -LiteralPath $evaluationFile.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    $evaluationRows = @($evaluationReport.rows)
    $passedRows = @($evaluationRows | Where-Object { $_.passed })
    $errorRows = @($evaluationRows | Where-Object { $_.errorType })
    $usageRows = @($evaluationRows | Where-Object {
        $null -ne $_.finalResponseInputTokens -and $null -ne $_.finalResponseOutputTokens
    })
    [pscustomobject]@{
        File = $evaluationFile.Name
        Model = $evaluationReport.model
        Total = $evaluationRows.Count
        Passed = $passedRows.Count
        PassPercent = [math]::Round(100 * $passedRows.Count / $evaluationRows.Count, 2)
        Errors = $errorRows.Count
        AverageMs = [math]::Round($evaluationReport.averageMs, 2)
        P50Ms = $evaluationReport.p50Ms
        P95Ms = $evaluationReport.p95Ms
        RowsWithFinalUsage = $usageRows.Count
        FinalInputTokens = ($usageRows | Measure-Object -Property finalResponseInputTokens -Sum).Sum
        FinalOutputTokens = ($usageRows | Measure-Object -Property finalResponseOutputTokens -Sum).Sum
    } | Format-List
    $evaluationRows | Group-Object round | ForEach-Object {
        [pscustomobject]@{
            Round = $_.Name
            Passed = @($_.Group | Where-Object { $_.passed }).Count
            Total = $_.Count
        }
    } | Format-Table
    $evaluationRows | Group-Object category | ForEach-Object {
        [pscustomobject]@{
            Category = $_.Name
            Passed = @($_.Group | Where-Object { $_.passed }).Count
            Total = $_.Count
        }
    } | Format-Table
}
