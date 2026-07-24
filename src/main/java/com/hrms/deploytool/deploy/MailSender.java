package com.hrms.deploytool.deploy;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * Handles sending release notification emails via SMTP after a successful deployment.
 */
public class MailSender {

    /**
     * Sends a release notification email.
     *
     * @param config Properties containing SMTP settings
     * @param subject Email subject
     * @param body Email body content
     * @throws Exception if sending fails
     */
    public static void sendEmail(Properties config, String subject, String body) throws Exception {
        String host = config.getProperty("smtpHost");
        String port = config.getProperty("smtpPort", "587");
        String username = config.getProperty("smtpUser");
        String password = config.getProperty("smtpPass");
        String from = config.getProperty("emailFrom");
        String to = config.getProperty("emailTo");

        if (host == null || host.isEmpty() || from == null || from.isEmpty() || to == null || to.isEmpty()) {
            throw new IllegalArgumentException("SMTP configuration is incomplete. "
                    + "Please configure SMTP host, sender (From), and recipient list (To) in configuration.");
        }

        Properties mailProps = new Properties();
        mailProps.put("mail.smtp.host", host);
        mailProps.put("mail.smtp.port", port);
        
        boolean hasAuth = username != null && !username.isEmpty();
        mailProps.put("mail.smtp.auth", String.valueOf(hasAuth));
        mailProps.put("mail.smtp.starttls.enable", "true");
        mailProps.put("mail.smtp.ssl.protocols", "TLSv1.2");

        Session session;
        if (hasAuth) {
            session = Session.getInstance(mailProps, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
        } else {
            session = Session.getInstance(mailProps);
        }

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        
        // Split recipients by comma or semicolon
        String[] recipients = to.split("[,;]");
        InternetAddress[] addresses = new InternetAddress[recipients.length];
        for (int i = 0; i < recipients.length; i++) {
            addresses[i] = new InternetAddress(recipients[i].trim());
        }
        message.setRecipients(Message.RecipientType.TO, addresses);
        
        message.setSubject(subject);
        message.setText(body);

        Transport.send(message);
    }
}
