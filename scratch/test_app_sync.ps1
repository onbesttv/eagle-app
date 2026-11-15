$sbUrl = "https://myreymyqsnikjdhnstjn.supabase.co"
$sbKey = "sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM"

$headers = @{
    "apikey" = $sbKey
    "Authorization" = "Bearer $sbKey"
    "Content-Type" = "application/json"
}

$body = @{
    p_device_id = "test-check"
    p_username = ""
    p_service = "best"
    p_device_name = "test"
    p_app_version = "3.25"
    p_reseller_id = "dream"
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Uri "$sbUrl/rest/v1/rpc/app_sync" -Headers $headers -Method Post -Body $body
    $res | ConvertTo-Json -Depth 5
} catch {
    Write-Host "Error: $_"
}
