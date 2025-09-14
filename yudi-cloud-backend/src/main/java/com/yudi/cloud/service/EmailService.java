package com.yudi.cloud.service;

public interface EmailService {

    /**
     * 发送邮件
     *
     * @param to      收件人
     * @param subject 标题
     * @param content 内容
     */
    void sendEmail(String to, String subject, String content);

}
