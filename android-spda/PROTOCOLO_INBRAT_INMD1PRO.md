# Miliohmímetro INBRAT INMD 1 Pro — protocolo Bluetooth (BLE)

Levantado em 20/11/2026 a partir do registro HCI do celular (ensaio feito com o app Inbrat Connect 1.0.2)
e dos nomes do próprio app. Aparelho do registro: nº de série **ML2511013**, firmware **2.3.1**,
nome no Bluetooth **IN433A**.

Todos os números são *little-endian*. UUIDs no padrão `XXXX42NN-...`: `NN = 00` é o serviço e `01, 02, 03…` as características.

## Serviços e características

| Serviço | Característica | Handle | Propriedades | Uso |
|---|---|---|---|---|
| `564b4200-1edc-4a71-8b34-b6ca02cb1e2c` (informações) | `564b4201` | 0x2a | read | nº de série ASCII (`ML2511013`) |
| | `564b4202` | 0x2c | read | firmware (`02 03 01` = 2.3.1) |
| | `564b4203` | 0x2e | read | `01` (modelo?) |
| | `564b4204` | 0x30 | read | uint64 (?) |
| | `564b4205` | 0x32 | read | ASCII `0820/2026` (calibração?) |
| | `564b4206` | 0x34 | read + notify | 1 byte (`0x4a` = 74 → bateria %?) |
| | `564b4207` | 0x37 | read + notify | 1 byte (estado) |
| | `564b4208` | 0x3a | read | 1 byte |
| | `564b4209` | 0x3c | read | ASCII `43IB` |
| `b0b54200-aa3f-45c4-8739-2e650838efd0` (configuração) | `b0b54201` | 0x3f | read/write/notify | 1 byte (`0x3b`) |
| | `b0b54202` | 0x42 | read/write | **relógio**: uint64 segundos (hora local gravada como se fosse UTC). O app grava ao conectar |
| | `b0b54203` | 0x44 | read/write/notify | 1 byte |
| `631c4200-e4ba-416d-bd4c-56740819941c` (**ensaio**) | `631c4201` | 0x48 | read/write | **nome do ensaio**: 11 bytes ASCII, completado com zeros |
| | `631c4202` | 0x4a | read + **notify** | **resultado** (ver abaixo) |
| | `631c4203` | 0x4d | write | **partida**: escrever `01` |
| `2afd4200-4d95-40c3-afb0-cedc8ea5b285` (memória) | `2afd4201` | 0x50 | read | uint32 quantidade de ensaios guardados (503) |
| | `2afd4202` | 0x52 | read/write | índice do ensaio a ler (uint32) |
| | `2afd4203` | 0x54 | read + notify | registro guardado (31 bytes) |

## Ensaio ao vivo (o que o APP JVM SPDA vai usar)

1. Ligar notificação em `631c4202` (CCCD `01 00`).
2. Escrever o nome do ensaio em `631c4201` (11 bytes ASCII, zeros no fim).
3. Escrever `01` em `631c4203`.
4. Em ~5,5–6 s chega a notificação em `631c4202`, 13 bytes:

| bytes | tipo | significado |
|---|---|---|
| 0–3 | uint32 | nº do ensaio no aparelho |
| 4 | uint8 | situação (0 = ok) |
| 5–8 | float32 | **resistência em mΩ** |
| 9–12 | float32 | **corrente de ensaio em A** |

Exemplos do registro:
- `0c020000 00 491fb043 5f49923f` → ensaio 524, **352,244 mΩ**, 1,143 A
- `0d020000 00 00803b45 1541c3b5` → ensaio 525, **3000** com corrente ≈ 0 → fora de escala / pontas abertas

## Registro guardado (`2afd4203`, 31 bytes)

| bytes | tipo | significado |
|---|---|---|
| 0–3 | uint32 | nº do ensaio |
| 4–7 | uint32 | data/hora (segundos; hora local gravada como UTC) |
| 8–11 | — | zeros |
| 12–18 | ASCII | nome (ex.: `TST-523`) |
| 19–22 | ? | 00/02/03… (modo/escala?) |
| 23–26 | float32 | resistência em mΩ |
| 27–30 | float32 | corrente em A |

## A confirmar no aparelho
- Unidade da resistência (mΩ) comparando com o visor.
- Valor de fora de escala (3000 mΩ?) e o byte de situação em caso de erro.
