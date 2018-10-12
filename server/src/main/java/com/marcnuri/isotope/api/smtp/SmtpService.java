/*
 * SmtpService.java
 *
 * Created on 2018-10-07, 18:25
 *
 * Copyright 2018 Marc Nuri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.marcnuri.isotope.api.smtp;

import com.marcnuri.isotope.api.credentials.Credentials;
import com.marcnuri.isotope.api.exception.IsotopeException;
import com.marcnuri.isotope.api.message.Message;
import com.marcnuri.isotope.api.message.MessageUtils;
import com.sun.mail.util.MailSSLSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

import javax.annotation.PreDestroy;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.util.Date;
import java.util.Properties;

/**
 * Created by Marc Nuri <marc@marcnuri.com> on 2018-10-07.
 */
@Service
@RequestScope
public class SmtpService {

    private static final Logger log = LoggerFactory.getLogger(SmtpService.class);

    private static final String SMTPS_PROTOCOL = "smtps";
    private static final String STYLES =
            "body {font-family: 'Roboto', 'Calibri',  sans-serif; font-size: 1rem; color: #333}" +
            "h1.h1 {margin: 6px 0 16px 0; font-size: 3rem; font-weight: normal}" +
            "h2.h2 {margin: 6px 0 12px 0; font-size: 2.5rem; font-weight: normal}" +
            "h3.h3 {margin: 6px 0 8px 0; font-size: 1.5rem; font-weight: bold}" +
            "blockquote.blockquote {border-left: 5px solid #ebebeb; font-style: italic; margin: 0; padding: 0 32px}" +
            "pre.code-block {background-color: #ebebeb; margin: 0; padding: 0 8px}" +
            "pre.code-block:first-child {padding-top: 8px}" +
            "pre.code-block:last-child {padding-bottom: 8px}";

    private final MailSSLSocketFactory mailSSLSocketFactory;

    private Session session;
    private Transport smtpTransport;

    @Autowired
    public SmtpService(MailSSLSocketFactory mailSSLSocketFactory) {
        this.mailSSLSocketFactory = mailSSLSocketFactory;
    }

    public void sendMessage(Credentials credentials, Message message) {
        try {
            final MimeMessage mimeMessage = new MimeMessage(getSession());
            mimeMessage.setSentDate(new Date());
            if (credentials.getUser() != null && credentials.getUser().contains("@")) {
                mimeMessage.setFrom(credentials.getUser());
            } else {
                mimeMessage.setFrom(String.format("%s@%s", credentials.getUser(), credentials.getServerHost()));
            }
            for (javax.mail.Message.RecipientType type : new javax.mail.Message.RecipientType[]{
                    MimeMessage.RecipientType.TO, MimeMessage.RecipientType.CC, MimeMessage.RecipientType.BCC
            }) {
                mimeMessage.setRecipients(type, MessageUtils.getRecipientAddresses(message, type));
            }
            mimeMessage.setSubject(message.getSubject());
            final MimeMultipart multipart = new MimeMultipart();
            final MimeBodyPart body = new MimeBodyPart();
            multipart.addBodyPart(body);
            body.setContent(String.format("<html><head><style>%1$s</style></head><body><div id='scoped'>" +
                            "<style type='text/css' scoped>%1$s</style>%2$s</div></body></html>",
                            STYLES, message.getContent()),
                    MediaType.TEXT_HTML_VALUE);
            mimeMessage.setContent(multipart);
            mimeMessage.saveChanges();
            getSmtpTransport(credentials).sendMessage(mimeMessage, mimeMessage.getAllRecipients());
        } catch(MessagingException ex) {
            throw new IsotopeException("Problem sending message", ex);
        }
    }

    @PreDestroy
    public void destroy() {
        log.debug("SmtpService destroyed");
        if(smtpTransport != null) {
            try {
                smtpTransport.close();
            } catch (MessagingException ex) {
                log.error("Error closing SMTP Transport", ex);
            }
        }
    }

    private Session getSession() {
        if (session == null) {
            session = Session.getInstance(initMailProperties(mailSSLSocketFactory), null);
        }
        return session;
    }

    private Transport getSmtpTransport(Credentials credentials) throws MessagingException {
        if (smtpTransport == null) {
            smtpTransport = getSession().getTransport(SMTPS_PROTOCOL);
            smtpTransport.connect(
                    credentials.getServerHost(),
                    465,
                    credentials.getUser(),
                    credentials.getPassword());
            log.debug("Opened new SMTP transport");
        }
        return smtpTransport;
    }

    private static Properties initMailProperties(MailSSLSocketFactory socketFactory) {
        final Properties ret = new Properties();
        ret.put("mail.smtp.ssl.enable", true);
        ret.put("mail.smtp.starttls.enable", true);
        ret.put("mail.smtps.socketFactory.class", socketFactory);
        ret.put("mail.smtps.socketFactory.fallback", false);
        ret.put("mail.smtps.auth", true);
        return ret;
    }
}