# [작업 상세 내역 1] AutoBuy Assistant 코어 시스템 및 구조 구현

> **작성 일시**: 2026-08-05
> **프로젝트 경로**: `C:\AutoBuyAssistant`
> **작성자**: Antigravity AI 수석 아키텍트

---

## 1. 개요 (Overview)

본 작업은 Android 온디바이스(On-Device) 로컬 자동구매 매크로/보조 앱 **AutoBuy Assistant**의 1차 코어 아키텍처 및 시스템 전체 구현을 포함합니다.
중앙 서버 의존성 없이 단말 내부에서 100% 동작하며, 사용자의 개인정보(배송지, 카드정보 등 PII)는 Android Keystore 기반 AES-256-GCM 알고리즘으로 암호화 저장됩니다. 최종 결제 단계에서는 Handover Layer를 통해 사용자에게 제어권을 안전하게 이관합니다.

---

## 2. 생성 및 변경된 전체 파일 목록 (Walkthrough Files)

### 🔧 빌드 설정 및 매니페스트 (5개)
| 파일 | 설명 |
|------|------|
| [settings.gradle.kts](file:///C:/AutoBuyAssistant/settings.gradle.kts) | 멀티모듈 구조 선언 (`:app`, `:core:common`, `:core:data`, `:core:security`, `:core:accessibility`, `:feature:config`, `:feature:dashboard`, `:feature:recorder`) |
| [build.gradle.kts (root)](file:///C:/AutoBuyAssistant/build.gradle.kts) | 루트 플러그인 선언 (`apply false`) |
| [libs.versions.toml](file:///C:/AutoBuyAssistant/gradle/libs.versions.toml) | 30+ 라이브러리 및 플러그인 버전 카탈로그 |
| [proguard-rules.pro](file:///C:/AutoBuyAssistant/app/proguard-rules.pro) | R8/ProGuard 난독화 보호 규칙 |
| [AndroidManifest.xml](file:///C:/AutoBuyAssistant/app/src/main/AndroidManifest.xml) | 접근성, 포그라운드 서비스(`specialUse`), WakeLock, 알림, 화면캡처 권한 선언 |

### 🔐 보안 레이어 `:core:security` (3개)
| 파일 | 역할 |
|------|------|
| [KeystoreManager.kt](file:///C:/AutoBuyAssistant/core/security/src/main/kotlin/com/autobuy/core/security/KeystoreManager.kt) | Android Keystore Hardware-backed TEE/SE 마스터키 관리 |
| [AesCryptoEngine.kt](file:///C:/AutoBuyAssistant/core/security/src/main/kotlin/com/autobuy/core/security/AesCryptoEngine.kt) | AES-256-GCM 암호화/복호화 (IV+인증태그 무결성 검증, CharArray 제로화) |
| [SecureVault.kt](file:///C:/AutoBuyAssistant/core/security/src/main/kotlin/com/autobuy/core/security/SecureVault.kt) | PII 배송지 및 카드 정보 암호화 저장소 |

### 💾 데이터 레이어 `:core:data` (5개)
| 파일 | 역할 |
|------|------|
| [AutoBuyDatabase.kt](file:///C:/AutoBuyAssistant/core/data/src/main/kotlin/com/autobuy/core/data/db/AutoBuyDatabase.kt) | Room DB 정의 (4개 테이블) |
| [Entities.kt](file:///C:/AutoBuyAssistant/core/data/src/main/kotlin/com/autobuy/core/data/db/entity/Entities.kt) | ProfileEntity, SecureRecordEntity, ExecutionLogEntity, ConfigurationEntity |
| [Daos.kt](file:///C:/AutoBuyAssistant/core/data/src/main/kotlin/com/autobuy/core/data/db/dao/Daos.kt) | ProfileDao, SecureRecordDao, ExecutionLogDao, ConfigurationDao (Flow 지원) |
| [ShopRecipe.kt](file:///C:/AutoBuyAssistant/core/data/src/main/kotlin/com/autobuy/core/data/model/ShopRecipe.kt) | Recipe JSON 스키마 (6 Selector, 10 Action) |
| [coupang_recipe.json](file:///C:/AutoBuyAssistant/core/data/src/main/kotlin/com/autobuy/core/data/assets/recipes/coupang_recipe.json) | 쿠팡 내장 자동화 레시피 v2.1 |

### ⚙️ 자동화 코어 `:core:accessibility` (10개)
| 파일 | 역할 |
|------|------|
| [AutoBuyAccessibilityService.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/AutoBuyAccessibilityService.kt) | 메인 접근성 서비스 (화면 이벤트 수신) |
| [AutoBuyForegroundService.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/AutoBuyForegroundService.kt) | 포그라운드 서비스 (WakeLock, START_STICKY, 상태 알림) |
| [AutoBuyOrchestrator.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/engine/AutoBuyOrchestrator.kt) | **10-State 코어 상태 머신** (전체 매크로 프로세스 제어) |
| [NodeScanner.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/engine/NodeScanner.kt) | UI 노드 탐색 엔진 (6가지 Selector 전략 Fallback, 비동기 대기) |
| [ActionExecutor.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/engine/ActionExecutor.kt) | 클릭/입력/스와이프/JS 실행 (인간 유사 랜덤 딜레이 적용) |
| [RecipeExecutor.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/engine/RecipeExecutor.kt) | Recipe 스텝 실행기 (SecureVault 값 자동 주입) |
| [NtpTimeSyncer.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/engine/NtpTimeSyncer.kt) | NTP 시간 동기화 (4개 서버 멀티쿼리 + RTT 지연 보정) |
| [QueueHandler.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/module/QueueHandler.kt) | Module 3: 대기열 감지/처리 (키워드 DB, 셀렉터, URL 3중 감지) |
| [AntiBotInterceptor.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/module/AntiBotInterceptor.kt) | Module 4: Anti-Bot Interceptor (ML Kit OCR + 1.5초 Fallback) |
| [HandoverLayer.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/module/HandoverLayer.kt) | Module 5: Handover Layer (긴급 알림 + 진동 + 앱 포커스) |

### 📱 UI 레이어 `:app`, `:feature` (7개)
| 파일 | 역할 |
|------|------|
| [MainActivity.kt](file:///C:/AutoBuyAssistant/app/src/main/kotlin/com/autobuy/app/MainActivity.kt) | Activity 진입점, Compose Navigation Host, 딥링크 핸들러 |
| [AutoBuyApplication.kt](file:///C:/AutoBuyAssistant/app/src/main/kotlin/com/autobuy/app/AutoBuyApplication.kt) | Hilt Application 클래스 |
| [Theme.kt](file:///C:/AutoBuyAssistant/app/src/main/kotlin/com/autobuy/app/ui/theme/Theme.kt) | 프리미엄 다크 테마 (딥 퍼플 + 사이안/틸 액센트) |
| [Typography.kt](file:///C:/AutoBuyAssistant/app/src/main/kotlin/com/autobuy/app/ui/theme/Typography.kt) | Outfit 타이포그래피 스타일 |
| [DashboardScreen.kt](file:///C:/AutoBuyAssistant/feature/dashboard/src/main/kotlin/com/autobuy/feature/dashboard/DashboardScreen.kt) | 대시보드 Compose UI (펄스 애니메이션, 대기열 카드, 실시간 로그 스트림) |
| [DashboardViewModel.kt](file:///C:/AutoBuyAssistant/feature/dashboard/src/main/kotlin/com/autobuy/feature/dashboard/DashboardViewModel.kt) | 대시보드 ViewModel |
| [ConfigScreen.kt](file:///C:/AutoBuyAssistant/feature/config/src/main/kotlin/com/autobuy/feature/config/ConfigScreen.kt) | 설정 화면 UI 스텁 |
| [RecorderScreen.kt](file:///C:/AutoBuyAssistant/feature/recorder/src/main/kotlin/com/autobuy/feature/recorder/RecorderScreen.kt) | Smart Recorder UI 스텁 |

---

## 3. 주요 구현 모듈 및 상세 기술 사양

### 3.1. 프로젝트 기반 및 멀티모듈 구조 (`Phase 0`)
- **멀티모듈 아키텍처 구축**:
  - `:app` — 애플리케이션 진입점, Navigation Graph, Theme
  - `:core:security` — Keystore & AES-256-GCM 암호화 모듈
  - `:core:data` — Room DB, Entity, DAO, Recipe JSON 스키마
  - `:core:accessibility` — AccessibilityService, 상태머신, OCR, 대기열, Handover 모듈
  - `:core:common` — 공통 유틸리티
  - `:feature:config` — 설정 UI 모듈
  - `:feature:dashboard` — 실시간 모니터링 대시보드 UI 모듈
  - `:feature:recorder` — Smart Recorder UI 모듈
- **의존성 중앙 관리**: `gradle/libs.versions.toml` (Version Catalog) 적용
- **R8/ProGuard 룰**: `app/proguard-rules.pro` (접근성 서비스, 보안 모듈, Room, ML Kit 코드 보호)

### 3.2. 온디바이스 보안 암호화 레이어 (`Phase 1`)
- **[KeystoreManager.kt](file:///C:/AutoBuyAssistant/core/security/src/main/kotlin/com/autobuy/core/security/KeystoreManager.kt)**:
  - Android Keystore Hardware-backed TEE/SE 환경에 AES-256 마스터키 생성 및 관리.
- **[AesCryptoEngine.kt](file:///C:/AutoBuyAssistant/core/security/src/main/kotlin/com/autobuy/core/security/AesCryptoEngine.kt)**:
  - AES-256-GCM 모드 적용 (12-byte IV + 128-bit Auth Tag 무결성 검증).
  - PII 메모리 보호: 카드번호/CVV 등 민감 정보 처리 시 `String` 대신 `CharArray`를 사용하고 입력 즉시 `fill('\u0000')`으로 메모리 제로화.
- **[SecureVault.kt](file:///C:/AutoBuyAssistant/core/security/src/main/kotlin/com/autobuy/core/security/SecureVault.kt)**:
  - 배송지(수령인, 연락처, 주소) 및 결제 수단(카드번호, 유효기간, CVV) 암호화 저장/복호화 API 제공.

### 3.3. 데이터베이스 및 레시피 스키마 (`Phase 2`)
- **[AutoBuyDatabase.kt](file:///C:/AutoBuyAssistant/core/data/src/main/kotlin/com/autobuy/core/data/db/AutoBuyDatabase.kt)**:
  - Room DB 4종 엔티티 구축 (`ProfileEntity`, `SecureRecordEntity`, `ExecutionLogEntity`, `ConfigurationEntity`).
  - Kotlin Coroutines `Flow` 지원 DAO 4종 구현.
- **[ShopRecipe.kt](file:///C:/AutoBuyAssistant/core/data/src/main/kotlin/com/autobuy/core/data/model/ShopRecipe.kt)**:
  - 쇼핑몰 자동화 JSON 스키마 정의 (6가지 Selector 전략: `RESOURCE_ID`, `TEXT`, `TEXT_CONTAINS`, `CONTENT_DESC`, `CLASS_NAME`, `BOUNDS`).
  - 10가지 Action 정의 (`CLICK`, `SET_TEXT`, `SCROLL_DOWN`, `SWIPE`, `JS_INJECT` 등).
- **[coupang_recipe.json](file:///C:/AutoBuyAssistant/core/data/src/main/kotlin/com/autobuy/core/data/assets/recipes/coupang_recipe.json)**:
  - 쿠팡 앱 바로구매→배송지→결제수단 선택→결제하기 내장 레시피 작성.

### 3.4. 접근성 코어 및 자동화 엔진 (`Phase 3`)
- **[AutoBuyAccessibilityService.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/AutoBuyAccessibilityService.kt)**:
  - 모든 접근성 이벤트를 수신하고 노드 스트림을 Orchestrator로 이관.
- **[AutoBuyForegroundService.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/AutoBuyForegroundService.kt)**:
  - `WakeLock` + `START_STICKY` 적용으로 화면 OFF 상태에서도 매크로 포그라운드 유지.
- **[NodeScanner.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/engine/NodeScanner.kt)**:
  - 다중 Selector Fallback 탐색 및 비동기 노드 출현/소멸 대기 로직.
- **[ActionExecutor.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/engine/ActionExecutor.kt)**:
  - 노드 클릭, 텍스트 주입, 좌표 기반 스와이프, WebView JS 주입 지원.
  - 안티봇 탐지 방지를 위한 인간 유사 랜덤 딜레이 (50~200ms) 적용.
- **[NtpTimeSyncer.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/engine/NtpTimeSyncer.kt)**:
  - 4개 NTP 서버 멀티 쿼리 + 중앙값 선택 + RTT 네트워크 지연 보정으로 서버시간 정밀 동기화.

### 3.5. 상태 머신 및 핵심 기능 모듈 (`Phase 4 & 5`)
- **[AutoBuyOrchestrator.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/engine/AutoBuyOrchestrator.kt)**:
  - 10개 상태를 지원하는 코어 상태 머신 (`Idle`, `Waiting`, `ModeAPending`, `ModeBPolling`, `PurchaseStarted`, `QueueHandling`, `FormFilling`, `AntiBotDetected`, `Handover`, `Complete`, `Error`).
- **[QueueHandler.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/module/QueueHandler.kt)** (Module 3):
  - Recipe 셀렉터 + 공통 키워드 DB + URL 패턴 3중 대기열 감지 및 해소 모니터링.
- **[AntiBotInterceptor.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/module/AntiBotInterceptor.kt)** (Module 4):
  - Google ML Kit OCR (한국어/영문) 연동으로 CAPTCHA 자동 해독. 1.5초 타임아웃 초과 시 사용자 Fallback 진동/알림.
- **[HandoverLayer.kt](file:///C:/AutoBuyAssistant/core/accessibility/src/main/kotlin/com/autobuy/core/accessibility/module/HandoverLayer.kt)** (Module 5):
  - 최종 결제 버튼 직전 자동화 일시정지, 긴급 알림 + 진동 패턴 발생, 앱 최전면 포커스 이관.

---

## 4. 핵심 아키텍처 결정 사항 (Key Architectural Decisions)

### 4.1. 보안 설계 (Security Design)
- **이중 암호화 레이어**: Android Keystore(TEE 하드웨어) + AES-256-GCM 암호화
- **CharArray 메모리 보안**: 카드번호/CVV를 `String` 대신 `CharArray`로 처리하여 Heap 메모리 잔존 및 GC 추적 차단
- **즉시 메모리 제로화**: `encryptSensitive()`, `decryptSensitive()` 사용 완료 직후 `fill('\u0000')`으로 덮어씀

### 4.2. 상태 머신 흐름 (State Machine Flow)
```
IDLE → WAITING → [MODE_A | MODE_B] → PURCHASE_STARTED →
QUEUE_HANDLING → FORM_FILLING → ANTIBOT_CHECK → HANDOVER → COMPLETE
```
- `sealed class AutoBuyState`로 10개 상태를 비동기 데이터 플로우(`StateFlow`)로 관리하여 UI와 동기화

### 4.3. Anti-Bot 및 딜레이 전략 (Anti-Bot Evasion)
- ML Kit OCR (한국어 우선) → Tesseract (Fallback) → 1.5초 타임아웃 → 사용자 수동 해결 알림
- `AccessibilityService.takeScreenshot()` (Android 11+) 무음 캡처 활용
- 노드 클릭 시 50~200ms의 인간 유사 랜덤 딜레이를 부여하여 타이밍 패턴 기반 봇 감지 우회

---

## 5. 다음 단계 및 잔여 과제 (Next Steps / Remaining Tasks)

| Phase | 남은 과제 |
|-------|-----------|
| **Phase 2** | `ConfigScreen` 완전 구현 (DateTimePicker, 폼 입력 validation, PII 등록 UI) |
| **Phase 2** | 네이버쇼핑, 무신사 추가 내장 Recipe JSON 작성 |
| **Phase 4** | Mode B (Monitoring Polling) 전용 고속 폴링 루프 독립 모듈화 |
| **Phase 5** | Smart Recorder 터치 이벤트 수집 로직 (`TouchEventRecorder`) 구현 |
| **Phase 6** | 대기열 감지 예외 시나리오 필드 테스트 |
| **Phase 9** | 대시보드와 ConfigScreen 설정 연결 및 실제 구매 세션 시작 로직 바인딩 |
| **Phase 10** | E2E 실기기 테스트, 메모리/배터리 최적화, 보안 감사 |

---

## 6. 실행 및 테스트 가이드 (Execution Guide & Cautions)

### 6.1. Android Studio에서 실행하기
1. Android Studio 실행
2. **File → Open** → `C:\AutoBuyAssistant` 선택
3. Gradle Sync 완료 후 빌드
4. **실제 기기** 연결 후 앱 실행 (접근성 서비스 기능은 에뮬레이터에서 제한될 수 있으므로 실기기 추천)

> [!IMPORTANT]
> **필수 접근성 권한 설정**: 앱 최초 설치 후 **안드로이드 설정 → 접근성 → 설치된 앱 → AutoBuy 자동화 서비스**를 활성화해야 자동구매가 정상 작동합니다.

> [!CAUTION]
> Tesseract4Android Fallback OCR 사용 시 한국어 학습 데이터(`kor.traineddata`) 파일은 필요 시 `assets/tessdata/` 경로에 사전 수동 배치해야 합니다.

---

## 7. 향후 작업 문서 관리 규칙 (Future Work Rule)

추가 요청 사항이 있을 경우, 본 규칙에 따라 작업 내역을 작성 및 보관합니다:
1. 매 작업 단위 완료 시 `C:\AutoBuyAssistant` 폴더 하단에 `task_detail2.md`, `task_detail3.md` 등의 순번 파일로 상세 보고서를 생성합니다.
2. 모든 작업 문서에는 본 문서와 동일하게 **전체 변경 파일 목록(링크/설명 포함)**, **기술 상세**, **아키텍처 결정 사항**, **다음 단계**, **실행 가이드 및 주의사항** 등 walkthrough 항목을 기본으로 포함합니다.
