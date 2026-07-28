<#
  Teste de autenticação interativa (Entra device-code) contra o Azure DevOps — SEM PAT,
  sem instalar nada (só PowerShell + navegador). Reproduz exatamente o fluxo que o loader
  do S9 usaria. Se tudo der certo, imprime "Funcionou".

  Como rodar:  powershell -ExecutionPolicy Bypass -File .\ado-auth-test.ps1
  (ou abra o PowerShell e cole o conteúdo)
#>

$ErrorActionPreference = 'Stop'

# client id público do Azure CLI (pré-consentido na maioria dos tenants — não registra app)
$Client   = '04b07795-8ddb-461a-bbee-02f9e1bf7b46'
# 499b84ac-... = recurso "Azure DevOps"; .default = permissões já consentidas
$Scope    = '499b84ac-1321-427f-aa17-267ca6975798/.default offline_access'
$Authority = 'https://login.microsoftonline.com/organizations/oauth2/v2.0'

function Fail($msg) {
  Write-Host ""
  Write-Host "NAO funcionou -> $msg" -ForegroundColor Red
  exit 1
}

$Org = Read-Host 'Nome da organizacao no Azure DevOps (o <org> em dev.azure.com/<org>)'
if ([string]::IsNullOrWhiteSpace($Org)) { Fail 'organizacao nao informada' }

# 1) pede o device code
try {
  $dc = Invoke-RestMethod -Method Post -Uri "$Authority/devicecode" `
    -Body @{ client_id = $Client; scope = $Scope }
} catch {
  Fail "o tenant bloqueou o device-code / client publico: $($_.ErrorDetails.Message)"
}

Write-Host ""
Write-Host "==> Abra:  $($dc.verification_uri)" -ForegroundColor Cyan
Write-Host "==> Codigo: $($dc.user_code)" -ForegroundColor Cyan
Write-Host "    (faca login com seu usuario + MFA; este script fica aguardando)" -ForegroundColor DarkGray
Write-Host ""

# 2) faz polling ate o login concluir (ou falhar)
$token   = $null
$deadline = (Get-Date).AddSeconds([int]$dc.expires_in)
$interval = [int]$dc.interval + 1
while (-not $token) {
  if ((Get-Date) -gt $deadline) { Fail 'tempo esgotado esperando o login' }
  Start-Sleep -Seconds $interval
  try {
    $r = Invoke-RestMethod -Method Post -Uri "$Authority/token" -Body @{
      grant_type  = 'urn:ietf:params:oauth:grant-type:device_code'
      client_id   = $Client
      device_code = $dc.device_code
    }
    $token = $r.access_token
  } catch {
    $err = ''
    try { $err = ($_.ErrorDetails.Message | ConvertFrom-Json).error } catch {}
    switch ($err) {
      'authorization_pending' { }                    # ainda logando -> continua
      'slow_down'             { $interval += 5 }      # backoff
      'authorization_declined' { Fail 'login recusado por voce' }
      'expired_token'         { Fail 'o codigo expirou; rode de novo' }
      'access_denied'         { Fail 'acesso negado (politica de consentimento do tenant)' }
      default                 { Fail "erro de auth: $($_.ErrorDetails.Message)" }
    }
  }
}
Write-Host "Token obtido." -ForegroundColor Green

# 3) chama a REST API do Azure DevOps com o token do usuario
try {
  $proj = Invoke-RestMethod -Uri "https://dev.azure.com/$Org/_apis/projects?api-version=7.1" `
    -Headers @{ Authorization = "Bearer $token" }
} catch {
  Fail "auth OK, mas a API do ADO recusou (permissao/escopo): $($_.Exception.Message)"
}

Write-Host ""
Write-Host "Funcionou" -ForegroundColor Green
Write-Host ("(%d projeto(s) visiveis na org '%s')" -f $proj.count, $Org) -ForegroundColor DarkGray
