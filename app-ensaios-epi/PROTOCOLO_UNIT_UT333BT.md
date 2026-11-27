# Medidor de temperatura e umidade UNI-T UT333BT — Bluetooth

Levantado em 25/11/2026 do registro HCI. Aparelho anuncia-se como **UT333BT**.

## GATT
- Serviço: `0000ff12-0000-1000-8000-00805f9b34fb` (16 bits 0xFF12)
- Notificação (leitura): `0000ff02` (handle 0x13), props notify
- Escrita (comando): `0000ff01` (handle 0x10), write sem resposta

## Como ler
1. Ligar notificação no CCCD de 0xFF02 (escrever `01 00`).
2. Ficar escrevendo o byte **`0x5E`** em 0xFF01 (write-no-response) a cada ~300 ms — cada escrita faz o aparelho
   mandar uma leitura por notificação.
3. A leitura chega em **TEXTO** (19 bytes): `aa bb 10 01 <cod> <ascii...> 80 04 <cksum>`.
   - umidade: contém "%RH" (ex.: `...36 39 2e 34 25 52 48...` = "69.4%RH"), byte5 = 0x34.
   - temperatura: em °C (ex.: `...32 38 2e 30 20 a1 e6...` = "28.0 °C"), byte5 = 0x30.
   - valor = o número ASCII (regex `\d+\.?\d*`). "%" presente → umidade; senão → temperatura.

Confirmado: umidade subiu 69,4 → 80,0 %RH (sopro no sensor); temperatura ~28,0–28,4 °C.
