# 🏰 GultenClaim — Chunk Claiming Plugin

> **v1.3.0 | Paper 1.21+ | Free & Open Source**

---

## 🇬🇧 English

### The ultimate chunk-based land protection plugin — lightweight, powerful, and feature-packed.

GultenClaim lets players protect their land by claiming chunks directly in-game. No complicated region tools, no GUIs — just clean, command-based protection with deep customization.

---

### ✨ Features

#### 🔒 Rock-Solid Protection
- Block break & placement protection in claimed chunks
- Lava & water bucket protection (no griefing with fluids!)
- Mob protection — hostile **and** passive mobs are shielded (can't kill zombies, animals, villagers, golems in someone else's claim)
- Entity ignite protection — fire arrows, flint & steel blocked for non-owners
- Painting, item frame & armor stand protection
- Piston griefing prevention across claim boundaries
- Explosion protection (TNT, creepers) — per-claim toggle
- Farmland trampling prevention

#### ⚙️ Per-Claim Toggles (owner-controlled)
| Toggle | Description |
|--------|-------------|
| `pvp` | Enable/disable PvP inside the claim |
| `fire` | Allow/prevent fire spread |
| `mob-spawning` | Block mob spawning + auto-remove hostile mobs every 3 seconds |
| `explosions` | Allow/prevent TNT & creeper damage |
| `public` | Make the claim open for everyone to build |

#### 🗺️ Chunk System
- **Contiguous claiming** — new claims must be adjacent to existing ones
- **Outpost claiming** — claim isolated chunks far from your base
- **Auto-claim mode** — walk around to claim chunks automatically
- **Chunk map** — see nearby claims in chat with `/claim map`
- **Claim list** — paginated list of all your claims
- **Claim info** — view settings and trusted players of the current chunk

#### ✈️ Claim Flight
- Toggle flight inside your own claim with `/claim fly`
- Automatically disabled when leaving the claim — no fly hacks!

#### 💰 Economy (Vault)
- Configurable price per chunk, per outpost
- Configurable refund amount on unclaim
- Limit players to a maximum number of claims

#### 🌍 Dynmap Integration
- All claims rendered on the Dynmap web map automatically
- Fully customizable marker colors per player with `/claim color`
- Shows owner name, trusted players, and claim type (outpost vs standard)

#### 🤝 Trust System
- Trust specific players to build in all your claims at once
- Untrust at any time — trusted players' flight is automatically revoked

#### 🔗 GultenClan Integration
- Clans can claim chunks as a group using `/clan claim`
- Clan members can build freely in clan-claimed territory
- Seamless event-based bridge with no performance overhead

#### 🛒 QuickShop-Hikari
- Shop creation is protected by claim — only owners/trusted can set up shops in a claim
- Buying & selling remains open to everyone (that's the point of a shop!)

#### 👑 Admin Tools
- OPs automatically bypass all claim protection — open chests, build anywhere
- `/claim bypass` — toggle admin bypass mode for non-OP admins
- `/claim adminunclaim` — remove any player's claim
- `/claim reload` — reload config & messages without restart

#### 🌐 Fully Localized
- Turkish (`messages_tr.yml`) out of the box
- Easy to add any language — just drop in a new `messages_XX.yml`

---

### 📋 Commands

| Command | Description |
|---------|-------------|
| `/claim` | Claim the chunk you're standing in |
| `/claim outpost` | Claim an isolated chunk (outpost) |
| `/claim unclaim` | Remove the claim under your feet |
| `/claim unclaimall` | Remove all your claims at once |
| `/claim trust <player>` | Give a player access to all your claims |
| `/claim untrust <player>` | Revoke a player's access |
| `/claim list` | See all your claimed chunks |
| `/claim info` | Info about the current chunk's claim |
| `/claim map` | Show a map of nearby claims in chat |
| `/claim show` | Visualize claim borders (particle effect) |
| `/claim auto` | Toggle auto-claim mode |
| `/claim fly` | Toggle flight in your own claim |
| `/claim setspawn` | Set a teleport point for this claim |
| `/claim tp [page]` | Teleport to one of your claims |
| `/claim toggle <setting>` | Toggle pvp / fire / mob-spawning / explosions / public |
| `/claim color <#RRGGBB\|reset>` | Change your Dynmap marker color (Premium) |
| `/claim bypass` | Admin: toggle protection bypass |
| `/claim adminunclaim` | Admin: force-remove any claim |
| `/claim reload` | Admin: reload config |

---

### 🔑 Permissions

| Permission | Default | Description |
|-----------|---------|-------------|
| `gultenclaim.use` | Everyone | Basic claim operations |
| `gultenclaim.fly` | OP | Flight in own claims |
| `gultenclaim.color` | OP | Custom Dynmap colors |
| `gultenclaim.admin` | OP | Admin commands & bypass |

---

### 📦 Dependencies

| Plugin | Required? | Purpose |
|--------|-----------|---------|
| **Paper 1.21+** | ✅ Required | Server platform |
| **Vault** | ⚡ Soft | Economy support |
| **Dynmap** | ⚡ Soft | Map integration |
| **QuickShop-Hikari** | ⚡ Soft | Shop protection |

> Works out of the box without Vault or Dynmap — economy features simply disable themselves.

---

### ⚡ Installation
1. Drop `GultenClaim-1.2.1.jar` into your `plugins/` folder
2. Restart the server
3. Edit `plugins/GultenClaim/config.yml` as needed
4. Done! Players can start claiming right away.

---
---

## 🇹🇷 Türkçe

### Hafif, güçlü ve özellik dolu chunk tabanlı arazi koruma plugini.

GultenClaim, oyuncuların chunk'ları talep ederek arazilerini korumasına olanak tanır. Karmaşık araçlara, GUI'lara gerek yok — sade, komut tabanlı koruma ve derin özelleştirme.

---

### ✨ Özellikler

#### 🔒 Güçlü Koruma
- Claim'deki blok kırma ve koyma koruması
- Lav & su kovası koruması (sıvı griefing yok!)
- Mob koruması — düşman **ve** pasif canlılar korunur (başkasının claim'indeki zombie, hayvan, köylü, golem'e saldıramazsın)
- Entity yakma koruması — ateşli ok, çakmaktaşı claim dışındakilere engelli
- Resim, çerçeve ve zirhstand koruması
- Piston griefing önleme
- Patlama koruması (TNT, creeper) — her claim için ayrı ayarlanabilir
- Tarla ezme koruması

#### ⚙️ Claim Başına Açma/Kapama Ayarları (Sadece sahibi değiştirebilir)
| Ayar | Açıklama |
|------|----------|
| `pvp` | Claim içinde PvP aç/kapat |
| `fire` | Ateş yayılmasına izin ver/engelle |
| `mob-spawning` | Mob spawn'u engelle + her 3 sn'de düşman moblara temizle |
| `explosions` | TNT & creeper hasarına izin ver/engelle |
| `public` | Claim'i herkese açık yap |

#### 🗺️ Chunk Sistemi
- **Bitişik claim** — yeni claimler mevcut olanlarla bitişik olmalı
- **Karakol (outpost)** — uzak yerlerde izole chunk claim etme
- **Otomatik claim modu** — yürüyerek chunk'ları otomatik claim et
- **Chunk haritası** — yakın claimleri sohbette `/claim map` ile gör
- **Claim listesi** — tüm claimlerinin sayfalı listesi
- **Claim bilgisi** — bulunduğun chunk'ın ayarlarını ve güvenilir oyuncuları gör

#### ✈️ Claim Uçuşu
- `/claim fly` ile kendi claim'inde uçuşu aç
- Claim'den çıkınca otomatik kapanır — uçuş hilesi yok!

#### 💰 Ekonomi (Vault)
- Her chunk için ayarlanabilir fiyat
- Unclaim'de geri ödeme miktarı ayarlanabilir
- Oyuncular için maksimum claim sayısı sınırı

#### 🌍 Dynmap Entegrasyonu
- Tüm claimler Dynmap haritasında otomatik gösterilir
- `/claim color` ile her oyuncu için özelleştirilebilir renk
- Sahip adını, güvenilen oyuncuları ve claim türünü gösterir

#### 🤝 Güven (Trust) Sistemi
- Belirli oyunculara tüm claimlerinde inşa izni ver
- İstediğin zaman güveni kaldır — trusted oyuncunun uçuşu da otomatik iptal olur

#### 🔗 GultenClan Entegrasyonu
- Klanlar `/clan claim` ile chunk'ları klan adına talep edebilir
- Klan üyeleri klan claim'lerinde serbestçe inşa edebilir

#### 🛒 QuickShop-Hikari
- Claim koruması mağaza kurma işlemine de uygulanır
- Satın alma & satış herkese açıktır

#### 👑 Admin Araçları
- OP oyuncular tüm claim korumasını otomatik aşar — sandık açabilir, her yerde inşa edebilir
- `/claim bypass` — admin bypass modunu aç/kapat
- `/claim adminunclaim` — herhangi bir claim'i sil
- `/claim reload` — config & mesajları yenile

#### 🌐 Tam Türkçe Dil Desteği
- `messages_tr.yml` ile sıfırdan Türkçe
- Yeni dil eklemek için `messages_XX.yml` dosyası oluştur

---

### 📋 Komutlar

| Komut | Açıklama |
|-------|----------|
| `/claim` | Durduğun chunk'ı claim et |
| `/claim outpost` | İzole bir chunk'ı karakol olarak claim et |
| `/claim unclaim` | Altındaki claim'i kaldır |
| `/claim unclaimall` | Tüm claimlerini kaldır |
| `/claim trust <oyuncu>` | Oyuncuya tüm claimlerinde izin ver |
| `/claim untrust <oyuncu>` | Oyuncunun iznini kaldır |
| `/claim list` | Tüm claim'lerini listele |
| `/claim info` | Mevcut chunk'ın claim bilgisini gör |
| `/claim map` | Yakındaki claimlerin haritasını göster |
| `/claim show` | Claim sınırlarını görselleştir |
| `/claim auto` | Otomatik claim modunu aç/kapat |
| `/claim fly` | Kendi claim'inde uçuşu aç/kapat |
| `/claim setspawn` | Bu claim için ışınlanma noktası belirle |
| `/claim tp [sayfa]` | Claimlerine ışınlan |
| `/claim toggle <ayar>` | pvp / fire / mob-spawning / explosions / public |
| `/claim color <#RRGGBB\|reset>` | Dynmap işaret renginizi değiştirin (Premium) |
| `/claim bypass` | Admin: koruma bypass modunu aç/kapat |
| `/claim adminunclaim` | Admin: herhangi bir claim'i zorla sil |
| `/claim reload` | Admin: config'i yenile |

---

### 🔑 İzinler

| İzin | Varsayılan | Açıklama |
|------|-----------|----------|
| `gultenclaim.use` | Herkes | Temel claim işlemleri |
| `gultenclaim.fly` | OP | Kendi claim'inde uçuş |
| `gultenclaim.color` | OP | Özel Dynmap rengi |
| `gultenclaim.admin` | OP | Admin komutları & bypass |

---

### 📦 Gereksinimler

| Plugin | Zorunlu? | Amaç |
|--------|----------|------|
| **Paper 1.21+** | ✅ Zorunlu | Sunucu platformu |
| **Vault** | ⚡ Opsiyonel | Ekonomi desteği |
| **Dynmap** | ⚡ Opsiyonel | Harita entegrasyonu |
| **QuickShop-Hikari** | ⚡ Opsiyonel | Mağaza koruması |

> Vault veya Dynmap olmadan da çalışır — ekonomi özellikleri otomatik devre dışı kalır.

---

### ⚡ Kurulum
1. `GultenClaim-1.2.1.jar` dosyasını `plugins/` klasörüne at
2. Sunucuyu yeniden başlat
3. `plugins/GultenClaim/config.yml` dosyasını isteğine göre düzenle
4. Hazır! Oyuncular hemen claim atmaya başlayabilir.
