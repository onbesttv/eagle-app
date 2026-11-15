const https = require("https");
const req = https.request({
  hostname: "myreymyqsnikjdhnstjn.supabase.co",
  path: "/rest/v1/resellers?select=*",
  method: "GET",
  headers: {
    "apikey": "sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM",
    "Authorization": "Bearer sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM"
  }
}, res => {
  let d = "";
  res.on("data", c => d += c);
  res.on("end", () => {
    try {
      const arr = JSON.parse(d);
      arr.forEach(r => {
        console.log("Reseller:", r.reseller_id, "| best_url:", r.best_url, "| ann:", r.announcement?.substring(0, 80));
      });
    } catch (e) {
      console.log("Raw:", d);
    }
  });
});
req.end();
