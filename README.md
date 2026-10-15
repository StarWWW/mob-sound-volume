# Mob Sound Volume

Her mobun sesini ayrı ayrı ayarla: tamamen kapalıdan (%0) iki kat yükseğe (%200) kadar. İstersen bir mobun ses türlerini ve her bir sesini de ayrı ayrı ayarlayabilirsin.

*English: A client-side Fabric mod for Minecraft 26.1.2 that sets the volume of every mob separately (0–200%), with optional separate volumes for its idle, hurt, death, step and other sounds.*

## Özellikler

- **Mob başına ses seviyesi:** Listedeki her mob için %0 (kapalı) ile %200 arası bir kaydırıcı.
- **Ses türüne göre ayar:** Bir mobun yanındaki **+** düğmesiyle açılır: normal sesler, hasar, ölüm, adım, hareket (zıplama, yüzme, uçma...), saldırı, yeme-içme ve diğer.
- **Her ses ayrı:** Bir türün yanındaki **+** o türdeki her sesi kendi kaydırıcısıyla gösterir. Sesler oyunun kendi altyazı adlarıyla yazılır ("Zombi inler", "Zırhçı çalışır"...); aynı altyazıya sahip sesler (ör. yedi kurt türünün havlaması) tek satırdır.
- **Üç seviye çarpılır:** Gerçek ses seviyesi mob × tür × ses olarak hesaplanır ve her satırda gösterilir, ör. zombi %60, normal sesler %100 ve "Zombi inler" %25 ise inleme %15 çalar.
- **Son duyulanlar:** Son 2 dakikada sesi gelen moblar listenin en üstünde ♪ işaretiyle durur. Rahatsız eden bir ses duyunca menüyü aç, mob en üstte.
- **Sesi dinle (♪):** Her satırda ayarlanan seviyede bir örnek ses çalar. Yanındaki ok düğmesi o satırı %100'e döndürür.
- **Canlı uygulama:** Kaydırıcıyı oynatırken çalmakta olan sesler de hemen değişir. Tuşla oyun içinde açıldığında tek oyunculu oyunu durdurmaz, etraftaki mobların sesinin değiştiğini duyabilirsin.
- **Arama:** Mob adına, kimliğine (ör. `zombie`) veya ses adına göre arama. Ses adıyla aranınca (ör. "çalışır") eşleşen sesler hemen açılır gelir.
- **Kapalı mob altyazı göstermez:** %0'daki bir mobun sesi hiç başlatılmaz.
- **Gerçek yükseltme:** %100 üstü sesi gerçekten yükseltir (OpenAL'ın 1.0 kazanç sınırı o ses için kaldırılır), duyulma mesafesi değişmez.
- **Modlu moblar:** Diğer modların mobları da kendiliğinden listeye girer. Hem `<mod>:entity.<mob>.<ses>` hem de `entity.` olmadan yazılan `<mod>:<mob>_<ses>` (ör. `grizzly_bear_idle`) biçimleri tanınır.
- Açma yolları: Seçenekler > Müzik ve Sesler > **Mob Sesleri...** düğmesi, Mod Menu ve atanabilir bir tuş (varsayılan olarak boş, Kontroller'den ata).
- 19 dil (Türkçe, İngilizce, Almanca, Fransızca, İspanyolca, Rusça, Çince, Japonca, Korece...). Türkçede yüzde işareti başta yazılır: %50.

## Nasıl çalışıyor

Sunucuda bir mob ses çıkardığında istemciye sadece sesin adı ve konumu gelir, sesi hangi mobun çıkardığı gelmez. Bu yüzden mod sesleri adlarından tanır: Minecraft mob seslerini `entity.<mob>.<ses>` diye adlandırır (`entity.zombie.ambient`, `entity.cow.hurt`...). Varyantlar ve yavrular da doğru moba gider (`entity.wolf_big.growl` → kurt, `entity.baby_cat.purr` → kedi). 26.1.2'deki tüm sesler denenerek 90 mob/grup bulundu.

Ses, oyunun kendi Ana Ses ve kategori (Düşman Yaratıklar, Dost Yaratıklar...) ayarlarından **sonra** çarpılır, yani o ayarlarla birlikte çalışır.

## Bilinen sınırlamalar

Bazı moblar oyunun kendisinde başka bir mobun seslerini kullanır, bu yüzden ayrı ayarlanamazlar:

- Mağara örümceği → Örümcek sesleri
- Gezgin tüccarın laması → Lama sesleri
- Mooshroom'un normal sesleri → İnek sesleri
- Balıkların yüzme sesi (`entity.fish.swim`) hiçbir moba ait değil

"Birçok mobun ortak sesleri" bölümündeki **Genel** (yeme, su, patlama...) ve **Düşman moblar (ortak)** grupları birçok mobun paylaştığı sesleri kapsar.

## Gereksinimler

- Minecraft 26.1.2, Fabric Loader 0.19+, Java 25
- Fabric API 0.155.3+
- İsteğe bağlı: Mod Menu

## Derleme

Java 25 gerekir. Prism Launcher'ın indirdiği Java 25'i de kullanabilirsin:

```
set JAVA_HOME=%APPDATA%\PrismLauncher\java\java-runtime-epsilon
gradlew build
```

Hazır jar `build/libs/` klasörüne çıkar (`-sources` olmayan dosya). `gradlew build` ayrıca `src/test` altındaki JUnit testlerini de çalıştırır.

## Proje yapısı

```
src/main/java/mobvolume/
  MobVolumeMod.java              giriş noktası, tuş ataması, Müzik ve Sesler menüsündeki düğme
  SoundClassifier.java           ses adından mobu ve ses türünü bulur (oyundan bağımsız, test edilebilir)
  SoundType.java                 ses türleri: normal, hasar, ölüm, adım, diğer
  MobVolumeConfig.java           ayarlar (config/mobvolume.properties)
  MobSounds.java                 oyunun sesleri ile ayarları birleştirir, mob listesini çıkarır
  client/MobVolumeScreen.java    ayarlar ekranı (mob > tür > ses)
  client/ui/                     ayar ekranı iskeleti; üç modda aynıdır, ekranlar aynı görünür
  mixin/SoundEngineMixin.java    her mob sesinin seviyesini çarpar, kapalı sesleri başlatmaz
  mixin/ChannelMixin.java        %100 üstü için OpenAL kazanç sınırını kaldırır
  compat/ModMenuIntegration.java
src/main/resources/assets/mobvolume/lang/   19 dil dosyası (doğrudan düzenlenir)
src/test/java/mobvolume/         JUnit testleri (ses tanıma, ayarlar, dil dosyaları)
tools/MakeIcon.java              logoyu ve sıfırlama butonu simgesini üretir: java tools/MakeIcon.java
```

Dil dosyalarına yazı eklerken `LangFilesTest` her dilin İngilizce ile aynı anahtarlara sahip olduğunu ve yazılarda oyunun gösteremeyeceği bir `%` olmadığını kontrol eder (tek başına `%` yerine `%%` yazılır).

## Ayar dosyası

`config/mobvolume.properties`, yüzde olarak. Sadece değiştirilenler yazılır, gerisi %100'dür:

```
enabled=true
volume.minecraft\:zombie=50
volume.minecraft\:zombie/hurt=0
volume.minecraft\:cow/ambient=20
sound.minecraft\:entity.zombie.ambient=25
```

## Lisans

MIT, `LICENSE` dosyasına bak.
