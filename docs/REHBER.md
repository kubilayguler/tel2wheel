# Tel2Wheel rehberi

Kurulum için [README](../README.md)'ye bak. Bu sayfa modları, ayarları ve sorun gidermeyi anlatır.

## Oyun ayarları (ETS2 / ATS)
*Seçenekler → Kontroller* menüsünde:
- Kontrolcü olarak **Klavye + Xbox Controller** (ya da "Gamepad, joystick") seç.
- Direksiyon → **Joy X Ekseni** (Sol çubuk X)
- Gaz → **Sağ tetik (RT)**
- Fren → **Sol tetik (LT)**
- Oyundaki direksiyon **doğrusalsızlığını 0'a yakın** tut. Böylece direksiyon hissini uygulamanın ayarları belirler, oyun üstüne kendi eğrisini eklemez. Oyundaki hassasiyeti başlangıçta ortada bırak, ince ayarı uygulamadan yap.

## Kullanım
Uygulama ana menüyle açılır. Üstte bağlantı durumu görünür; buradan iki moddan birini seçersin. **MENÜ** düğmesi ya da geri tuşu menüye döndürür.

### Pedallar (iki modda da)
- Parmağın ne kadar yukarıdaysa pedal o kadar basılı olur. **Pedalın dikey ortası ve üstü %100**'dür; tam boy pedalda bu, ekranın ortası demektir. Pedalın üzerindeki ince çizgi bu sınırı gösterir.
- Ayarlardaki *Dijital pedallar* seçeneğiyle dokunmak doğrudan %100 basar.

### Eğim direksiyonu
Telefonu direksiyon gibi çevirirsin. Solda fren, sağda gaz.
- **Direksiyon açısı:** Sanal direksiyonun kilitten kilide açısı. Varsayılan 270°, yani tam kilitte ±135°.
- **Hassasiyet:** Telefonun dönüşünü büyüten çarpan. Varsayılan 1,5x; 270° ile birlikte telefonu ±90° çevirince tam kilit.
- **Merkez yardımı:** Telefon düz konuma yakınken (yaklaşık ±5°) direksiyon yumuşakça ortaya çekilir.
- **Toparlama:** Telefonu düzeltirken direksiyon ortaya daha hızlı döner. Tekrar çevirince sıçramadan normal tepkiye döner.
- **ORTALA:** Telefonun o anki duruşunu düz kabul eder.
- **KİLİT:** Direksiyonu o anki açıda dondurur. Telefonu rahat bir pozisyona getir, **KİLİDİ AÇ**'a bas; direksiyon kaldığı yerden, telefonun yeni duruşuyla devam eder.

### Dokunmatik direksiyon (Dr. Driving tarzı)
Solda ekrandaki direksiyon, sağda fren ve gaz pedalı. Parmağını direksiyonun üzerine ya da hemen çevresine koyup merkezi etrafında çevirirsin.
- **Direksiyon açısı:** Varsayılan 900° (tam kilitte ±450°, birden fazla tur).
- **Hassasiyet:** Parmağının bir derecelik dönüşü direksiyonu kaç derece çevirsin. Varsayılan 1,5x; tam kilit için parmağını 300° çevirmen yeter.
- **Ortaya dönüş hızı:** Parmağını çekince direksiyon bu hızla ortaya döner. Varsayılan 720°/sn. 0 yaparsan bırakıldığı açıda kalır.
- **KİLİT:** Direksiyonu o anki açıda tutar; ortaya dönmez ve dokunmaya tepki vermez.

### Yerleşimi özelleştirme
*AYAR → Yerleşimi düzenle* ile kontrolleri istediğin yere taşıyıp boyutlandırabilirsin. Dokunmatik modda direksiyon, fren ve gaz; eğim modunda fren ve gaz düzenlenebilir.
- **Taşımak için:** Kontrolü tek parmakla sürükle.
- **Boyutlandırmak için:** Köşedeki turuncu noktayı çek ya da kontrolü iki parmakla sıkıştır/aç.
- **KAYDET** yeni yerleşimi saklar, **VARSAYILAN** fabrika yerleşimine döndürür, **İPTAL** (ya da geri tuşu) değişiklikleri geri alır.
- Her mod kendi yerleşimini ekran boyutuna oranlı olarak saklar.

Ekran her zaman yatay kalır; sert dönüşlerde ters de dönmez.

## En düşük gecikme için
- **5 GHz hotspot kullan.** Hem daha az parazit hem daha kısa hava süresi demek.
- **PC Wi-Fi güç tasarrufunu kapat:** *Aygıt Yöneticisi → Ağ bağdaştırıcısı → Gelişmiş* bölümünde "Power Saving Mode / MIMO Power Save" ayarını kapat. *Güç seçenekleri → Kablosuz bağdaştırıcı* ayarını "En yüksek performans" yap.
- **Telefonda pil tasarrufu modunu kapat.** Uygulama zaten şunları açıyor:
  - Wi-Fi düşük gecikme kilidi (Android 10+)
  - WMM "voice" önceliği (DSCP EF)
  - Sürekli performans modu
- **Paketleri izle:** Sunucu konsolunda paket/sn değeri 200'ün üzerinde, kayıp değeri sıfıra yakın olmalı.
- **RTT'yi izle:** Telefondaki RTT, gidiş-dönüş süresini gösterir. Tek yön gecikme bunun yaklaşık yarısıdır.

## Sorun giderme
| Belirti | Çözüm |
|---|---|
| "PC aranıyor…" | Telefon ve PC aynı ağda mı? Değilse *BAĞLANTI AYARLARI*'ndan PC'nin IP adresini elle gir; sunucu açılışta adreslerini listeler. Bazı hotspot'lar broadcast'i engeller. |
| "… yanıt vermiyor" | Sunucu çalışmıyor ya da güvenlik duvarı izni verilmedi. Sunucuyu bir kez *Yönetici olarak çalıştır*. |
| Direksiyon ters | Ayarlardan *Yönü ters çevir* seçeneğini aç. |
| Direksiyon titriyor | *Titreme filtresi*ni aç ya da ölü bölgeyi artır. |
| Uygulama kapanınca gaz basılı kalıyor | Olmamalı: PC 250 ms içinde her şeyi bırakır. |

## Kaynaktan derleme
Gerekenler: .NET 8 SDK, JDK 17+, Android SDK.

```powershell
powershell -ExecutionPolicy Bypass -File .\build-release.ps1
```
`dist\` klasörüne `Tel2Wheel-Server.exe` (tek dosya, .NET gerektirmez) ve `Tel2Wheel.apk` üretilir.

GitHub'da `v1.0.0` gibi bir etiket gönderince `.github/workflows/release.yml` ikisini derleyip Releases sayfasına ekler.

Sunucuyu telefon olmadan denemek için `Tel2Wheel-Server.exe --selftest` çalıştır ve `joy.cpl` ile izle. Paket formatı: [protocol.md](../protocol.md).
