# Tel2Wheel

Android telefonunu **Euro Truck Simulator 2** ve **American Truck Simulator** için direksiyon ve analog gaz/fren pedalı olarak kullan. Wi-Fi/hotspot üzerinden çalışır; tipik gecikme 1–3 ms.

İki mod var: telefonu çevirerek sürdüğün **eğim direksiyonu** ve Dr. Driving tarzı **dokunmatik direksiyon**.

## Kurulum
Gerekenler: Windows 10/11 ve Android 8+.

1. PC'ye **[ViGEmBus](https://github.com/nefarius/ViGEmBus/releases/latest)** sürücüsünü kur. Bunu bir kere yapman yeterli.
2. **[Releases](../../releases/latest)** sayfasından iki dosyayı indir:
   - `Tel2Wheel-Server.exe` → PC'ye
   - `Tel2Wheel.apk` → telefona. Kurarken "bilinmeyen kaynaklar" iznini ver.
3. PC'de `Tel2Wheel-Server.exe`'yi çalıştır.
   - İlk açılışta güvenlik duvarı için yönetici onayı ister.
   - SmartScreen uyarısı çıkarsa *Ek bilgi → Yine de çalıştır*'ı seç.
4. Telefonu ve PC'yi aynı ağa bağla. Telefonun hotspot'u da olur, Windows *Mobil etkin nokta* da; 5 GHz önerilir.
5. Uygulamayı aç ve bir mod seç. PC otomatik bulunur.

## Oyunda
*Seçenekler → Kontroller* bölümünde Xbox denetleyicisini seçip şu atamaları yap:

| Kontrol | Atama |
|---|---|
| Direksiyon | Sol çubuk X (Joy X) |
| Gaz | Sağ tetik (RT) |
| Fren | Sol tetik (LT) |

Modlar, ayarlar ve sorun giderme için **[rehbere](docs/REHBER.md)** bak.
