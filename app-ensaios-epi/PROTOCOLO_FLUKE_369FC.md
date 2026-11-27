# Alicate de fuga FLUKE 369 FC — Bluetooth (Fluke Connect)

Levantado em 25/11/2026 do registro HCI (medições feitas com o app Fluke Connect).
Aparelho: **FLUKE 369FC**, firmware **V0.5.5**, fabricante "Fluke Mfg Co.". Anuncia-se como **369FC**.
Conecta direto no celular por BLE (sem base intermediária) e **transmite a leitura ao vivo** (notificações).

UUID base Fluke Connect: `b69829NN-7562-11e2-b50d-00163e46f8fe`.

## Passos do app (do registro)
1. Liga notificação (CCCD = 01 00) nas características de medição.
2. Sincroniza a hora escrevendo em `b698290e` (handle 0x30): `01` + uint32 segundos (ex.: 0x6b074626).
3. A leitura chega por **notificação** em `b698290f` (handle 0x24), ~5 por segundo, 8 bytes.

## Formato da notificação de medição (b698290f, 8 bytes)
| bytes | conteúdo |
|---|---|
| 0–1 | **valor** (uint16 little-endian) — varia com a medição |
| 2 | 00 |
| 3 | 06 |
| 4 | 03 — provável nº de casas decimais (valor ÷ 1000?) |
| 5 | 0e — provável código de unidade |
| 6 | 03 |
| 7 | 00 (visto 0x80 uma vez — provável sinal/estado) |

Exemplos reais (bytes 0–1 LE): capturas variaram de ~91 a ~1169 e de ~393 a ~1118.
Com o alicate parado/aberto o valor fica baixo e estável; ao medir, sobe.

## Confirmado (todas as leituras da captura em Ampères)
Valores conferidos com o visor: raw **1130** → **1,130 A** e raw **440** → **0,440 A**.
Logo: **valor = int16 LE ÷ 1000, em Ampères** (tail `00 06 03 0e 03`, onde 0x03 = 3 casas decimais e 0x0e = unidade A).
Para o campo do laudo em mA: mA = (int16 ÷ 1000) × 1000 = int16 (quando em A com 3 casas).

### FÓRMULA FINAL (faixas A e mA confirmadas)
Pacote de 8 bytes de b698290f: bytes 0–1 = inteiro (int16 LE) = `valor`; **byte 5 = código de escala**.
- **corrente (mA) = valor × 10^(byte5 − 14)**  ·  **corrente (A) = valor × 10^(byte5 − 17)**

Confirmado:
- byte5 = 0x0e (faixa A): 1130→1,130 A; 889→0,889 A AC; 440→0,440 A; 160→0,160 A.
- byte5 = 0x0d (faixa mA): 1608→160,8 mA.

byte2 = flags (0x80 visto em estados instáveis/hold — descartar leitura com esse flag ou esperar estabilizar).
O 369 FC auto-ajusta a faixa (A ↔ mA); como o byte5 vem em cada notificação, o mesmo cálculo cobre as duas.

## Implementação prevista (app de Ensaios de EPI)
- Botão "Medir pelo Bluetooth" ao lado de "Corrente de fuga".
- Conecta no 369FC, liga notificação em b698290f (0x24), lê o valor ao vivo (int16 LE),
  converte para a unidade do campo (mA) conforme a faixa, mostra para conferir e grava.
