$base = "http://localhost:8081"
$email = "verify$(Get-Random)@test.local"

$reg = Invoke-RestMethod -Uri "$base/api/v1/auth/register" -Method Post -ContentType "application/json" `
  -Body (@{email=$email; password="VerifyPassw0rd123"; displayName="VerifyUser"} | ConvertTo-Json)
$token = $reg.data.accessToken
if (-not $token) { Write-Host "REGISTER FAILED: $($reg|ConvertTo-Json)"; exit 1 }

$cid = (Invoke-RestMethod -Uri "$base/api/v1/conversations" -Method Post `
  -Headers @{Authorization="Bearer $token"} -ContentType "application/json" -Body "{}").data.id
$modelId = (Invoke-RestMethod -Uri "$base/api/v1/ai/models" -Headers @{Authorization="Bearer $token"}).data[0].id
Write-Host "cid=$cid  modelId=$modelId"

$streamJson = @{content="Hello, this is a streaming test."; contentType="PLAIN_TEXT"; modelId=$modelId} | ConvertTo-Json -Compress
$f = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($f, $streamJson, [System.Text.Encoding]::UTF8)

$auth = "Authorization: Bearer $token"
$ct   = "Content-Type: application/json"
$acc  = "Accept: text/event-stream"
$url  = "$base/api/v1/conversations/$cid/messages:stream"

Write-Host "`n==> [A] SSE RESPONSE HEADERS"
curl.exe -s -D - -o NUL -X POST $url -H $auth -H "Idempotency-Key: verify-A" -H $ct -H $acc -d "@$f"

Write-Host "`n==> [B] STREAM EVENTS (distinct Idempotency-Key)"
curl.exe -sN -X POST $url -H $auth -H "Idempotency-Key: verify-B" -H $ct -H $acc -d "@$f"
