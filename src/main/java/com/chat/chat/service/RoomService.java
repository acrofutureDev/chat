package com.chat.chat.service;

import static com.chat.chat.common.error.ErrorTypes.*;
import static com.chat.chat.dto.response.RoomListResponse.*;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import com.chat.chat.common.exception.CustomException;
import com.chat.chat.common.util.MemberValidator;
import com.chat.chat.dto.request.RoomDeleteRequest;
import com.chat.chat.dto.request.RoomRequest;
import com.chat.chat.dto.response.BasicMemberResponse;
import com.chat.chat.dto.response.BasicRoomResponse;
import com.chat.chat.dto.response.JoinRoomResponse;
import com.chat.chat.dto.response.RoomListResponse;
import com.chat.chat.entity.Member;
import com.chat.chat.entity.Room;
import com.chat.chat.repository.CustomMemberRepository;
import com.chat.chat.repository.MemberRepository;
import com.chat.chat.repository.RoomRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

	private final RoomRepository roomRepository;
	private final MemberRepository memberRepository;
	private final CustomMemberRepository customMemberRepository;
	private final MemberValidator memberValidator;
	private final ReactiveMongoTemplate reactiveMongoTemplate;

	//reactive mongodb pagenation 정보
	// https://www.devkuma.com/docs/spring-data-mongo/pagination/
	// https://medium.com/@AADota/spring-mongo-template-pagination-92fa93c50d5c
	// https://gnidinger.tistory.com/entry/WebFluxReactive-MongoDB-%EC%BF%BC%EB%A6%AC-%EC%A0%95%EB%A0%AC-%ED%8E%98%EC%9D%B4%EC%A7%80%EB%84%A4%EC%9D%B4%EC%85%98
	//pageable 성능최적화
	// https://junior-datalist.tistory.com/342
	public Mono<Page<RoomListResponse>> getAllRooms(String page, String size) {

		Pageable pageable = PageRequest.of(Integer.parseInt(page), Integer.parseInt(size));
		Query pageableQuery = new Query().with(pageable);

		Mono<List<Room>> roomsMono = reactiveMongoTemplate.find(pageableQuery, Room.class).collectList();
		Mono<Long> totalElements = reactiveMongoTemplate.count(new Query(), Room.class);

		return Mono.zip(roomsMono, totalElements)
			.map(tuple -> {
				List<RoomListResponse> responses = tuple.getT1().stream()
					.map(room -> {
						List<BasicMemberResponse> groupMembers = room.getGroupMembers().stream()
							.map(BasicMemberResponse::basicMemberResponse).toList();
						return roomListResponse(room, groupMembers);
					}).toList();
				log.info("모든 방이 조회되었습니다.");
				return PageableExecutionUtils.getPage(responses, pageable, tuple::getT2);
			});
	}

	public Mono<JoinRoomResponse> joinRoom(String roomId, String userId) {
		return isExistRoom(roomId)
			.flatMap(e -> roomRepository.joinRoomMember(roomId, userId))
			.doOnNext(e -> log.info(userId + " 가 " + roomId + " 에 입장하셨습니다."))
			.flatMap(updatedRoom -> roomRepository.findById(roomId))
			.map(room -> new JoinRoomResponse(room.getRoomName(), userId));
	}

	public Mono<BasicRoomResponse> deleteRoom(String roomId, RoomDeleteRequest deleteRequest) {
		return
			isExistRoom(roomId)
				.doOnNext(room -> checkPassword(room.getRoomPassword(), deleteRequest.password()))
				.flatMap(room ->
					roomRepository.delete(room)
						.thenReturn(BasicRoomResponse.basicRoomResponse(room)))
				.doOnNext(e -> log.info(e.roomName() + " 가 삭제되었습니다."));

	}

	public Mono<RoomListResponse> createRooms(Mono<RoomRequest> roomRequest) {
		return roomRequest.flatMap(roomData ->
				isExistMember(roomData.adminMemberId())
					.flatMap(memberInfo ->
						roomRepository.save(new Room(roomData, memberInfo))
							.flatMap(savedRoom -> {
								List<BasicMemberResponse> groupMembers = savedRoom.getGroupMembers().stream()
									.map(BasicMemberResponse::basicMemberResponse)
									.toList();
								return Mono.just(roomListResponse(savedRoom, groupMembers));
							})
					)
			)
			.doOnNext(room -> log.info("방" + room.roomName() + " 이 생성되었습니다."));
	}

	public Mono<BasicRoomResponse> leaveRoom(String roomId, String userId) {
		return
			isExistRoom(roomId)
				.flatMap(e -> roomRepository.removeRoomMember(roomId, userId))
				.doOnNext(room -> log.info(userId + "님이" + roomId + "방에서 나가셨습니다."))
				.flatMap(updatedRoom -> roomRepository.findById(roomId))
				.map(BasicRoomResponse::basicRoomResponse);
	}

	public Mono<Room> isExistRoom(String roomId) {
		return roomRepository.findById(roomId)
			.switchIfEmpty(Mono.error(new CustomException(NOT_EXIST_ROOM.errorMessage)));
	}

	public Mono<Member> isExistMember(String userId) {
		return memberRepository.findById(userId)
			.switchIfEmpty(Mono.error(new CustomException(NOT_EXIST_MEMBER.errorMessage)));
	}

	private void checkPassword(String originPass, String requestPass) {
		if (!originPass.equals(requestPass)) {
			Mono.error(new CustomException(NOT_VALID_PASSWORD.errorMessage));
		}
	}

	public Mono<List<RoomListResponse>> getUserAllRooms(String memberId) {
		return customMemberRepository.findRoomsByMemberId(memberId)
			.doOnNext(room -> log.info("조회된 방: {}", room))
			.map(room -> {
				List<BasicMemberResponse> groupMembers = room.getGroupMembers().stream()
					.map(BasicMemberResponse::basicMemberResponse)
					.toList();
				return RoomListResponse.roomListResponse(room, groupMembers);
			})
			.doOnNext(response -> log.info("특정 유저의 방 조회: {}", response))
			.collectList();
	}

	public Mono<List<RoomListResponse>> searchRoomByTitle(String memberId, String title, int page, int size) {
		// member 필요없음 빼기
		return customMemberRepository.findRoomsByTitleWithPagination(title, page, size)
			.map(room -> {
				List<BasicMemberResponse> groupMembers = room.getGroupMembers().stream()
					.map(BasicMemberResponse::basicMemberResponse)
					.toList();
				return RoomListResponse.roomListResponse(room, groupMembers);
			})
			.collectList();
	}
}
