package com.chat.chat.entity;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Document(collection = "room")
@NoArgsConstructor
@Getter
public class Room {
	@Id
	private String id;
	private String roomName;
	private String roomPassword;
	private Member roomAdmin;
	private List<Member> groupMembers;
	@CreatedDate
	private LocalDate createdDate;

}
