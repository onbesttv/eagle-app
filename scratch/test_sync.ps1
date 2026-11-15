$body = @{
    p_device_id = 'test-probe-1'
    p_username = 'test'
    p_service = 'best'
    p_device_name = 'probe'
    p_app_version = '1.0'
    p_reseller_id = 'suporte24'
} | ConvertTo-Json

$headers = @{
    apikey = 'sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM'
    Authorization = 'Bearer sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM'
    'Content-Type' = 'application/json'
}

$resp = Invoke-RestMethod -Uri 'https://myreymyqsnikjdhnstjn.supabase.co/rest/v1/rpc/app_sync' -Method Post -Headers $headers -Body $body
$resp | ConvertTo-Json -Depth 5
