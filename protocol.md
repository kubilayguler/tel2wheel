# Tel2Wheel protokolü

UDP üzerinden, bütün sayılar little-endian. Her paket `0x57` ('W') magic baytıyla başlar, ardından tip baytı gelir.

| Port  | Yön | Kullanım |
|-------|-----|----------|
| 47800 | telefon → PC | INPUT, PING (PC aynı porttan PONG döner) |
| 47801 | PC → yayın   | BEACON |

## INPUT (tip 1, 12 bayt)
| Ofset | Tip | Alan |
|---|---|---|
| 0 | u8  | magic `0x57` |
| 1 | u8  | `1` |
| 2 | u16 | sıra no (her pakette +1, taşınca başa döner) |
| 4 | i16 | direksiyon, -32767 (sol) … 32767 (sağ) |
| 6 | u8  | gaz 0–255 |
| 7 | u8  | fren 0–255 |
| 8 | u8  | bayraklar (ayrılmış, 0) |
| 9 | 3 B | ayrılmış |

- Her paket durumun tamamını taşır, kaybolan paket yeniden gönderilmez.
- PC, sıra numarası öncekinden eski olan paketi atar.
- PC 250 ms boyunca paket almazsa her şeyi nötre çeker. Telefon bu yüzden en geç 50 ms'de bir keepalive gönderir.

## PING / PONG (tip 2 / 3, 10 bayt)
`magic`, `tip`, `u64` gönderen zamanı (ns). PC tip baytını 3 yapıp paketi hemen geri yollar.

## BEACON (tip 4)
`magic`, `4`, `u16` girdi portu, ardından UTF-8 bilgisayar adı.

PC bunu saniyede bir, her IPv4 arayüzünün subnet broadcast adresine ve 255.255.255.255'e gönderir.
