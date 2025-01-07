package com.chat.chat.handler;

import com.chat.chat.common.exception.CustomException;
import com.chat.chat.common.responseEnums.ErrorTypes;
import com.chat.chat.common.responseEnums.SuccessTypes;
import com.chat.chat.common.util.MemberValidator;
import com.chat.chat.common.util.ResponseUtils;
import com.chat.chat.dto.request.MemberRequest;
import com.chat.chat.dto.response.MemberResponse;
import com.chat.chat.service.MemberService;
import com.chat.chat.service.UserInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * (공통)- 토큰 파싱
 * request.attribute(memberId) 꺼내기 ->
 * attribute 항상 Object 타입을 반환 -> cast 로 내가 쓸 타입으로 변환
 * 만약에 null 값이 들어올 수 있으니 justOrEmpty 로 래핑 -> null 이 들어오면 터트려야 함
 * <p>
 * retrievedUserInfo -
 * memberId 만들어오면 바로 service 로 전달
 * <p>
 * editUserInfo-
 * body에 유효한 데이터만 들어오면 service 전달
 */
@Slf4j
@Component
@Service
@RequiredArgsConstructor
public class UserInfoHandler {

    private final UserInfoService userInfoService;

    public Mono<ServerResponse> retrievedUserInfoHandler(ServerRequest request) {
        return Mono.justOrEmpty(request.attribute("memberId"))
                .cast(String.class)
                .switchIfEmpty(Mono.error(new CustomException(ErrorTypes.NOT_EXIST_MEMBER.errorMessage)))
                .doOnNext(memberId -> log.info("memberId:{}", memberId))
                .flatMap(memberId -> userInfoService.getUserInfo(memberId))
                .flatMap(memberResponse -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ResponseUtils.success(SuccessTypes.USER_INFO_RETRIEVED_SUCCESSFULLY.successMessage, memberResponse)))
                .onErrorResume(CustomException.class, error -> {
                    log.error("CustomError Exception :{}", error.getMessage());
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(ResponseUtils.fail(error.getMessage()));
                })
                .onErrorResume(error -> {
                    log.error("Unexpected Exception :{}", error.getMessage());
                    return ServerResponse.status(500)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(ResponseUtils.fail("Internal Server Error"));
                });


    }

    /**
     * 원래 비밀 번호를 치고 , newPassword 치는 방식으로
     * 비밀번호 변경
     *
     * @param request
     * @return //
     */
    public Mono<ServerResponse> editUserInfoHandler(ServerRequest request) {

        Mono<String> memberIdMono = Mono.justOrEmpty(request.attribute("memberId")).cast(String.class).doOnNext(memberId -> log.info("memberId:{}", memberId))
                .switchIfEmpty(Mono.error(new CustomException(ErrorTypes.NOT_EXIST_MEMBER.errorMessage)));

        Mono<MemberRequest> passwordUpdateMono = request.bodyToMono(MemberRequest.class)
                .flatMap(req -> MemberValidator.validateForEdit(req).thenReturn(req));
        return Mono.zip(memberIdMono, passwordUpdateMono)
                .flatMap(tuple -> {
                    String memberId = tuple.getT1();
                    MemberRequest memberRequest = tuple.getT2();
                    return userInfoService.updateUserInfo(
                            memberId,
                            memberRequest.getMemberPassword(),
                            memberRequest.getMemberNewPassword()
                    );
                })
                .flatMap(member -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ResponseUtils.success(SuccessTypes.USER_PW_UPDATE_SUCCESSFULLY.successMessage,
                                Map.of("memberId", member.getMemberId())
                        )))

                .onErrorResume(CustomException.class, error -> {
                    log.error("CustomError Exception :{}", error.getMessage());
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(ResponseUtils.fail(error.getMessage()));
                })
                .onErrorResume(error -> {
                    log.error("Unexpected Exception :{}", error.getMessage());
                    return ServerResponse.status(500)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(ResponseUtils.fail("Internal Server Error"));
                });


    }



    public Mono<ServerResponse> deleteUserInfoHandler(ServerRequest request) {
        return Mono.justOrEmpty(request.attribute("memberId"))
                .cast(String.class)
                .doOnNext(memberId -> log.info("memberId:{}", memberId))
                .flatMap(memberId -> userInfoService.deleteUserInfo(memberId))
                // flatmap-> then 으로 변경함
                // - flatMap 입력데이터를 반환해서 새로운 Mono , Flux 를 반환 그리고 이 새로운 Pulisher 를 다음 단계로 전달함
                // - then 입력 데이데를 무시하도 다음 작업으로 이동함 이전 결과를 사용할 필요 없을 때 적합
                // .flatMap은 이전 작업의 결과 값을 필요,  (Mono<void 반환했음>), Mono<Void>는 값이 없어서 result 에서 흐름이 중단 결과값을 전달 자체를 하지 않음
                // .then은 결과 값을 무시하고 작업 완료 후 다음 작업으로 바로 이동하므로 적합
                .then(Mono.defer(() -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ResponseUtils.success(SuccessTypes.DELETE_SUCCESS.successMessage, null))))
                .onErrorResume(CustomException.class, error -> {
                    log.error("CustomError Exception: {}", error.getMessage());
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(ResponseUtils.fail(error.getMessage()));
                })
                .onErrorResume(error -> {
                    log.error("Unexpected Exception: {}", error.getMessage());
                    return ServerResponse.status(500)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(ResponseUtils.fail("Internal Server Error"));
                });
    }
}
