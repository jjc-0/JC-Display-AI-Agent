$env:OPENCLAW_HOME = "d:\code\AI_Prompt\.openclaw"

Write-Host "=== OpenClaw WeChat 频道初始化 ==="
Write-Host ""
Write-Host "接下来需要交互式配置微信频道。"
Write-Host "选择频道时输入: openclaw-weixin"
Write-Host "已有凭证自动识别，无需重新扫码。"
Write-Host ""

& "E:\shop\evn\nodejs\node.exe" "E:\shop\evn\nodejs\node_modules\openclaw\openclaw.mjs" channels add

Write-Host ""
Write-Host "=== 启动 Gateway ==="
& "E:\shop\evn\nodejs\node.exe" "E:\shop\evn\nodejs\node_modules\openclaw\openclaw.mjs" gateway run
