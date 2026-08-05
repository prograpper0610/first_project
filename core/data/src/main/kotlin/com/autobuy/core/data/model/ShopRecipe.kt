package com.autobuy.core.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 쇼핑몰 자동화 레시피 (UI Recipe) 정의.
 *
 * 향후 어떤 사이트/앱이 추가되더라도 확장 가능하도록 설계되었습니다.
 * - customData: 쇼핑몰별 임의의 확장 속성 (Key-Value)
 * - customKey: 특정 Step에서 사용자 정의 필드 값을 동적으로 주입
 */
@JsonClass(generateAdapter = true)
data class ShopRecipe(
    @Json(name = "shopId") val shopId: String,
    @Json(name = "shopName") val shopName: String,
    @Json(name = "packageName") val packageName: String,     // 앱 패키지명 or "browser"
    @Json(name = "version") val version: String,
    @Json(name = "entryUrl") val entryUrl: String? = null,   // Mode A: 상품 상세 URL 템플릿
    @Json(name = "searchUrl") val searchUrl: String? = null, // Mode B: 검색 URL 템플릿
    @Json(name = "steps") val steps: List<RecipeStep>,
    @Json(name = "queueDetectors") val queueDetectors: List<NodeSelector> = emptyList(),
    @Json(name = "paymentFinalTrigger") val paymentFinalTrigger: NodeSelector,
    @Json(name = "antiBotKeywords") val antiBotKeywords: List<String> = listOf(
        "보안문자", "캡차", "CAPTCHA", "자동입력 방지", "robot", "verify"
    ),
    @Json(name = "customData") val customData: Map<String, String> = emptyMap() // 확장 속성
)

/**
 * 단일 자동화 액션 스텝.
 */
@JsonClass(generateAdapter = true)
data class RecipeStep(
    @Json(name = "stepId") val stepId: String,
    @Json(name = "description") val description: String = "",
    @Json(name = "action") val action: StepAction,
    @Json(name = "selectors") val selectors: List<NodeSelector>,  // 다중 fallback selector
    @Json(name = "inputType") val inputType: InputType? = null,   // 입력 타입 (자동 값 주입용)
    @Json(name = "customKey") val customKey: String? = null,      // InputType.CUSTOM_FIELD 시 참조할 Key
    @Json(name = "staticValue") val staticValue: String? = null,  // 정적 입력값
    @Json(name = "waitAfterMs") val waitAfterMs: Long = 300,
    @Json(name = "retryCount") val retryCount: Int = 3,
    @Json(name = "retryIntervalMs") val retryIntervalMs: Long = 500,
    @Json(name = "isOptional") val isOptional: Boolean = false,
    @Json(name = "scrollToView") val scrollToView: Boolean = false
)

/**
 * UI 노드 셀렉터 (다중 전략 지원).
 */
@JsonClass(generateAdapter = true)
data class NodeSelector(
    @Json(name = "type") val type: SelectorType,
    @Json(name = "value") val value: String,
    @Json(name = "parentClass") val parentClass: String? = null, // 부모 노드 필터
    @Json(name = "index") val index: Int = 0                     // 동일 노드 다수 시 인덱스
)

enum class SelectorType {
    RESOURCE_ID,    // com.shop:id/btn_buy
    TEXT,           // 텍스트 완전 일치
    TEXT_CONTAINS,  // 텍스트 부분 포함
    CLASS_NAME,     // android.widget.Button
    CONTENT_DESC,   // contentDescription
    XPATH_LIKE,     // 계층 경로
    BOUNDS          // 화면 좌표 (최후 수단)
}

enum class StepAction {
    CLICK,
    LONG_CLICK,
    SET_TEXT,
    CLEAR_TEXT,
    SCROLL_DOWN,
    SCROLL_UP,
    WAIT_FOR_ELEMENT,  // 요소가 나타날 때까지 대기
    WAIT_FOR_DISAPPEAR,// 요소가 사라질 때까지 대기 (대기열 해소)
    SELECT_OPTION,     // Spinner / 드롭다운 선택
    SWIPE,
    JS_INJECT          // WebView JS 실행
}

enum class InputType {
    RECIPIENT_NAME,    // 수령인 이름
    PHONE_NUMBER,      // 연락처
    ADDRESS,           // 주소
    ADDRESS_DETAIL,    // 상세 주소
    POSTAL_CODE,       // 우편번호
    CARD_NUMBER,       // 카드번호
    CARD_EXPIRY,       // 유효기간
    CARD_CVV,          // CVV
    CARD_HOLDER,       // 카드 소유자명
    QUANTITY,          // 수량 (staticValue 사용)
    CAPTCHA_TEXT,      // CAPTCHA 입력 (OCR 결과 자동 주입)
    CUSTOM_FIELD       // 동적 확장 커스텀 필드 (customKey 기반 매칭)
}
