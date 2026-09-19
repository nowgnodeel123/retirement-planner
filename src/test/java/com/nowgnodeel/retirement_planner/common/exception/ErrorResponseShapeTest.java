package com.nowgnodeel.retirement_planner.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 에러 응답이 사용자에게 보여도 되는 모양인지 고정한다.
 *
 * WHY: 프론트(lib/api.ts)는 에러 본문에서 `message ?? error` 순으로 메시지를 골라
 * 그대로 화면에 띄운다. 그래서 백엔드가 Spring 기본 응답
 * {"timestamp","status","error":"Bad Request","path"}을 흘리면 한국어 UI 한가운데에
 * 영어 "Bad Request"가 그대로 떴다 — 실제로 생년월일을 "19660315"처럼 ISO가 아닌
 * 형식으로 보내면 재현됐다.
 *
 * 본문을 객체로 만들기 전에 실패하는 경로(깨진 JSON, 날짜 파싱 실패, 없는 enum 값)는
 * @Valid 검증까지 가지도 못해 MethodArgumentNotValidException으로는 안 잡힌다.
 * 그래서 별도 핸들러가 필요하고, 그 핸들러가 사라지면 조용히 영어가 새므로 여기서 못 박는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ErrorResponseShapeTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String VALID_SIGNUP = """
            {"email":"shape@nest.local","password":"Qa!passw0rd1","nickname":"모양",
             "name":"홍길동","birthDate":"1990-01-01","gender":"MALE","phone":"01012345678"}
            """;

    @Test
    @DisplayName("날짜를 ISO가 아닌 8자리로 보내면 한국어 메시지를 준다 (영어 'Bad Request' 금지)")
    void unparseableDateReturnsKoreanMessage() throws Exception {
        String body = VALID_SIGNUP.replace("\"1990-01-01\"", "\"19900101\"");
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("입력값을 다시 확인해주세요."))
                // Spring 기본 응답의 지문. 하나라도 있으면 기본 응답이 새어 나온 것이다.
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.path").doesNotExist());
    }

    @Test
    @DisplayName("enum에 없는 값도 같은 모양으로 막는다")
    void unknownEnumReturnsKoreanMessage() throws Exception {
        String body = VALID_SIGNUP.replace("\"MALE\"", "\"XX\"");
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("입력값을 다시 확인해주세요."))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }

    @Test
    @DisplayName("JSON 자체가 깨져도 스프링 기본 응답이 새지 않는다")
    void brokenJsonReturnsKoreanMessage() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("입력값을 다시 확인해주세요."))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }

    @Test
    @DisplayName("필드 검증 실패는 기존대로 어느 칸이 왜 틀렸는지까지 준다")
    void fieldValidationStillReportsFields() throws Exception {
        String body = VALID_SIGNUP.replace("\"Qa!passw0rd1\"", "\"short\"");
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("입력값을 다시 확인해주세요."))
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    @DisplayName("catch-all이 구체적 예외를 가로채지 않는다 — 휴대전화 미인증은 그대로 400 + 고유 메시지")
    void catchAllDoesNotShadowSpecificHandlers() throws Exception {
        // GlobalExceptionHandler에 @ExceptionHandler(Exception.class)를 두면서 생긴 위험.
        // 어드바이스 순서(@Order)가 어긋나면 AuthExceptionHandler가 담당하는 예외까지
        // 여기서 500 "일시적인 문제가 발생했어요"로 뭉개진다.
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(VALID_SIGNUP))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("휴대전화 인증을 완료해주세요."));
    }
}
