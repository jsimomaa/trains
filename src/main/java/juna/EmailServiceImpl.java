package juna;

import java.util.concurrent.ForkJoinPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailServiceImpl implements EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Autowired
    private Juna juna;

    @Autowired
    public JavaMailSender emailSender;

    @Override
    public void sendSimpleMessage(String to, String subject, String text) {
        ForkJoinPool.commonPool().execute(() -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(to);
                message.setFrom(juna.getFromEmail());
                message.setSubject(subject);
                message.setText(text + "\n\nMessage sent from " + juna.getServername() +"/trains");
                emailSender.send(message);
            } catch (Throwable t) {
                LOGGER.error("Could not send email with subject {} to {}", subject, to, t);
            }
        });
    }
}