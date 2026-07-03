package com.strataresolve.notification.service;

import com.strataresolve.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * EmailSender implementation using Spring's JavaMailSender for SMTP delivery.
 */
@Component
public class SpringMailEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SpringMailEmailSender.class);

    private final JavaMailSender javaMailSender;
    private final String fromAddress;

    public SpringMailEmailSender(JavaMailSender javaMailSender,
                                 @Value("${app.notification.from-address:noreply@strataresolve.com}") String fromAddress) {
        this.javaMailSender = javaMailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(Notification notification, String recipientEmail) {
        log.debug("Sending email to {} for notification {}", recipientEmail, notification.getId());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipientEmail);
        message.setSubject(notification.getSubject());
        message.setText(notification.getBody());

        try {
            javaMailSender.send(message);
            log.info("Email sent successfully to {} for notification {}", recipientEmail, notification.getId());
        } catch (MailException e) {
            log.error("Failed to send email to {} for notification {}: {}", recipientEmail, notification.getId(), e.getMessage());
            throw new EmailSendException("Failed to send email to " + recipientEmail, e);
        }
    }
}
