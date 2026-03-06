# TenkoCall - 中間点呼アプリ

## 概要

トラックドライバーの中間点呼を簡易化する Android アプリ。
ボタン一つで「位置情報送信 → 電話発信」を実行する。

## システム構成

```
Android (TenkoCall)
  → alc-app (Nuxt 4 / Cloudflare Workers) /api/tenko-call/*
    → rust-alc-api (Axum / Cloud Run) /api/tenko-call/*
      → PostgreSQL (Supabase)
```

- **認証**: 電話番号ベース (端末から自動取得、取得不可なら使用不可)
- **バックエンド**: alc-app 経由で rust-alc-api を呼ぶ (直接叩かない)

## アプリフロー

1. 起動時: `READ_PHONE_NUMBERS` 権限で電話番号を自動取得
2. 取得不可 → 「この端末では使用できません」で終了
3. 取得成功 → サーバーに登録 (電話番号 + ドライバー名)
4. 「中間点呼」ボタン → GPS取得 → サーバー送信 → 登録済み発信先に `ACTION_CALL`

## 技術スタック

| カテゴリ | 技術 |
|---|---|
| 言語 | Kotlin 1.9.22 |
| ビルド | Gradle (KTS), AGP 8.2.2 |
| Min SDK | API 26 (Android 8.0) |
| Target SDK | API 34 |
| 通信 | Retrofit 2.9.0 + OkHttp 4.12.0 |
| 位置情報 | Play Services Location 21.1.0 |
| UI | ViewBinding + Material Design |
| アーキテクチャ | MVVM (ViewModel + LiveData) |

## ディレクトリ構成

```
app/src/main/java/com/example/tenkocall/
├── ui/
│   ├── MainActivity.kt       # メイン画面 (点呼ボタン)
│   └── MainViewModel.kt      # 通信・状態管理
├── data/
│   ├── model/TenkoModels.kt   # API リクエスト/レスポンス
│   └── remote/
│       ├── TenkoApi.kt        # Retrofit インターフェース
│       └── ApiClient.kt       # Retrofit シングルトン
└── util/
    └── PhoneNumberUtil.kt     # 電話番号取得ユーティリティ
```

## API エンドポイント (alc-app 経由)

- `POST /api/tenko-call/register` - ドライバー登録 (phone_number, driver_name)
  - レスポンス: { success, driver_id, call_number }
- `POST /api/tenko-call/tenko` - 点呼送信 (phone_number, driver_name, latitude, longitude)
  - レスポンス: { success, call_number }

## パーミッション

- `READ_PHONE_NUMBERS` / `READ_PHONE_STATE` - 電話番号取得
- `CALL_PHONE` - 電話発信
- `ACCESS_FINE_LOCATION` - GPS

## ビルド

```bash
cd /home/yhonda/android/TenkoCall   # 192.168.11.60
./gradlew assembleDebug
```

## API_BASE_URL

`app/build.gradle.kts` の `buildConfigField` で設定:
- 現在: `https://tenko-api.your-domain.workers.dev` (要変更)
- 本番: alc-app の Workers URL (`https://alc-app.m-tama-ramu.workers.dev`)

## 関連リポジトリ

| リポジトリ | パス (192.168.11.60) | 役割 |
|---|---|---|
| alc-app | `/home/yhonda/js/alc-app/web` | Nuxt フロント (API プロキシ) |
| rust-alc-api | `/home/yhonda/rust/rust-alc-api` | バックエンド API |

## 引き継ぎ: 残作業

### 完了済み
- [x] Android アプリ本体 (ビルド通る状態)
- [x] 192.168.11.60 にアップロード済み (`/home/yhonda/android/TenkoCall`)

### 残作業 (優先順)

#### 1. rust-alc-api にマイグレーション + ルート追加
場所: `/home/yhonda/rust/rust-alc-api`

**マイグレーション** (`migrations/030_tenko_call_drivers.sql`):
```sql
-- 中間点呼ドライバー (電話番号で識別)
CREATE TABLE IF NOT EXISTS alc_api.tenko_call_drivers (
    id SERIAL PRIMARY KEY,
    phone_number TEXT NOT NULL UNIQUE,
    driver_name TEXT NOT NULL,
    call_number TEXT,  -- 発信先電話番号 (管理者がWebで設定)
    tenant_id TEXT NOT NULL DEFAULT 'default',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 中間点呼位置情報ログ
CREATE TABLE IF NOT EXISTS alc_api.tenko_call_logs (
    id SERIAL PRIMARY KEY,
    driver_id INT NOT NULL REFERENCES alc_api.tenko_call_drivers(id),
    phone_number TEXT NOT NULL,
    driver_name TEXT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**ルート** (`src/routes/tenko_call.rs`):
- `POST /api/tenko-call/register` — ドライバー登録/更新、call_number を返す
- `POST /api/tenko-call/tenko` — 位置情報保存、call_number を返す
- 認証不要 (public route) — `routes/mod.rs` の `public_routes` に追加

#### 2. alc-app に Nitro サーバールート追加 (プロキシ)
場所: `/home/yhonda/js/alc-app/web`

`server/api/tenko-call/register.post.ts`:
```ts
export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig()
  const body = await readBody(event)
  return $fetch(`${config.public.apiBase}/api/tenko-call/register`, {
    method: 'POST',
    body,
  })
})
```

`server/api/tenko-call/tenko.post.ts`: 同様にプロキシ

#### 3. API_BASE_URL を変更
`app/build.gradle.kts` の `buildConfigField`:
```
"https://alc-app.m-tama-ramu.workers.dev"
```

#### 4. アプリアイコン差し替え (任意)
