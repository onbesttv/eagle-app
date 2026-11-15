$sbUrl = "https://myreymyqsnikjdhnstjn.supabase.co"
$sbKey = "sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM"

$headers = @{
    "apikey" = $sbKey
    "Authorization" = "Bearer $sbKey"
    "Content-Type" = "application/json"
}

$body = @{
    p_reseller_id = "dream"
    p_password = "dream"
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Uri "$sbUrl/rest/v1/rpc/panel_login_dashboard" -Headers $headers -Method Post -Body $body
    $res | ConvertTo-Json -Depth 5
} catch {
    Write-Host "Error: $_"
}
