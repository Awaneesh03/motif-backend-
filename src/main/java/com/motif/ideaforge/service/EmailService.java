package com.motif.ideaforge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around JavaMailSender.
 *
 * All credentials are injected from environment variables — never hardcoded.
 * MAIL_USERNAME and MAIL_PASSWORD must be set as Render environment variables.
 * MAIL_PASSWORD should be a Gmail App Password (not the account password).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.contact.to-email}")
    private String toEmail;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * Sends a contact-form email.
     * Reply-To is set to the submitter's address so hitting Reply goes back to them.
     *
     * @throws org.springframework.mail.MailException on SMTP failure (let caller handle)
     */
    public void sendContactEmail(String name, String submitterEmail, String message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(fromEmail);
        mail.setTo(toEmail);
        mail.setReplyTo(submitterEmail);
        mail.setSubject("New Contact Form Message from " + name);
        mail.setText(
            "Name:    " + name          + "\n" +
            "Email:   " + submitterEmail + "\n" +
            "\nMessage:\n" + message
        );
        mailSender.send(mail);
        log.info("[EmailService] Contact email sent — name='{}' from='{}'", name, submitterEmail);
    }
}
