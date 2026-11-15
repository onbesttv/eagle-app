$sbUrl = "https://myreymyqsnikjdhnstjn.supabase.co"
$sbKey = "sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM"

$headers = @{
    "apikey" = $sbKey
    "Authorization" = "Bearer $sbKey"
}

$res = Invoke-RestMethod -Uri "$sbUrl/rest/v1/app_resellers?reseller_id=eq.dream" -Headers $headers
$res | ConvertTo-Json -Depth 5
