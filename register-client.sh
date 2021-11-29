export JSON_OBJECT=$(cat ./ci/terraform/oidc/outputs.json)
echo $JSON_OBJECT
export BASE_URL=$(node -pe 'JSON.parse(process.argv[1]).base_url' $JSON_OBJECT)
export JSON_RESP=$(curl --location --request POST "${BASE_URL}/connect/register" \
--header 'Content-Type: application/json' \
--data-raw '{
    "client_name": "rp_stub",
    "redirect_uris": [
        "http://localhost:8081/oidc/authorization-code/callback"
    ],
    "contacts": [],
    "public_key": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxt91w8GsMDdklOpS8ZXAsIM1ztQZd5QT/bRCQahZJeS1a6Os4hbuKwzHlz52zfTNp7BL4RB/KOcRIPhOQLgqeyM+bVngRa1EIfTkugJHS2/gu2Xv0aelwvXj8FZgAPRPD+ps2wiV4tUehrFIsRyHZM3yOp9g6qapCcxF7l0E1PlVkKPcPNmxn2oFiqnP6ZThGbE+N2avdXHcySIqt/v6Hbmk8cDHzSExazW7j/XvA+xnp0nQ5m2GisCZul5If5edCTXD0tKzx/I/gtEG4gkv9kENWOt4grP8/0zjNAl2ac6kpRny3tY5RkKBKCOB1VHwq2lUTSNKs32O1BsA5ByyYQIDAQAB",
    "scopes": [
        "openid",
        "email",
        "phone"
    ],
    "post_logout_redirect_uris": [
        "http://localhost:8081/signed_out"
    ],
    "service_type": "MANDATORY",
    "sector_identifier_uri": "http://test.com",
    "subject_type": "public"
}')
export CLIENT_ID=$(node -pe 'JSON.parse(process.argv[1]).client_id' $JSON_RESP)
echo $CLIENT_ID > ./build/client_id.txt