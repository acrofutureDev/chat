package com.chat.chat.service;

import com.chat.chat.common.error.ErrorTypes;
import com.chat.chat.common.exception.CustomException;
import com.chat.chat.common.util.JwtUtil;
import com.chat.chat.dto.request.MemberRequest;
import com.chat.chat.dto.response.MemberResponse;
import com.chat.chat.dto.response.TokenResponse;
import com.chat.chat.entity.Member;
import com.chat.chat.repository.MemberRepository;
import com.chat.chat.repository.RedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepo;
    private final JwtUtil jwtUtil;
    private final RedisRepository redisRepo;


    public Mono<Member> register(MemberRequest memberRequest) {
        return checkDuplicateId(memberRequest.getMemberId())
                .then(Mono.just(createMember(memberRequest)))
                .flatMap(member ->
                        memberRepo.save(member)
                                .doOnSuccess(savedMember -> log.info("회원가입한 유저 데이터베이스 반영 완료 유저 id: {}", savedMember.getMemberId()))
                                .then(redisRepo.saveMember(
                                                member.getMemberId(),
                                                member.getMemberPassword(),
                                                member.getCreatedDate()
                                        )
                                        .doOnSuccess(redisResult -> log.info("회원가입한 유저 레디스에 반영 완료 여부:{}", redisResult.toString()))
                                        .thenReturn(member))
                );
    }

    private Member createMember(MemberRequest memberRequest) {
        Member member = new Member();
        member.setMemberId(memberRequest.getMemberId());
        member.setMemberPassword(hashPassword(memberRequest.getMemberPassword()));
        member.setCreatedDate(LocalDateTime.now());
        return member;
    }




    private Mono<Void> checkDuplicateId(String memberId) {
        return redisRepo.exists(memberId)
                .flatMap(existUserInRedis -> {
                    if (existUserInRedis) {
                        log.error("해당 아이디 중복, 레디스에서 찾음: {}", memberId);
                        return Mono.error(new CustomException(ErrorTypes.DUPLICATE_MEMBER_ID.errorMessage));
                    } else {
                        return memberRepo.existsByMemberId(memberId)
                                .flatMap(existUserInDB -> {
                                    if (existUserInDB) {
                                        log.error("해당 아이디 중복 데이터베이스에서 찾으,ㅁ: {}", memberId);
                                        return Mono.error(new CustomException(ErrorTypes.DUPLICATE_MEMBER_ID.errorMessage));
                                    } else {
                                        log.info("데이터 베이스 , 레디스 모두에서 해당 아이디가 중복되지 않음: {}", memberId);
                                        return Mono.empty();
                                    }
                                });
                    }

                });
    }

    public static String hashPassword(String memberPassword) {
        return BCrypt.hashpw(memberPassword, BCrypt.gensalt());
    }


    /**
     * 로그인 요청 처리 메서드
     * <p>
     * 1. ID 가 DB에 있는지 확인 / 예외 :"User Not Exist" 반환
     * 2. PW 비교 / 예외 : "ID or Pw Do Not Match" 반환
     * 로그인 성공 시 JWT 토큰 생성
     *
     * @param memberRequestMono {@link Mono} 로그인 데이터
     * @return {@link Mono} 형태로 로그인 처리 결과를 {@link TokenResponse} 반환
     * @throws CustomException 회원이 데이터베이스에 없거나 비밀번호가 일치하지 않을 경우 발생
     */
    public Mono<TokenResponse> login(Mono<MemberRequest> memberRequestMono) {
        return memberRequestMono
                .flatMap(memberReq -> existingUserOrNot(memberReq))
                .doOnNext(existingMember ->
                {
                    log.info("login memberId: {}", existingMember.getMemberId());
                })
                .flatMap(existingUser ->
                        jwtUtil.generateToken(existingUser.getMemberId())
                                .flatMap(token -> {
                                    log.info("login token: {}", token);
                                    log.info("login token: {}", token);
                                    return Mono.just(new TokenResponse(existingUser.getMemberId(), token));
                                }));
    }


    private Mono<Member> existingUserOrNot(MemberRequest req) {
        return memberRepo.findByMemberId(req.getMemberId())
                .switchIfEmpty(
                        Mono.error(new CustomException(ErrorTypes.NOT_EXIST_MEMBER.errorMessage)))
                .flatMap(member -> {
                    if (BCrypt.checkpw(req.getMemberPassword(), member.getMemberPassword())) {
                        return Mono.just(member);
                    } else {
                        log.error("ID or Pw Do Not Match");
                        return Mono.error(new CustomException(ErrorTypes.ID_OR_PW_DO_NOT_MATCH.errorMessage));
                    }
                });
    }
}







