$headers = @{
    apikey = 'sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM'
    Authorization = 'Bearer sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM'
    'Content-Type' = 'application/json'
}

$body = @{
    p_reseller_id = 'suporte24'
    p_password = 'suporte24'
    p_best_url = 'http://bmgroup2026.com:8080'
    p_best2_url = 'http://bmgroup2026.com:8080'
    p_announcement = ''
    p_maintenance_mode = $false
    p_new_password = ''
} | ConvertTo-Json

$resp = Invoke-RestMethod -Uri 'https://myreymyqsnikjdhnstjn.supabase.co/rest/v1/rpc/panel_save_own_config' -Method Post -Headers $headers -Body $body
Write-Host "Updated config for suporte24:" -ForegroundColor Green
$resp | ConvertTo-Json
