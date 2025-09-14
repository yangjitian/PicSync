package com.yudi.cloud.model.dto.user;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserRegisterDTO implements Serializable {

    private static final long serialVersionUID = 4714004344106603785L;

    private String userAccount;

    private String userPassword;

    private String checkPassword;

    private String verificationCode;
}