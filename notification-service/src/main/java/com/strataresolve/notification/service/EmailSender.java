package com.strataresolve.notification.service;

import com.strataresolve.notification.domain.Notification;

/**
 * Interface abstracting email delivery for notifications.
 * Implementations handle the actual transport mechanism (SMTP, API-based, etc.).
 */
public interface EmailSender {

    /**
     * Sends an email for the given notification to the recipient.
     *
     * @param notification the notification containing subject, body, and recipient info
     * @param recipientEmail the email address of the recipient
     * @throws EmailSendException if the email could not be delivered
     */
    void send(Notification notification, String recipientEmail);
}
