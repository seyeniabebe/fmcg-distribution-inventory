package com.distribution.dms.repository;

import com.distribution.dms.model.AppMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppMessageRepository extends JpaRepository<AppMessage, Long> {

    @Query("SELECT m FROM AppMessage m WHERE " +
           "(m.senderUsername = :u1 AND m.recipientUsername = :u2) OR " +
           "(m.senderUsername = :u2 AND m.recipientUsername = :u1) " +
           "ORDER BY m.sentAt ASC")
    List<AppMessage> findThread(@Param("u1") String u1, @Param("u2") String u2);

    long countByRecipientUsernameAndIsReadFalse(String recipientUsername);

    long countByRecipientUsernameAndSenderUsernameAndIsReadFalse(String recipientUsername, String senderUsername);

    @Modifying
    @Query("UPDATE AppMessage m SET m.isRead = true WHERE m.recipientUsername = :recipient AND m.senderUsername = :sender AND m.isRead = false")
    int markThreadAsRead(@Param("recipient") String recipient, @Param("sender") String sender);

    @Query("SELECT m FROM AppMessage m WHERE m.senderUsername = :u OR m.recipientUsername = :u ORDER BY m.sentAt DESC")
    List<AppMessage> findAllForUser(@Param("u") String u);
}
