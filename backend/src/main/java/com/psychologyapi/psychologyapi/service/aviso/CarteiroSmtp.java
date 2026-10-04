package com.psychologyapi.psychologyapi.service.aviso;

import java.io.UnsupportedEncodingException;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

public class CarteiroSmtp implements Carteiro {

    private final JavaMailSender sender;

    public CarteiroSmtp(JavaMailSender sender) {
        this.sender = sender;
    }

    @Override
    public void enviar(Mensagem mensagem) {
        try {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
            helper.setFrom(mensagem.de(), mensagem.nomeDoRemetente());
            helper.setTo(mensagem.para());
            if (mensagem.responderPara() != null) {
                helper.setReplyTo(mensagem.responderPara());
            }
            helper.setSubject(mensagem.assunto());
            helper.setText(mensagem.corpo(), false);
            sender.send(mime);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException(e.getClass().getSimpleName());
        }
    }
}
